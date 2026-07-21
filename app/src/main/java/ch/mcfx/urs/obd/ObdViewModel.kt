package ch.mcfx.urs.obd

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import kotlinx.coroutines.flow.StateFlow

/** Thin wrapper around [ObdManager] for the OBD live-data/setup screens. */
class ObdViewModel(private val obdManager: ObdManager) : ViewModel() {

    val connectionState: StateFlow<ObdConnectionState> get() = obdManager.connectionState
    val reading: StateFlow<ObdReading> get() = obdManager.reading

    fun hasPermission(): Boolean = obdManager.hasRequiredPermissions()

    fun pairedDevice(): BluetoothDevice? = obdManager.findPairedDevice()

    fun pairedDevices(): List<BluetoothDevice> = obdManager.pairedDevices()

    fun selectDevice(device: BluetoothDevice) = obdManager.selectDevice(device)

    /** No-op if [pairedDevice] resolves to nothing - callers should check it first to show the right state. */
    fun connectToPairedDevice() {
        pairedDevice()?.let { obdManager.connect(it) }
    }

    fun disconnect() {
        obdManager.disconnect()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                ObdViewModel(app.container.obdManager)
            }
        }
    }
}
