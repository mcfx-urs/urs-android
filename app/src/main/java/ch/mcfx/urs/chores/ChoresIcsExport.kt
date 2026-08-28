package ch.mcfx.urs.chores

import ch.mcfx.urs.data.local.TrackerEventEntity
import ch.mcfx.urs.data.local.TrackerTypeEntity
import ch.mcfx.urs.data.local.publicId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * On-device iCalendar export for the Chores tracker (GitHub issue #28).
 * Pure functions over local Room data — no network, no Android APIs — so
 * the caller only has to write the returned strings out and hand them to
 * the share sheet.
 *
 * One [IcsFile] per distinct non-blank `calendar` label; every type
 * without a label is folded into a single unlabelled file. "Only new"
 * scope keeps, per type, the events dated on or after the type's last
 * export (all of them when it has never been exported); `exportAll`
 * ignores that marker. UIDs are stable, so re-exporting an event is safe
 * — a calendar app dedupes on the UID.
 */

data class IcsFile(val fileName: String, val content: String)

data class IcsExport(
    val files: List<IcsFile>,
    /** Local ids of the types whose events went into [files] — to be marked exported. */
    val exportedTypeLocalIds: List<Long>,
)

private const val UID_DOMAIN = "urs.mcfx.ch"
private val DATE_BASIC = DateTimeFormatter.ofPattern("yyyyMMdd")
private val DATETIME_BASIC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
private val DTSTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

fun buildChoresIcs(
    types: List<TrackerTypeEntity>,
    events: List<TrackerEventEntity>,
    exportAll: Boolean,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): IcsExport {
    val eventsByType: Map<String, List<TrackerEventEntity>> = events.groupBy { it.trackerTypeId }
    val dtStamp = DTSTAMP.format(now)

    // One VEVENT per file, always — bundling several VEVENTs into a single
    // multi-event .ics only surfaces the first one when opened in some
    // mobile calendar apps' "open .ics" flow (confirmed with Proton Mail
    // Mobile). A separate ACTION_SEND_MULTIPLE attachment per event, each
    // importable on its own, avoids that entirely.
    val files = mutableListOf<IcsFile>()
    val exportedTypeIds = mutableSetOf<Long>()

    for (type in types.sortedBy { it.name.lowercase() }) {
        val label = type.calendar?.trim()?.ifBlank { null }
        val cutoff: LocalDate? = if (exportAll) null else type.lastExportedAtMillis
            ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

        val typeEvents = eventsByType[type.publicId].orEmpty()
            .filter { event ->
                val day = runCatching { LocalDate.parse(event.occurredOn) }.getOrNull() ?: return@filter false
                cutoff == null || !day.isBefore(cutoff)
            }
        if (typeEvents.isEmpty()) continue

        exportedTypeIds += type.id
        for (event in typeEvents) {
            files += IcsFile(
                fileName = fileNameFor(label, type, event),
                content = vcalendar(listOf(vevent(type, event, dtStamp))),
            )
        }
    }

    return IcsExport(files, exportedTypeIds.toList())
}

private fun vcalendar(blocks: List<String>): String = buildString {
    append("BEGIN:VCALENDAR\r\n")
    append("VERSION:2.0\r\n")
    append("PRODID:-//urs//chores//EN\r\n")
    append("CALSCALE:GREGORIAN\r\n")
    blocks.forEach { append(it) }
    append("END:VCALENDAR\r\n")
}

private fun vevent(type: TrackerTypeEntity, event: TrackerEventEntity, dtStamp: String): String {
    val day = LocalDate.parse(event.occurredOn)
    val uid = "urs-${type.publicId}-${event.publicId}@$UID_DOMAIN"
    val time = event.occurredAt?.trim()?.ifBlank { null }
    val (dtStart, dtEnd) = if (time == null) {
        "DTSTART;VALUE=DATE:${DATE_BASIC.format(day)}" to "DTEND;VALUE=DATE:${DATE_BASIC.format(day.plusDays(1))}"
    } else {
        val parts = time.split(":")
        val start = day.atTime(parts.getOrNull(0)?.toIntOrNull() ?: 0, parts.getOrNull(1)?.toIntOrNull() ?: 0)
        "DTSTART:${DATETIME_BASIC.format(start)}" to "DTEND:${DATETIME_BASIC.format(start.plusMinutes(30))}"
    }
    return buildString {
        append("BEGIN:VEVENT\r\n")
        append("UID:").append(uid).append("\r\n")
        append("DTSTAMP:").append(dtStamp).append("\r\n")
        append(dtStart).append("\r\n")
        append(dtEnd).append("\r\n")
        append("SUMMARY:").append(escapeText(summaryFor(type, event))).append("\r\n")
        append("END:VEVENT\r\n")
    }
}

private fun summaryFor(type: TrackerTypeEntity, event: TrackerEventEntity): String {
    val emojiPrefix = if (trackerIconMaterialVector(type.icon) == null) type.icon.trim() + " " else ""
    val note = event.note?.trim()?.ifBlank { null }
    return emojiPrefix + if (note != null) "${type.name} — $note" else type.name
}

private fun escapeText(value: String): String = value
    .replace("\\", "\\\\")
    .replace(";", "\\;")
    .replace(",", "\\,")
    .replace("\n", "\\n")

private fun fileNameFor(label: String?, type: TrackerTypeEntity, event: TrackerEventEntity): String {
    val slug = (label ?: type.name).lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "calendar" }
    return "chores-$slug-${event.occurredOn}-${event.publicId}.ics"
}
