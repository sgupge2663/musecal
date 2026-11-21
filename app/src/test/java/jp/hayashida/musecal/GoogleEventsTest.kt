package jp.hayashida.musecal

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * V1-3: events.list の JSON → [CalEvent] の変換と、6週ウィンドウの取得判定・在メモリ保持。
 * fixture（src/test/resources/events/）は実 API（2026-11-21 に hatch_gws_cli で取得した 2026年12月分）と
 * 同じ形で、タイトル・id は架空のものに置き換えてある。
 */
class GoogleEventsTest {

    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val mainTab = CalendarTab("kou1995yurin530@gmail.com", "メイン", Color(0xFF9FE1E7))
    private val mokuyouTab = CalendarTab("mokuyoukai.ai@gmail.com", "AI木曜会", Color(0xFFFAD165))
    private val holidayTab = CalendarTab("ja.japanese#holiday@group.v.calendar.google.com", "日本の祝日", Color(0xFF9A9CFF))

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader!!.getResource("events/$name")) { name }.readText(Charsets.UTF_8)

    private fun page1() = parseEventsPage(fixture("main_dec2026.json"), mainTab, tokyo)
    private fun page2() = parseEventsPage(fixture("main_dec2026_page2.json"), mainTab, tokyo)
    private fun byId(events: List<CalEvent>, id: String) = events.single { it.eventId == id }

    @Test
    fun pageTokenIsReadAndAllItemsConverted() {
        val (events, next) = page1()
        assertEquals("page2token", next)
        assertEquals(6, events.size)
        assertNull(page2().second)
    }

    @Test
    fun timedEventUsesDateTimeConvertedToZone() {
        val e = byId(page1().first, "timedcolor0001")
        assertFalse(e.allDay)
        assertFalse(e.isBar)
        assertEquals(ZonedDateTime.of(2026, 11, 30, 18, 0, 0, 0, tokyo), e.start)
        assertEquals(ZonedDateTime.of(2026, 11, 30, 21, 20, 0, 0, tokyo), e.end)
        assertEquals(LocalDate.of(2026, 11, 30), e.startDate)
        assertEquals(LocalDate.of(2026, 11, 30), e.endDateInclusive)
        assertEquals("バイト", e.title)
    }

    @Test
    fun utcDateTimeIsShownInLocalZone() {
        // 08:05Z = 17:05 JST
        val e = byId(page1().first, "timednocolor0002")
        assertEquals(ZonedDateTime.of(2026, 12, 5, 17, 5, 0, 0, tokyo), e.start)
        assertEquals("映画館", e.location)
    }

    @Test
    fun overnightTimedEventSpansTwoDaysAsBar() {
        val e = byId(page1().first, "overnight0006")
        assertFalse(e.allDay)
        assertEquals(LocalDate.of(2026, 12, 18), e.startDate)
        assertEquals(LocalDate.of(2026, 12, 19), e.endDateInclusive)
        assertTrue(e.isBar)
    }

    @Test
    fun allDayEventUsesDateWithExclusiveEnd() {
        val single = byId(page1().first, "alldaysingle0003")
        assertTrue(single.allDay)
        assertNull(single.start)
        assertEquals(LocalDate.of(2026, 12, 16), single.startDate)
        assertEquals(LocalDate.of(2026, 12, 16), single.endDateInclusive)

        val range = byId(page1().first, "alldayrange0004")
        assertEquals(LocalDate.of(2026, 12, 30), range.startDate)
        assertEquals(LocalDate.of(2027, 1, 2), range.endDateInclusive)
    }

    @Test
    fun colorIdMapsToStandardEventPalette() {
        val events = page1().first
        assertEquals("4", byId(events, "timedcolor0001").colorId)
        assertEquals(Color(0xFFFF887C), byId(events, "timedcolor0001").baseColor)
        assertEquals(Color(0xFFDC2127), byId(events, "alldaysingle0003").baseColor)
        assertEquals(Color(0xFFFBD75B), byId(events, "alldayrange0004").baseColor)
        assertEquals(Color(0xFF5484ED), byId(page2().first, "weeklyrec01_20261203T110000Z").baseColor)
    }

    @Test
    fun noColorIdUsesCalendarBackgroundColor() {
        val e = byId(page1().first, "timednocolor0002")
        assertNull(e.colorId)
        assertEquals(Color(0xFF9FE1E7), e.baseColor)
        val h = parseEventsPage(fixture("holiday_dec2026.json"), holidayTab, tokyo).first.single()
        assertEquals(Color(0xFF9A9CFF), h.baseColor)
    }

    @Test
    fun colorIdOutsidePaletteFallsBackToCalendarColor() {
        val json = """{"items":[{"id":"x","status":"confirmed","summary":"a","colorId":"99",
            "start":{"date":"2026-12-01"},"end":{"date":"2026-12-02"}}]}"""
        val e = parseEventsPage(json, mokuyouTab, tokyo).first.single()
        assertEquals(Color(0xFFFAD165), e.baseColor)
    }

    @Test
    fun timedTextColorGoesThroughTextShadeOfPaletteColor() {
        // 時間指定予定の文字色は描画側が textShade(baseColor, 背景) で作る（T-108）。変換は baseColor を正しく渡すだけ
        val e = byId(page1().first, "timedcolor0001")
        assertEquals(textShade(Color(0xFFFF887C), Color.White), textShade(e.baseColor, Color.White))
    }

    @Test
    fun missingSummaryBecomesEmptyTitleWithoutDecoration() {
        val e = byId(page1().first, "notitle0005")
        assertEquals("", e.title)
    }

    @Test
    fun expandedRecurringInstancesAreSeparateEventsAndCancelledIsDropped() {
        val events = page2().first
        // 繰り返し予定は singleEvents=true で1回ずつに展開されて届く。cancelled（例外で消された回）は出さない
        assertEquals(
            listOf("weeklyrec01_20261203T110000Z", "weeklyrec01_20261210T110000Z", "weeklyrec02_20261220"),
            events.map { it.eventId },
        )
        assertEquals(listOf("週次ゼミ", "週次ゼミ", "週末の当番"), events.map { it.title })
        assertEquals(LocalDate.of(2026, 12, 10), events[1].startDate)
        assertTrue(events[2].allDay)
        assertEquals(LocalDate.of(2026, 12, 20), events[2].endDateInclusive)
    }

    @Test
    fun multipleCalendarsCarryTheirOwnCalendarIdAndNameForTabFilter() {
        val main = page1().first + page2().first
        val holiday = parseEventsPage(fixture("holiday_dec2026.json"), holidayTab, tokyo).first
        val all = main + holiday
        assertTrue(main.all { it.calendarId == mainTab.calendarId && it.calendarName == "メイン" })
        assertEquals(listOf(holidayTab.calendarId), holiday.map { it.calendarId })
        assertEquals("日本の祝日", holiday.single().calendarName)
        assertTrue(holiday.single().allDay)
        // タブで日本の祝日を非表示にしたとき、その予定だけが消える（MuseCalApp と同じ判定）
        val hidden = setOf(holidayTab.calendarId)
        assertEquals(main.size, all.count { it.calendarId !in hidden })
    }

    @Test
    fun calendarIdIsEncodedForPath() {
        assertEquals(
            "ja.japanese%23holiday%40group.v.calendar.google.com",
            EventsApi.pathSegment("ja.japanese#holiday@group.v.calendar.google.com"),
        )
        assertEquals("a%20b", EventsApi.pathSegment("a b"))
    }

    // ---------- ウィンドウ ----------

    private val nov29 = LocalDate.of(2026, 11, 29) // 2026年12月表示の先頭の日曜

    private fun weeks(from: LocalDate, n: Int) = List(n) { from.plusWeeks(it.toLong()) }

    @Test
    fun windowIsSixWeeksFromFirstUncoveredVisibleWeek() {
        assertEquals(EventWindow(nov29), windowToFetch(weeks(nov29, 6), emptySet()))
        assertEquals(LocalDate.of(2027, 1, 10), EventWindow(nov29).endExclusive)
        // 取得済みの範囲内なら取り直さない
        val covered = EventWindow(nov29).weekStarts.toSet()
        assertNull(windowToFetch(weeks(nov29, 6), covered))
        // 1週下へスクロールして7週目が見えたら、その週から6週を取る
        assertEquals(EventWindow(nov29.plusWeeks(6)), windowToFetch(weeks(nov29.plusWeeks(1), 6), covered))
        // 上へはみ出したら、はみ出した最初の週から
        assertEquals(EventWindow(nov29.minusWeeks(2)), windowToFetch(weeks(nov29.minusWeeks(2), 6), covered))
    }

    @Test
    fun storeCoversInFlightAndLoadedAndResetClearsAll() {
        val store = EventWindowStore()
        val w = EventWindow(nov29)
        store.markInFlight(w)
        assertNull(windowToFetch(weeks(nov29, 6), store.coveredWeeks()))
        store.fail(w)
        assertEquals(w, windowToFetch(weeks(nov29, 6), store.coveredWeeks()))
        store.put(w, page1().first)
        assertEquals(6, store.merged().size)
        val gen = store.generation
        store.reset()
        assertTrue(store.merged().isEmpty())
        assertTrue(store.coveredWeeks().isEmpty())
        assertTrue(store.generation != gen)
    }

    @Test
    fun overlappingWindowsDeduplicateByCalendarAndEventIdPreferringLatest() {
        val store = EventWindowStore()
        val first = page1().first
        store.put(EventWindow(nov29), first)
        val renamed = first.map { if (it.eventId == "alldayrange0004") it.copy(title = "帰省（変更）") else it }
            .filter { it.eventId == "alldayrange0004" }
        store.put(EventWindow(nov29.plusWeeks(4)), renamed)
        val merged = store.merged()
        assertEquals(6, merged.size)
        assertEquals("帰省（変更）", merged.single { it.eventId == "alldayrange0004" }.title)
        // 同じ event id でも別カレンダーなら別の予定
        store.put(EventWindow(nov29.plusWeeks(8)), listOf(first[0].copy(calendarId = mokuyouTab.calendarId)))
        assertEquals(7, store.merged().size)
    }
}
