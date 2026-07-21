package ch.mcfx.urs.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val OBD_SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

// 5s was too tight for this hardware: confirmed 2026-07-21 via Android's own
// Bluetooth stack logs that our RFCOMM connection reaches the identical
// on_cli_rfc_connect/scn:1 state as a Mucar-app connection to the same
// adapter (which itself reportedly takes "a few seconds" to go live) -
// ours was simply being torn down by the watchdog at almost exactly the
// 5s mark, likely just before a slow/cold ELM327 clone would have answered
// ATZ (a full chip reset, slower than steady-state PID queries).
private const val COMMAND_TIMEOUT_MILLIS = 15_000L
private const val SETTLE_DELAY_MILLIS = 500L
private const val PROMPT_CHAR = '>'.code

class ObdTransportException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Raw Bluetooth Classic (SPP) transport to an ELM327-compatible adapter
 * (Mucar BT200). Owns exactly one [BluetoothSocket] at a time and only
 * knows how to open/close it and shuttle command/response lines across it
 * - connection lifecycle (reconnect-on-drop, permission checks, polling)
 * lives one layer up in [ObdManager].
 *
 * Not thread-safe: callers must not invoke [sendCommand] concurrently from
 * more than one coroutine at a time (ObdManager's single poll loop is the
 * only caller).
 */
class BluetoothObdTransport(private val device: BluetoothDevice, private val adapter: BluetoothAdapter) {

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    val isOpen: Boolean get() = socket != null

    @SuppressLint("MissingPermission") // caller (ObdManager) guarantees BLUETOOTH_CONNECT/BLUETOOTH_SCAN before calling
    suspend fun open() = withContext(Dispatchers.IO) {
        close()
        val newSocket = createSocket()
        try {
            // Discovery competes for the radio and slows/blocks a pending
            // connect on many Bluetooth stacks - standard Android advice is
            // to always cancel it right before connecting. Requires
            // BLUETOOTH_SCAN specifically (a separate permission from
            // BLUETOOTH_CONNECT, which covers the actual socket I/O below) -
            // caught rather than left to crash the app, since skipping this
            // call only risks a slower/less reliable connect, not a broken one.
            try {
                adapter.cancelDiscovery()
            } catch (e: SecurityException) {
                Log.w("BluetoothObdTransport", "cancelDiscovery failed (missing BLUETOOTH_SCAN?): ${e.message}")
            }
            newSocket.connect()
        } catch (e: IOException) {
            runCatching { newSocket.close() }
            throw ObdTransportException("Failed to connect to ${device.address}", e)
        }
        socket = newSocket
        input = newSocket.inputStream
        output = newSocket.outputStream

        // Many ELM327 clones need their UART to settle briefly right after
        // the RFCOMM link comes up - sending ATZ immediately can get no
        // response at all (confirmed 2026-07-21: reproduced against real
        // BT200 hardware, socket connects fine but the first command times
        // out with zero bytes back). Cheap, well-precedented defensive
        // delay; does not affect steady-state polling, only this one-time
        // post-connect window.
        delay(SETTLE_DELAY_MILLIS)
    }

    // Confirmed 2026-07-21 against this real Mucar BT200 (LED goes blue -
    // RFCOMM link genuinely established, verified via the Android Bluetooth
    // stack's own logs reaching RFC_MX_STATE_CONNECTED - then back to green,
    // truly zero bytes ever received, ruling out a framing/terminator
    // mismatch): the channel wasn't the issue - explicit channel 1 via
    // reflection made no difference over the standard SDP-based method.
    // What's left is secure vs. insecure RFCOMM: createRfcommSocket(*) opens
    // an authenticated/encrypted ("secure") link by default, which many
    // cheap HC-05-style Bluetooth-serial chips used in ELM327 clones don't
    // implement correctly - the link comes up but the chip never actually
    // bridges data across it. The SDK has a public, documented insecure
    // variant for exactly this ("use if authentication/encryption of the
    // link is not required") - createInsecureRfcommSocket(channel) direct,
    // falling back to the standard insecure SDP-based method.
    @SuppressLint("MissingPermission")
    private fun createSocket(): BluetoothSocket = try {
        val method = device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
        method.invoke(device, 1) as BluetoothSocket
    } catch (e: Exception) {
        Log.w("BluetoothObdTransport", "Direct insecure RFCOMM channel 1 unavailable, falling back to insecure SDP lookup: ${e.message}")
        device.createInsecureRfcommSocketToServiceRecord(OBD_SPP_UUID)
    }

    /** Sends [command] (a bare AT or PID command, no CR) and returns the raw text up to the next prompt. */
    suspend fun sendCommand(command: String): String {
        val out = output ?: throw ObdTransportException("Not connected")
        val inp = input ?: throw ObdTransportException("Not connected")
        return coroutineScope {
            // withTimeout alone cannot bound readUntilPrompt below: it only
            // cancels at coroutine suspension points, but InputStream.read()
            // is a genuinely blocking call with none inside it, so a silent
            // adapter (no response ever arrives) would hang forever instead
            // of timing out (confirmed 2026-07-21: this exact freeze happened
            // against real BT200 hardware). A watchdog running independently,
            // force-closing the socket after the timeout, is the standard
            // Android idiom to unblock a pending Bluetooth socket read - it
            // reliably makes that read() throw IOException.
            val watchdog = launch {
                delay(COMMAND_TIMEOUT_MILLIS)
                close()
            }
            try {
                withContext(Dispatchers.IO) {
                    // CRLF, not bare CR - some ELM327 clones are stricter
                    // about line termination than the "CR alone suffices"
                    // spec (confirmed 2026-07-21: this exact BT200 gave zero
                    // response to CR-terminated commands over a verified-good
                    // RFCOMM link - Mucar's own app talks to it fine, so this
                    // is a real protocol detail we're missing, not a
                    // hardware/pairing issue - trying CRLF as the most common
                    // such quirk).
                    out.write("$command\r\n".toByteArray(Charsets.US_ASCII))
                    out.flush()
                    readUntilPrompt(inp)
                }
            } catch (e: IOException) {
                // e.message already carries readUntilPrompt's partial-bytes
                // detail (if that's where this came from) - folded into the
                // outer message too, since ObdManager only logs e.message,
                // not the full cause chain.
                throw ObdTransportException("I/O error or timeout during $command: ${e.message}", e)
            } finally {
                watchdog.cancel()
            }
        }
    }

    private fun readUntilPrompt(input: InputStream): String {
        val buffer = StringBuilder()
        try {
            while (true) {
                val byte = input.read()
                if (byte == -1) throw ObdTransportException("Connection closed by remote device")
                if (byte == PROMPT_CHAR) break
                buffer.append(byte.toChar())
            }
            return buffer.toString()
        } catch (e: IOException) {
            // Surfaces exactly what (if anything) was received before the
            // failure/timeout - otherwise a watchdog-forced close silently
            // discards this, making "zero response" indistinguishable from
            // "got a partial/malformed response" in the logs.
            throw ObdTransportException("Received ${buffer.length} byte(s) before failure: ${buffer.toString().take(200)}", e)
        }
    }

    fun close() {
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket?.close() }
        input = null
        output = null
        socket = null
    }
}
