package jp.hayashida.musecal

import androidx.compose.ui.graphics.Color
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EventEditTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private fun event(allDay: Boolean) = googleEventToCalEvent(JSONObject("""{
      "id":"instance", "recurringEventId":"master", "summary":"予定", "colorId":"11",
      "location":"会議室", "description":"説明",
      "start":${if (allDay) """{"date":"2026-12-16"}""" else """{"dateTime":"2026-12-16T23:00:00+09:00"}"""},
      "end":${if (allDay) """{"date":"2026-12-18"}""" else """{"dateTime":"2026-12-17T00:00:00+09:00"}"""}
    }"""), CalendarTab("test", "メイン", Color.Gray), zone)!!

    @Test fun deletionRequiresPermissionConfirmationAndCorrectIdentity() {
        val instance = event(true)
        val single = instance.copy(recurringEventId = null)
        assertEquals("instance", deletionEventId(single, true, true, DeleteScope.THIS_EVENT))
        assertEquals("instance", deletionEventId(instance, true, true, DeleteScope.THIS_EVENT))
        assertEquals("master", deletionEventId(instance, true, true, DeleteScope.ALL_EVENTS))
        for (scope in DeleteScope.values()) {
            assertNull(deletionEventId(instance, false, true, scope))
            assertNull(deletionEventId(instance, true, false, scope))
            assertNull(deletionEventId(instance.copy(eventId = null), true, true, scope))
        }
    }
    @Test fun initialValuesAndRecurringIdentity() {
        val e = event(true)
        val d = e.toDraft()
        assertEquals("master", e.recurringEventId)
        assertEquals("instance", e.eventId)
        assertTrue(d.allDay)
        assertEquals("2026-12-17", d.end.toLocalDate().toString())
        assertEquals("11", d.colorId)
        assertEquals("会議室", d.location)
        assertEquals("説明", d.description)
        assertEquals("予定", d.title)
        assertNull(e.copy(recurringEventId = null).recurringEventId)
    }
    @Test fun timedMidnightKeepsActualEnd() {
        val d = event(false).toDraft()
        assertFalse(d.allDay)
        assertEquals("2026-12-17T00:00", d.end.toString())
        assertEquals("Asia/Tokyo", createEventBody(d, zone).getJSONObject("end").getString("timeZone"))
    }
    @Test fun updateReplacesEndpointsClearsColorAndPreservesUneditedFields() {
        val current = JSONObject("""{"colorId":"11","start":{"dateTime":"old"},"recurrence":["RRULE:FREQ=WEEKLY"],"attendees":[{"email":"fixture@example.com"}]}""")
        val result = updateEventBody(current, event(true).toDraft().copy(colorId = null), zone)
        assertFalse(result.has("colorId"))
        assertFalse(result.getJSONObject("start").has("dateTime"))
        assertEquals("2026-12-18", result.getJSONObject("end").getString("date"))
        assertEquals(current.getJSONArray("recurrence").toString(), result.getJSONArray("recurrence").toString())
        assertEquals(current.getJSONArray("attendees").toString(), result.getJSONArray("attendees").toString())
        assertEquals("11", current.getString("colorId"))
    }
}
