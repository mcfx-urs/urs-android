package ch.mcfx.urs.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import ch.mcfx.urs.R
import ch.mcfx.urs.data.sync.PullRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ExportTimestampFormat = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())

/** One CSV line per domain result of every retained pull pass, oldest pass first. */
fun buildPullHistoryCsv(history: List<PullRecord>): String {
    val header = "pulledAt,trigger,network,vpn,domain,ok,cause,durationMs,bytes"
    val lines = history.flatMap { record ->
        val pulledAt = ExportTimestampFormat.format(Instant.ofEpochMilli(record.atMillis))
        record.results.map { r -> listOf(pulledAt, record.trigger?.name?.lowercase().orEmpty(), record.network.network, record.network.vpn.toString(), r.domain, r.ok.toString(), pullCsvEscape(r.cause.orEmpty()), r.durationMs.toString(), r.bytes.toString()).joinToString(",") }
    }
    return (listOf(header) + lines).joinToString("\n")
}

private fun pullCsvEscape(value: String): String =
    if (value.contains(',') || value.contains('"') || value.contains('\n')) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }

/** Writes the CSV to the document the user picked in the system "save as" dialog. */
fun writePullHistoryExport(context: Context, uri: Uri, history: List<PullRecord>) {
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { it.write(buildPullHistoryCsv(history).toByteArray()) }
            ?: error("no output stream")
    }.onFailure { Toast.makeText(context, R.string.about_pull_export_failed, Toast.LENGTH_SHORT).show() }
}
