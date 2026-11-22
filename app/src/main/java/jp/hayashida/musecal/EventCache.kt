package jp.hayashida.musecal

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/** Account-scoped data only; no credentials. Caller serializes IO to preserve write order. */
class EventCache(private val directory: File) {
    data class Entry(val tabs: List<CalendarTab>, val snapshot: EventWindowStore.Snapshot)

    internal fun fileFor(email: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(email.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$hash.json")
    }

    fun read(email: String): Entry? = runCatching {
        val root = JSONObject(fileFor(email).readText())
        require(root.getInt("version") == 2 && root.getString("account") == email)
        val tabs = root.getJSONArray("tabs").objects().map {
            CalendarTab(it.getString("id"), it.getString("label"), Color(it.getString("color").toULong()))
        }
        val windows = linkedMapOf<LocalDate, List<CalEvent>>()
        root.getJSONArray("windows").objects().forEach { w ->
            windows[LocalDate.parse(w.getString("start"))] = w.getJSONArray("events").objects().map { e ->
                CalEvent(
                    e.getString("title"), e.getBoolean("allDay"),
                    LocalDate.parse(e.getString("startDate")), LocalDate.parse(e.getString("endDate")),
                    e.nullable("start")?.let(ZonedDateTime::parse), e.nullable("end")?.let(ZonedDateTime::parse),
                    e.nullable("colorId"), e.getString("calendarId"), e.getString("calendarName"),
                    Color(e.getString("calendarColor").toULong()), e.getString("location"), e.nullable("eventId"),
                    e.nullable("matchStart")?.let(Instant::parse), e.nullable("matchEnd")?.let(Instant::parse),
                    e.optString("description", ""), e.nullable("recurringEventId"),
                )
            }
        }
        Entry(tabs, EventWindowStore.Snapshot(windows, Instant.parse(root.getString("fetchedAt"))))
    }.getOrNull()

    fun write(email: String, entry: Entry) {
        val root = JSONObject().put("version", 2).put("account", email)
            .put("fetchedAt", entry.snapshot.fetchedAt.toString())
            .put("tabs", JSONArray(entry.tabs.map {
                JSONObject().put("id", it.calendarId).put("label", it.label).put("color", it.color.value.toString())
            }))
            .put("windows", JSONArray(entry.snapshot.windows.map { (start, events) ->
                JSONObject().put("start", start.toString()).put("events", JSONArray(events.map { e ->
                    JSONObject().put("title", e.title).put("allDay", e.allDay)
                        .put("startDate", e.startDate.toString()).put("endDate", e.endDateInclusive.toString())
                        .put("start", e.start?.toString()).put("end", e.end?.toString())
                        .put("colorId", e.colorId).put("calendarId", e.calendarId).put("calendarName", e.calendarName)
                        .put("calendarColor", e.calendarColor.value.toString()).put("location", e.location)
                        .put("description", e.description).put("recurringEventId", e.recurringEventId).put("eventId", e.eventId).put("matchStart", e.matchStart?.toString()).put("matchEnd", e.matchEnd?.toString())
                }))
            }))
        directory.mkdirs()
        val target = fileFor(email)
        val temporary = File(directory, target.name + ".tmp")
        temporary.writeText(root.toString())
        check(temporary.renameTo(target)) { "Cache replacement failed" }
    }
}

private fun JSONArray.objects() = List(length()) { getJSONObject(it) }
private fun JSONObject.nullable(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
