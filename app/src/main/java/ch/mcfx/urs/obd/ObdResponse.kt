package ch.mcfx.urs.obd

sealed class ObdResponse {
    data class Data(val bytes: List<Int>) : ObdResponse()
    object NoData : ObdResponse()
    data class Error(val message: String) : ObdResponse()
}

/**
 * Turns a raw ELM327 line (as read up to the `>` prompt, echo already
 * suppressed by `ATE0`) into structured [ObdResponse] for a given [pid].
 *
 * All four PIDs this module polls fit in a single CAN frame, so no
 * multi-frame (ISO-TP) reassembly is needed here - each response is one
 * line of hex byte pairs, optionally preceded by ELM327 chatter like
 * `SEARCHING...` on the first query after `ATSP0`.
 */
object ObdResponseParser {

    fun parse(raw: String, pid: ObdPid<*>): ObdResponse {
        val cleaned = raw.replace("SEARCHING...", " ")
            .trim()
            .uppercase()

        if (cleaned.isEmpty()) return ObdResponse.Error("Empty response")
        if (cleaned.contains("NO DATA")) return ObdResponse.NoData
        if (cleaned == "?" ||
            cleaned.contains("UNABLE TO CONNECT") ||
            cleaned.contains("STOPPED") ||
            cleaned.contains("ERROR") ||
            cleaned.contains("BUS INIT")
        ) {
            return ObdResponse.Error(cleaned)
        }

        val tokens = cleaned.split(Regex("[\\s\r\n]+")).filter { it.isNotBlank() }
        val mode = pid.command.substring(0, 2)
        val pidHex = pid.command.substring(2).uppercase()
        val expectedMode = (mode.toInt(16) + 0x40).toString(16).uppercase().padStart(2, '0')

        val headerIndex = (0..tokens.size - 2).firstOrNull { i ->
            tokens[i] == expectedMode && tokens[i + 1] == pidHex
        } ?: return ObdResponse.Error("Unrecognized response: $cleaned")

        val dataStart = headerIndex + 2
        val dataEnd = dataStart + pid.dataByteCount
        if (dataEnd > tokens.size) return ObdResponse.Error("Truncated response: $cleaned")

        val bytes = tokens.subList(dataStart, dataEnd).map { token ->
            token.toIntOrNull(16) ?: return ObdResponse.Error("Non-hex byte '$token' in: $cleaned")
        }
        return ObdResponse.Data(bytes)
    }
}
