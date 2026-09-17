package ch.mcfx.urs.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.LocationCaptureLogEntity
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ExportTimestampFormat = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())

/**
 * One CSV line per [LocationCaptureLogEntity], every field the entity
 * carries (GitHub issue #86) — so an export lines up with an on-device
 * cross-check against Android's own location-access indicator (Settings →
 * Location → recent app accesses).
 */
fun buildLocationCaptureLogCsv(entries: List<LocationCaptureLogEntity>): String {
    val header = listOf(
        "timestamp", "eventType", "detail", "mode",
        "startMillis", "endMillis", "durationMillis",
        "scheduledForMillis", "driftMillis",
        "batteryPercent", "isCharging", "isPowerSaveMode", "isDeviceIdleMode",
    ).joinToString(",")
    val lines = entries.sortedBy { it.timestampMillis }.map { e ->
        val durationMillis = if (e.startMillis != null && e.endMillis != null) e.endMillis - e.startMillis else null
        val driftMillis = if (e.scheduledForMillis != null && e.startMillis != null) e.startMillis - e.scheduledForMillis else null
        listOf(
            ExportTimestampFormat.format(Instant.ofEpochMilli(e.timestampMillis)),
            e.eventType,
            csvEscape(e.detail),
            csvEscape(e.mode.orEmpty()),
            e.startMillis?.toString().orEmpty(),
            e.endMillis?.toString().orEmpty(),
            durationMillis?.toString().orEmpty(),
            e.scheduledForMillis?.toString().orEmpty(),
            driftMillis?.toString().orEmpty(),
            e.batteryPercent?.toString().orEmpty(),
            e.isCharging?.toString().orEmpty(),
            e.isPowerSaveMode?.toString().orEmpty(),
            e.isDeviceIdleMode?.toString().orEmpty(),
        ).joinToString(",")
    }
    return (listOf(header) + lines).joinToString("\n")
}

private fun csvEscape(value: String): String =
    if (value.contains(',') || value.contains('"') || value.contains('\n')) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }

/**
 * Writes the CSV into cacheDir/location-log/ and hands it to the share sheet
 * through the app's FileProvider — same pattern as ChoresScreen.kt's ICS
 * export, minus its "mark exported" chooser-result receiver, since there's
 * no persisted exported-state to update for this debug log.
 */
fun shareLocationCaptureLogExport(context: Context, entries: List<LocationCaptureLogEntity>) {
    if (entries.isEmpty()) {
        Toast.makeText(context, R.string.about_location_capture_export_nothing, Toast.LENGTH_SHORT).show()
        return
    }
    val dir = File(context.cacheDir, "location-log").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "location-capture-log.csv")
    file.writeText(buildLocationCaptureLogCsv(entries))
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(sendIntent, null)) }
        .onFailure { Toast.makeText(context, R.string.about_location_capture_export_no_app, Toast.LENGTH_SHORT).show() }
}
