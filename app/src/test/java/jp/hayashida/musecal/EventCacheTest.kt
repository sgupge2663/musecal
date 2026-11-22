package jp.hayashida.musecal

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.ZoneId

class EventCacheTest {
    @get:Rule val temp = TemporaryFolder()
    private val tab = CalendarTab("calendar", "Test", Color(0xFF123456))
    private val zone = ZoneId.of("Asia/Tokyo")
    private val window = EventWindow(LocalDate.parse("2026-11-22"))

    @Test fun roundTrip() {
        val store = EventWindowStore()
        val event = googleEventToCalEvent(org.json.JSONObject("""{"id":"test","summary":"Sample","start":{"dateTime":"2026-12-01T09:00:00+09:00"},"end":{"dateTime":"2026-12-01T10:00:00+09:00"},"location":"Room","colorId":"3"}"""), tab, zone)!!
        val allDay = googleEventToCalEvent(org.json.JSONObject("""{"id":"day","start":{"date":"2026-12-02"},"end":{"date":"2026-12-04"}}"""), tab, zone, ZoneId.of("America/Los_Angeles"))!!
        store.put(window, listOf(event, allDay), zone)
        store.put(EventWindow(window.start.plusWeeks(6)), emptyList(), zone)
        val cache = EventCache(temp.root)
        cache.write("a@example.test", EventCache.Entry(listOf(tab), store.snapshot()))
        val entry = cache.read("a@example.test")!!
        assertEquals(listOf(tab), entry.tabs)
        assertEquals(store.snapshot(), entry.snapshot)
        val restored = EventWindowStore()
        restored.restore(entry.snapshot)
        assertEquals(store.merged(), restored.merged())
        assertEquals(store.coveredWeeks(), restored.coveredWeeks())
        assertEquals(store.lastFetchedAt, restored.lastFetchedAt)
    }

    @Test fun accountIsolation() {
        val cache = EventCache(temp.root)
        val store = EventWindowStore()
        store.put(window, emptyList(), zone)
        cache.write("a@example.test", EventCache.Entry(listOf(tab), store.snapshot()))
        assertNull(cache.read("b@example.test"))
        cache.fileFor("a@example.test").copyTo(cache.fileFor("b@example.test"))
        assertNull(cache.read("b@example.test"))
    }

    @Test fun corruptIsEmpty() {
        val cache = EventCache(temp.root)
        cache.fileFor("a@example.test").writeText("{broken")
        assertNull(cache.read("a@example.test"))
        cache.fileFor("a@example.test").writeText("""{"version":99}""")
        assertNull(cache.read("a@example.test"))
    }

    @Test fun failedFetchPreservesTimestampAndResetClearsIt() {
        val store = EventWindowStore()
        store.put(window, emptyList(), zone)
        val before = store.snapshot()
        val pending = store.markInFlight(window)
        store.finish(pending, Result.failure(java.io.IOException("offline")), zone, true)
        assertEquals(before, store.snapshot())
        store.reset()
        assertNull(store.lastFetchedAt)
        assertTrue(store.merged().isEmpty())
        assertTrue(store.coveredWeeks().isEmpty())
    }

    @Test fun discardedWindowIsImmediatelyEligible() {
        val store = EventWindowStore()
        val old = store.markInFlight(window)
        val newer = store.markInFlight(EventWindow(window.start.plusWeeks(1)))
        store.finish(newer, Result.success(emptyList()), zone, true)
        assertEquals(EventWindowStore.Outcome.Discarded, store.finish(old, Result.success(emptyList()), zone, true))
        val visible = List(7) { window.start.plusWeeks(it.toLong()) }
        val retry = windowToFetch(visible, store.coveredWeeks())
        assertEquals(window, retry)
        store.finish(store.markInFlight(retry!!), Result.success(emptyList()), zone, true)
        assertNull(windowToFetch(visible, store.coveredWeeks()))
    }
}
