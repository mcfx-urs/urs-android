package ch.mcfx.urs.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "ObdManager"
private const val DEFAULT_POLL_INTERVAL_MILLIS = 1_000L
private const val MAX_RECONNECT_ATTEMPTS = 5
private const val RECONNECT_BACKOFF_MILLIS = 2_000L

// Convenience-only fallback for auto-picking a bonded device by name when
// nothing has been explicitly selected yet (see findPairedDevice) - many
// ELM327 clones advertise a generic serial number instead of a vendor name
// (confirmed 2026-07-21: a real Mucar BT200 paired as "989140751235", not
// anything containing "BT200"), so this alone is not reliable; selectDevice/
// pairedDevices exist for the user to pick the right one explicitly.
const val OBD_DEVICE_NAME_HINT = "BT200"

private const val PREFS_NAME = "obd_prefs"
private const val KEY_SELECTED_DEVICE_ADDRESS = "selected_device_address"

// ATH0/ATS1 force the exact "41 0C 1A F8"-style response shape ObdResponseParser
// expects, regardless of protocol (ATSP0 auto-detects among J1850/ISO9141/KWP2000/
// ISO 15765-4 CAN - all of them honor these two display settings the same way).
// Explicit, not left at the chip's power-on default: ELM327 EEPROM persists
// headers/spacing across resets, so a prior app (e.g. Mucar's own) could have
// saved headers-on ("7E8 06 41 0C 1A F8") or spaces-off ("410C1AF8"), either of
// which would silently break this parser's whitespace-token-based parsing.
private val INIT_COMMANDS = listOf("ATZ", "ATE0", "ATL0", "ATH0", "ATS1", "ATSP0")

/**
 * Owns the Bluetooth connection to a paired ELM327-compatible OBD-II
 * adapter (Mucar BT200), runs the AT init sequence, and polls the PIDs in
 * [ObdPid.ALL] at [pollIntervalMillis], exposing state and the latest
 * reading as [StateFlow]s for reactive UI consumption.
 *
 * One instance per app process (owned by `AppContainer`), not designed for
 * concurrent [connect] calls from multiple callers - a second [connect]
 * cancels whatever connection attempt/poll loop is currently running.
 */
class ObdManager(
    private val context: Context,
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS,
) {

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _connectionState = MutableStateFlow(ObdConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ObdConnectionState> = _connectionState.asStateFlow()

    private val _reading = MutableStateFlow(ObdReading())
    val reading: StateFlow<ObdReading> = _reading.asStateFlow()

    private var transport: BluetoothObdTransport? = null
    private var connectionJob: Job? = null

    // BLUETOOTH_SCAN is required too, not just BLUETOOTH_CONNECT - specifically for
    // BluetoothAdapter.cancelDiscovery() in BluetoothObdTransport.open(), which throws
    // a SecurityException without it (confirmed 2026-07-21: crashed the app on a real
    // device, since that exception isn't an IOException and wasn't being caught there).
    fun hasRequiredPermissions(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true // BLUETOOTH/BLUETOOTH_ADMIN below API 31 are install-time (normal) permissions
        }

    /** All bonded devices, or empty if Bluetooth is off, permission is missing, or nothing is paired. */
    @SuppressLint("MissingPermission") // guarded by hasRequiredPermissions() below
    fun pairedDevices(): List<BluetoothDevice> {
        if (!hasRequiredPermissions()) return emptyList()
        val adapter = bluetoothAdapter() ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return adapter.bondedDevices?.toList() ?: emptyList()
    }

    /** Persists [device] (by MAC address) as the OBD adapter to use - see [findPairedDevice]. */
    fun selectDevice(device: BluetoothDevice) {
        prefs.edit().putString(KEY_SELECTED_DEVICE_ADDRESS, device.address).apply()
    }

    /**
     * The explicitly [selectDevice]-d device if it's still bonded, otherwise
     * a bonded device matching [OBD_DEVICE_NAME_HINT] as a best-effort
     * default. `null` if neither is available - pairing itself (fixed PIN,
     * standard for ELM327 dongles) happens via the system Bluetooth
     * settings screen, this module never initiates pairing itself.
     */
    fun findPairedDevice(): BluetoothDevice? {
        val devices = pairedDevices()
        val selectedAddress = prefs.getString(KEY_SELECTED_DEVICE_ADDRESS, null)
        return devices.firstOrNull { it.address == selectedAddress }
            ?: devices.firstOrNull { it.name?.contains(OBD_DEVICE_NAME_HINT, ignoreCase = true) == true }
    }

    private fun bluetoothAdapter(): BluetoothAdapter? =
        ContextCompat.getSystemService(context, BluetoothManager::class.java)?.adapter

    /** Connects to [device] and starts polling; reconnects on a dropped connection until [MAX_RECONNECT_ATTEMPTS] is exceeded. */
    fun connect(device: BluetoothDevice) {
        connectionJob?.cancel()
        connectionJob = managerScope.launch { runConnectionLoop(device) }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        transport?.close()
        transport = null
        _connectionState.value = ObdConnectionState.DISCONNECTED
    }

    private suspend fun runConnectionLoop(device: BluetoothDevice) {
        var attempt = 0
        while (coroutineContext.isActive) {
            val adapter = bluetoothAdapter()
            if (!hasRequiredPermissions() || adapter == null) {
                Log.w(TAG, "Missing BLUETOOTH_CONNECT permission or no Bluetooth adapter, cannot connect")
                _connectionState.value = ObdConnectionState.ERROR
                return
            }

            _connectionState.value =
                if (attempt == 0) ObdConnectionState.CONNECTING else ObdConnectionState.RECONNECTING
            val newTransport = BluetoothObdTransport(device, adapter)
            try {
                newTransport.open()
                transport = newTransport
                _connectionState.value = ObdConnectionState.INITIALIZING
                runInitSequence(newTransport)
                _connectionState.value = ObdConnectionState.CONNECTED
                attempt = 0
                pollLoop(newTransport) // suspends here until the connection drops or is cancelled
            } catch (e: ObdTransportException) {
                Log.w(TAG, "OBD connection attempt failed: ${e.message}")
            } finally {
                newTransport.close()
                if (transport === newTransport) transport = null
            }

            attempt++
            if (attempt > MAX_RECONNECT_ATTEMPTS) {
                Log.w(TAG, "Giving up after $MAX_RECONNECT_ATTEMPTS reconnect attempts")
                _connectionState.value = ObdConnectionState.ERROR
                return
            }
            delay(RECONNECT_BACKOFF_MILLIS * attempt)
        }
    }

    private suspend fun runInitSequence(transport: BluetoothObdTransport) {
        for (command in INIT_COMMANDS) {
            val response = transport.sendCommand(command)
            Log.d(TAG, "$command -> ${response.trim()}")
        }
    }

    private suspend fun pollLoop(transport: BluetoothObdTransport) {
        while (coroutineContext.isActive) {
            val reading = ObdReading(
                rpm = queryPid(transport, ObdPid.EngineRpm),
                speedKmh = queryPid(transport, ObdPid.VehicleSpeed),
                coolantTempC = queryPid(transport, ObdPid.CoolantTemp),
                fuelLevelPercent = queryPid(transport, ObdPid.FuelLevel),
            )
            _reading.value = reading
            Log.d(
                TAG,
                "RPM=${reading.rpm} speed=${reading.speedKmh}km/h " +
                    "coolant=${reading.coolantTempC}C fuel=${reading.fuelLevelPercent}%",
            )
            delay(pollIntervalMillis)
        }
    }

    /** @throws ObdTransportException on I/O failure/timeout (propagates to trigger reconnect); returns `null` for NO DATA/unparseable. */
    private suspend fun <T> queryPid(transport: BluetoothObdTransport, pid: ObdPid<T>): T? {
        val raw = transport.sendCommand(pid.command)
        return when (val response = ObdResponseParser.parse(raw, pid)) {
            is ObdResponse.Data -> pid.parse(response.bytes)
            is ObdResponse.NoData -> null
            is ObdResponse.Error -> {
                Log.d(TAG, "${pid.command}: ${response.message}")
                null
            }
        }
    }
}
