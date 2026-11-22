package jp.hayashida.musecal

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class EventDraftTest {
    private val start = LocalDateTime.of(2026, 12, 16, 9, 0)
    private fun draft(allDay: Boolean = false, color: String? = null) =
        EventDraft("予定", start, start.plusHours(1), allDay, "calendar", color, "場所", "説明")

    @Test fun allDayUsesExclusiveDateAndOmitsColorAndTime() {
        val body = createEventBody(draft(true), ZoneId.of("Asia/Tokyo"))
        assertEquals("2026-12-16", body.getJSONObject("start").getString("date"))
        assertEquals("2026-12-17", body.getJSONObject("end").getString("date"))
        assertFalse(body.getJSONObject("start").has("dateTime"))
        assertFalse(body.has("colorId"))
        assertEquals("場所", body.getString("location"))
        assertEquals("説明", body.getString("description"))
    }
    @Test fun timedUsesDeviceZoneAndExplicitColor() {
        val body = createEventBody(draft(color = "11"), ZoneId.of("Asia/Tokyo"))
        assertEquals("2026-12-16T09:00:00+09:00", body.getJSONObject("start").getString("dateTime"))
        assertEquals("2026-12-16T10:00:00+09:00", body.getJSONObject("end").getString("dateTime"))
        assertEquals("Asia/Tokyo", body.getJSONObject("start").getString("timeZone"))
        assertEquals("11", body.getString("colorId"))
        assertFalse(body.getJSONObject("start").has("date"))
        assertFalse(createEventBody(draft(), ZoneId.of("UTC")).has("colorId"))
    }
    @Test fun dstUsesOffsetAtEachEndpoint() {
        val d = draft().copy(start = LocalDateTime.of(2026, 3, 8, 1, 30), end = LocalDateTime.of(2026, 3, 8, 3, 30))
        val b = createEventBody(d, ZoneId.of("America/New_York"))
        assertTrue(b.getJSONObject("start").getString("dateTime").endsWith("-05:00"))
        assertTrue(b.getJSONObject("end").getString("dateTime").endsWith("-04:00"))
    }
    @Test fun writeFailuresUseExistingClassificationWithoutRawDetails() {
        assertTrue(writeFailureMessage(EventsApi.HttpError(403, "secret")).contains("書き込みの許可"))
        assertTrue(writeFailureMessage(EventsApi.HttpError(500, "secret")).contains("Google カレンダーに保存"))
        assertTrue(writeFailureMessage(java.io.IOException("secret")).contains("通信環境"))
        assertFalse(writeFailureMessage(EventsApi.HttpError(500, "secret")).contains("secret"))
    }
    @Test fun calendarPermissionsDefaultToReadOnly() {
        val roles = listOf("owner", "writer", "reader", "freeBusyReader", "")
        assertEquals(listOf("owner", "writer"), writableCalendars(roles.map { CalendarListEntry(it, it, accessRole = it) }).map { it.calendarId })
        val parsed = parseCalendarListPage("""{"items":[{"id":"a","accessRole":"writer"}]}""").first
        assertEquals(listOf("a"), writableCalendars(parsed).map { it.calendarId })
    }
    @Test fun invalidRangeRejectedAndMultiDayIsInclusive() {
        assertFalse(draft().copy(end = start).valid())
        assertTrue(draft(true).copy(end = start).valid())
        val b = createEventBody(draft(true, "1").copy(end = start.plusDays(2)), ZoneId.of("UTC"))
        assertEquals("2026-12-19", b.getJSONObject("end").getString("date"))
        assertEquals("1", b.getString("colorId"))
    }
}
