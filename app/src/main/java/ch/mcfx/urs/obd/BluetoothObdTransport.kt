package ch.mcfx.urs.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

val OBD_SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

private const val COMMAND_TIMEOUT_MILLIS = 5_000L
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

    @SuppressLint("MissingPermission") // caller (ObdManager) guarantees BLUETOOTH_CONNECT before calling
    suspend fun open() = withContext(Dispatchers.IO) {
        close()
        val newSocket = device.createRfcommSocketToServiceRecord(OBD_SPP_UUID)
        try {
            // Discovery competes for the radio and slows/blocks a pending
            // connect on many Bluetooth stacks - standard Android advice is
            // to always cancel it right before connecting.
            adapter.cancelDiscovery()
            newSocket.connect()
        } catch (e: IOException) {
            runCatching { newSocket.close() }
            throw ObdTransportException("Failed to connect to ${device.address}", e)
        }
        socket = newSocket
        input = newSocket.inputStream
        output = newSocket.outputStream
    }

    /** Sends [command] (a bare AT or PID command, no CR) and returns the raw text up to the next prompt. */
    suspend fun sendCommand(command: String): String {
        val out = output ?: throw ObdTransportException("Not connected")
        val inp = input ?: throw ObdTransportException("Not connected")
        return try {
            withTimeout(COMMAND_TIMEOUT_MILLIS) {
                withContext(Dispatchers.IO) {
                    out.write("$command\r".toByteArray(Charsets.US_ASCII))
                    out.flush()
                    readUntilPrompt(inp)
                }
            }
        } catch (e: TimeoutCancellationException) {
            // withTimeout only cancels the coroutine - InputStream.read() is a
            // blocking call that ignores cancellation and would otherwise leak
            // the underlying IO thread stuck in read(). Closing the socket
            // forces that pending read to throw, freeing the thread.
            close()
            throw ObdTransportException("Timed out waiting for response to $command", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw ObdTransportException("I/O error during $command", e)
        }
    }

    private fun readUntilPrompt(input: InputStream): String {
        val buffer = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte == -1) throw ObdTransportException("Connection closed by remote device")
            if (byte == PROMPT_CHAR) break
            buffer.append(byte.toChar())
        }
        return buffer.toString()
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
