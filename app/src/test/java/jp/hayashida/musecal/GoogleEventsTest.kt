package jp.hayashida.musecal

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    /** 実 API と同じ形の1件だけの応答から変換する（照合区間 matchStart/End も変換経路で付く）。 */
    private fun one(item: String, calendarTz: String = "Asia/Tokyo", tab: CalendarTab = mainTab): CalEvent =
        parseEventsPage("""{"kind":"calendar#events","timeZone":"$calendarTz","items":[$item]}""", tab, tokyo).first.single()

    private fun allDay(id: String, date: String, endDate: String, calendarTz: String, tab: CalendarTab = mainTab) =
        one("""{"id":"$id","status":"confirmed","summary":"$id","start":{"date":"$date"},"end":{"date":"$endDate"}}""", calendarTz, tab)

    private fun timed(id: String, start: String, end: String) =
        one("""{"id":"$id","status":"confirmed","summary":"$id","start":{"dateTime":"$start"},"end":{"dateTime":"$end"}}""")

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
        store.put(w, page1().first, tokyo)
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
        store.put(EventWindow(nov29), first, tokyo)
        val renamed = first.map { if (it.eventId == "alldayrange0004") it.copy(title = "帰省（変更）") else it }
            .filter { it.eventId == "alldayrange0004" }
        store.put(EventWindow(nov29.plusWeeks(4)), renamed, tokyo)
        val merged = store.merged()
        assertEquals(6, merged.size)
        assertEquals("帰省（変更）", merged.single { it.eventId == "alldayrange0004" }.title)
        // 同じ event id でも別カレンダーなら別の予定
        store.put(EventWindow(nov29.plusWeeks(8)), listOf(first[0].copy(calendarId = mokuyouTab.calendarId)), tokyo)
        assertEquals(7, store.merged().size)
    }

    // ---------- Codex レビュー（e369ce3）指摘への対応 ----------

    @Test
    fun allPagesAreFetchedUntilNoNextPageToken() {
        val pages = mapOf(null to (listOf(1, 2) to "t1"), "t1" to (listOf(3) to "t2"), "t2" to (listOf(4) to null))
        val requested = mutableListOf<String?>()
        val all = EventsApi.fetchAllPages<Int> { token -> requested += token; pages.getValue(token) }
        assertEquals(listOf(1, 2, 3, 4), all)
        assertEquals(listOf(null, "t1", "t2"), requested)
    }

    @Test
    fun reachingPageLimitWithTokenLeftFailsInsteadOfReturningPartialResult() {
        // 指摘1: 上限に届いても nextPageToken が残っていれば失敗（部分結果を成功にしない）
        var calls = 0
        val e = runCatching {
            EventsApi.fetchAllPages<Int>(maxPages = 3) { _ -> calls++; listOf(calls) to "t$calls" }
        }.exceptionOrNull()
        assertTrue(e is EventsApi.IncompleteError)
        assertEquals(3, calls)
        // ちょうど上限ページで終わるなら成功
        var n = 0
        val ok = EventsApi.fetchAllPages<Int>(maxPages = 3) { _ -> n++; listOf(n) to (if (n < 3) "t$n" else null) }
        assertEquals(listOf(1, 2, 3), ok)
    }

    @Test
    fun repeatedPageTokenFails() {
        val e = runCatching { EventsApi.fetchAllPages<Int> { _ -> listOf(0) to "same" } }.exceptionOrNull()
        assertTrue(e is EventsApi.IncompleteError)
    }

    @Test
    fun refetchOfOverlappingWindowRemovesEventsThatNoLongerExist() {
        // 指摘2: 11/29 開始の窓で 12/10 の予定を取得 → 11/22 開始の窓（〜1/2）を再取得したら削除済みで返らない → 消える
        val store = EventWindowStore()
        val dec10 = byId(page1().first, "notitle0005") // 12/10
        val dec30 = byId(page1().first, "alldayrange0004") // 12/30〜1/2（新しい窓 11/22〜1/2 にかかる）
        val jan5 = timed("jan5", "2027-01-05T09:00:00+09:00", "2027-01-05T10:00:00+09:00")
        store.put(EventWindow(nov29), listOf(dec10, dec30, jan5), tokyo)
        val nov22 = nov29.minusWeeks(1)
        assertEquals(LocalDate.of(2027, 1, 3), EventWindow(nov22).endExclusive)
        store.put(EventWindow(nov22), listOf(dec30), tokyo)
        val merged = store.merged()
        assertEquals(listOf("alldayrange0004", "jan5"), merged.mapNotNull { it.eventId }.sorted())
        // 新しい窓の範囲外（1/5）の予定は古い窓の分として残る
        assertEquals(1, merged.count { it.eventId == "alldayrange0004" })
    }

    @Test
    fun movedEventDoesNotRemainAtOldDate() {
        val store = EventWindowStore()
        val e = byId(page1().first, "alldaysingle0003") // 12/16
        store.put(EventWindow(nov29), listOf(e), tokyo)
        val moved = allDay("alldaysingle0003", "2026-12-18", "2026-12-19", "Asia/Tokyo")
        store.put(EventWindow(nov29.plusWeeks(2)), listOf(moved), tokyo) // 12/13〜1/23
        assertEquals(listOf(LocalDate.of(2026, 12, 18)), store.merged().map { it.startDate })
    }

    @Test
    fun failedWindowIsRefetchedOnNextScrollStopAtSameWeeks() {
        // 指摘3: 失敗 → 同じ週のままスクロールが止まり直したら、再び取得対象になる
        val detector = ScrollStopDetector()
        val store = EventWindowStore()
        val idx = listOf(100, 101, 102, 103, 104, 105)
        val visible = weeks(nov29, 6)

        // 初回表示（停止状態）で知らせる → 取得開始
        assertEquals(idx, detector.onState(false, idx))
        val w = windowToFetch(visible, store.coveredWeeks())!!
        store.markInFlight(w)
        // 再評価されただけ（スクロールしていない）では知らせない
        assertNull(detector.onState(false, idx))
        // 取得失敗
        store.fail(w)
        // ユーザーが少しスクロールして同じ週で止まる
        assertNull(detector.onState(true, idx))
        assertEquals(idx, detector.onState(false, idx))
        assertEquals(w, windowToFetch(visible, store.coveredWeeks()))
        // 成功後は同じ週で止まっても取り直さない
        store.put(w, emptyList(), tokyo)
        assertNull(detector.onState(true, idx))
        assertEquals(idx, detector.onState(false, idx))
        assertNull(windowToFetch(visible, store.coveredWeeks()))
    }

    @Test
    fun scrollStopDetectorIgnoresWhileScrollingAndEmptyLayout() {
        val d = ScrollStopDetector()
        assertNull(d.onState(false, emptyList()))
        assertNull(d.onState(true, listOf(1, 2)))
        assertEquals(listOf(2, 3), d.onState(false, listOf(2, 3)))
        // 停止のまま週が変わった（animateScrollToItem 直後等）なら知らせる
        assertEquals(listOf(5, 6), d.onState(false, listOf(5, 6)))
    }

    // ---------- Codex 再レビュー（951c042）: 端末とカレンダーの TZ が違う終日予定 ----------

    private val laTab = CalendarTab("la@example.com", "LA", Color(0xFF9E9E9E))
    private val jan3 = LocalDate.of(2027, 1, 3) // 日曜

    @Test
    fun allDayMatchIntervalUsesCalendarTimeZoneWhileDisplayDateStaysAsIs() {
        val e = allDay("la-jan2", "2027-01-02", "2027-01-03", "America/Los_Angeles", laTab)
        // 表示日は date 文字列のまま
        assertEquals(LocalDate.of(2027, 1, 2), e.startDate)
        assertEquals(LocalDate.of(2027, 1, 2), e.endDateInclusive)
        // API との照合区間はカレンダー TZ の 0:00 = 日本時間 1/2 17:00〜1/3 17:00
        assertEquals(ZonedDateTime.of(2027, 1, 2, 17, 0, 0, 0, tokyo).toInstant(), e.matchStart)
        assertEquals(ZonedDateTime.of(2027, 1, 3, 17, 0, 0, 0, tokyo).toInstant(), e.matchEnd)
        // 応答に timeZone が無ければ端末の TZ
        val noTz = parseEventsPage(
            """{"items":[{"id":"x","status":"confirmed","start":{"date":"2027-01-02"},"end":{"date":"2027-01-03"}}]}""",
            laTab, tokyo,
        ).first.single()
        assertEquals(ZonedDateTime.of(2027, 1, 2, 0, 0, 0, 0, tokyo).toInstant(), noTz.matchStart)
        // 時間指定は時刻そのもの
        val t = timed("t", "2026-12-10T09:00:00+09:00", "2026-12-10T10:00:00+09:00")
        assertEquals(t.start!!.toInstant(), t.matchStart)
        assertEquals(t.end!!.toInstant(), t.matchEnd)
    }

    @Test
    fun deletedAllDayEventOfOtherTimeZoneCalendarDoesNotRemainAtOldDate() {
        // 端末 Asia/Tokyo、カレンダー America/Los_Angeles。11/29 窓で 2027/1/2 の終日予定を保持
        val store = EventWindowStore()
        val jan2 = allDay("la-jan2", "2027-01-02", "2027-01-03", "America/Los_Angeles", laTab)
        val dec20 = allDay("la-dec20", "2026-12-20", "2026-12-21", "America/Los_Angeles", laTab)
        store.put(EventWindow(nov29), listOf(jan2, dec20), tokyo)
        // 表示日だけで見ると 1/3 開始窓にかからない境界ケース（endDateInclusive 1/2 < 窓の開始 1/3）
        assertTrue(jan2.endDateInclusive < EventWindow(jan3).start)
        // 予定を削除 → 1/3 開始窓の完全応答には無い → API の範囲（1/3 00:00 JST 以降に終わる）に入るので消える
        store.put(EventWindow(jan3), emptyList(), tokyo)
        assertEquals(listOf("la-dec20"), store.merged().map { it.eventId })
    }

    @Test
    fun allDayEventOfOtherTimeZoneMovedOutOfNewWindowDoesNotRemainAtOldDate() {
        val store = EventWindowStore()
        store.put(EventWindow(nov29), listOf(allDay("la-jan2", "2027-01-02", "2027-01-03", "America/Los_Angeles", laTab)), tokyo)
        // 12/24 へ移動（新しい 1/3 窓に含まれない以前の日）。1/3 窓の完全応答には無い
        store.put(EventWindow(jan3), emptyList(), tokyo)
        assertTrue(store.merged().none { it.startDate == LocalDate.of(2027, 1, 2) })
        // 旧窓を取り直すと移動後の日付で出る（旧日付には残らない）
        store.put(EventWindow(nov29), listOf(allDay("la-jan2", "2026-12-24", "2026-12-25", "America/Los_Angeles", laTab)), tokyo)
        assertEquals(listOf(LocalDate.of(2026, 12, 24)), store.merged().map { it.startDate })
    }

    @Test
    fun allDayEventOfEastOfDeviceCalendarAtWindowEndIsRemovedWhenGone() {
        // 逆向き: カレンダー Pacific/Kiritimati（+14）の 1/3 終日 = 日本時間 1/2 19:00〜1/3 19:00。
        // 11/22 開始窓（timeMax = 1/3 00:00 JST）の API 範囲に入るので、その完全応答に無ければ消える
        val store = EventWindowStore()
        val e = allDay("kiri-jan3", "2027-01-03", "2027-01-04", "Pacific/Kiritimati", laTab)
        store.put(EventWindow(jan3), listOf(e), tokyo)
        assertTrue(e.startDate >= EventWindow(nov29.minusWeeks(1)).endExclusive) // 表示日では窓の外
        store.put(EventWindow(nov29.minusWeeks(1)), emptyList(), tokyo)
        assertTrue(store.merged().isEmpty())
    }

    @Test
    fun sameTimeZoneBoundariesAreUnchanged() {
        // 同一 TZ: 窓開始ちょうどで終わる予定・窓終了ちょうどから始まる予定は除去しない
        val store = EventWindowStore()
        val endsAtStart = allDay("ends-at-start", "2027-01-02", "2027-01-03", "Asia/Tokyo")   // 終了 = 1/3 00:00 = timeMin
        val timedEndsAtStart = timed("timed-ends", "2027-01-02T23:00:00+09:00", "2027-01-03T00:00:00+09:00")
        val startsAtEnd = allDay("starts-at-end", "2027-02-14", "2027-02-15", "Asia/Tokyo") // 開始 = 2/14 00:00 = timeMax
        store.put(EventWindow(nov29), listOf(endsAtStart, timedEndsAtStart), tokyo)
        store.put(EventWindow(jan3.plusWeeks(6)), listOf(startsAtEnd), tokyo)
        assertEquals(LocalDate.of(2027, 2, 14), EventWindow(jan3).endExclusive)
        store.put(EventWindow(jan3), emptyList(), tokyo)
        assertEquals(listOf("ends-at-start", "starts-at-end", "timed-ends"), store.merged().mapNotNull { it.eventId }.sorted())
    }

    // ---------- V1-4a 今すぐ同期（取得済みの窓の強制再取得） ----------

    private val applied = EventWindowStore.Outcome.Applied
    private val discarded = EventWindowStore.Outcome.Discarded
    private fun ok(events: List<CalEvent>) = Result.success(events)

    @Test
    fun syncNowRefetchesLoadedWindowThatScrollStopWouldSkip() {
        val store = EventWindowStore()
        val visible = weeks(nov29, 6)
        val w = EventWindow(nov29)
        store.put(w, page1().first, tokyo)
        // スクロール停止の経路では取得済みなので取らない
        assertNull(windowToFetch(visible, store.coveredWeeks()))
        // 今すぐ同期は取得済みでも同じ窓を取り直す
        assertEquals(listOf(w), windowsToRefresh(visible))
        val p = store.markInFlight(w)
        // Web 版で 12/16 の予定を削除し、帰省の名前を変えた後の応答
        val after = page1().first.filter { it.eventId != "alldaysingle0003" }
            .map { if (it.eventId == "alldayrange0004") it.copy(title = "帰省（変更）") else it }
        assertEquals(applied, store.finish(p, ok(after), tokyo, stillCurrent = true))
        val merged = store.merged()
        assertEquals(5, merged.size)
        assertTrue(merged.none { it.eventId == "alldaysingle0003" })
        assertEquals("帰省（変更）", merged.single { it.eventId == "alldayrange0004" }.title)
        assertTrue(store.coveredWeeks().containsAll(visible))
    }

    @Test
    fun syncNowCoversAllVisibleWeeksIncludingPartlyVisibleSeventh() {
        // 行の途中で止まって7週見えているときは、7週目から始まる窓も取り直す
        assertEquals(listOf(EventWindow(nov29), EventWindow(nov29.plusWeeks(6))), windowsToRefresh(weeks(nov29, 7)))
        assertTrue(windowsToRefresh(emptyList()).isEmpty())
    }

    @Test
    fun retryClearsOffscreenFailureOnlyWhenThatWindowSucceeds() {
        val store = EventWindowStore()
        val offscreen = EventWindow(nov29.minusWeeks(6))
        val failure = java.io.IOException("offline")
        assertTrue(store.finish(store.markInFlight(offscreen), Result.failure(failure), tokyo, true)
            is EventWindowStore.Outcome.Failed)
        var errors: Map<EventWindow?, String> = mapOf(offscreen to fetchFailureMessage(failure))
        assertNotNull(errors.values.firstOrNull()) // 表示外の失敗がエラー帯に出ている
        val targets = windowsToRefresh(weeks(nov29, 6), errors.keys.filterNotNull())
        assertEquals(listOf(EventWindow(nov29), offscreen), targets)
        // 表示窓だけ成功しても帯は消えない
        assertEquals(applied, store.finish(store.markInFlight(targets.first()), ok(emptyList()), tokyo, true))
        errors = errors.withoutSuccessfulWindows(listOf(targets.first()))
        assertNotNull(errors.values.firstOrNull())
        // 表示外の窓が再び失敗したら帯を残す
        assertTrue(store.finish(store.markInFlight(offscreen), Result.failure(failure), tokyo, true)
            is EventWindowStore.Outcome.Failed)
        assertNotNull(errors.values.firstOrNull())
        // 次の再試行でその窓も成功すると帯が消える
        assertTrue(offscreen in windowsToRefresh(weeks(nov29, 6), errors.keys.filterNotNull()))
        assertEquals(applied, store.finish(store.markInFlight(offscreen), ok(emptyList()), tokyo, true))
        errors = errors.withoutSuccessfulWindows(listOf(offscreen))
        assertNull(errors.values.firstOrNull())
    }

    @Test
    fun retryDeduplicatesWindowsAndPreservesAccessAndOtherWindowFailures() {
        val visible = EventWindow(nov29)
        val overlapping = EventWindow(nov29.plusWeeks(1))
        assertEquals(listOf(visible, overlapping), windowsToRefresh(weeks(nov29, 6), listOf(visible, overlapping)))
        assertEquals(listOf(overlapping), windowsToRefresh(emptyList(), listOf(overlapping)))
        val errors: Map<EventWindow?, String> = mapOf(null to "access", visible to "visible", overlapping to "overlap")
        assertEquals(mapOf(null to "access", overlapping to "overlap"), errors.withoutSuccessfulWindows(listOf(visible)))
    }

    @Test
    fun failedSyncNowKeepsExistingEventsAndReportsFailure() {
        val store = EventWindowStore()
        val w = EventWindow(nov29)
        store.put(w, page1().first, tokyo)
        val before = store.merged()
        val p = store.markInFlight(w)
        val error = EventsApi.HttpError(503, "予定の取得に失敗しました（HTTP 503）")
        val outcome = store.finish(p, Result.failure(error), tokyo, stillCurrent = true)
        // 失敗は理由つきで呼び出し側へ返る（トーストに出す）
        assertTrue(outcome is EventWindowStore.Outcome.Failed)
        assertEquals(error, (outcome as EventWindowStore.Outcome.Failed).error)
        // 表示中の予定は消えず、取得済みの扱いも変わらない（スクロールで取り直しが暴走しない）
        assertEquals(before, store.merged())
        assertNull(windowToFetch(weeks(nov29, 6), store.coveredWeeks()))
        // 取り消し（画面を離れた等）は失敗として知らせない
        val q = store.markInFlight(w)
        assertEquals(discarded, store.finish(q, Result.failure(kotlinx.coroutines.CancellationException()), tokyo, true))
        assertEquals(before, store.merged())
    }

    @Test
    fun olderScrollFetchReturningAfterSyncNowDoesNotOverwriteNewerResult() {
        // スクロール由来の取得 A の途中で今すぐ同期 B を押す。B が先に返り、A（古い内容）が後から返る
        val store = EventWindowStore()
        val w = EventWindow(nov29)
        val a = store.markInFlight(w)
        val b = store.markInFlight(w)
        val newer = page1().first.filter { it.eventId != "alldaysingle0003" }
        assertEquals(applied, store.finish(b, ok(newer), tokyo, true))
        assertEquals(discarded, store.finish(a, ok(page1().first), tokyo, true))
        assertEquals(newer.map { it.eventId }, store.merged().map { it.eventId })
    }

    @Test
    fun olderNov22FetchCannotUndoNov29SyncDeletionOnDec10() {
        val store = EventWindowStore()
        val nov22 = nov29.minusWeeks(1)
        val old = listOf(allDay("dec10", "2026-12-10", "2026-12-11", "Asia/Tokyo", mainTab))
        store.put(EventWindow(nov29), old, tokyo)
        val a = store.markInFlight(EventWindow(nov22)) // 自動取得が先に開始
        val b = store.markInFlight(EventWindow(nov29)) // 別開始日の今すぐ同期
        assertEquals(applied, store.finish(b, ok(emptyList()), tokyo, true))
        assertTrue(store.merged().isEmpty()) // 12/10 の削除
        assertEquals(discarded, store.finish(a, ok(old), tokyo, true))
        assertTrue(store.merged().isEmpty())
        assertFalse(store.coveredWeeks().contains(nov22)) // 印は解除、未取得部分は再試行可能
        val retry = store.markInFlight(EventWindow(nov22))
        assertEquals(applied, store.finish(retry, ok(emptyList()), tokyo, true))
    }

    @Test
    fun newerDisjointSuccessDoesNotDiscardOlderFetch() {
        val store = EventWindowStore()
        val a = store.markInFlight(EventWindow(nov29))
        val b = store.markInFlight(EventWindow(nov29.plusWeeks(6)))
        assertEquals(applied, store.finish(b, ok(emptyList()), tokyo, true))
        assertEquals(applied, store.finish(a, ok(page1().first), tokyo, true))
        assertEquals(page1().first, store.merged())
    }

    @Test
    fun olderScrollFetchReturningBeforeSyncNowIsDiscardedAndSyncNowDecides() {
        val store = EventWindowStore()
        val w = EventWindow(nov29)
        val a = store.markInFlight(w)
        val b = store.markInFlight(w)
        // 古い A は先に返っても反映しない（失敗でもトーストしない）。取得中の扱いは B が残す
        assertEquals(discarded, store.finish(a, Result.failure(java.io.IOException("x")), tokyo, true))
        assertNull(windowToFetch(weeks(nov29, 6), store.coveredWeeks()))
        assertEquals(applied, store.finish(b, ok(page1().first), tokyo, true))
        assertEquals(6, store.merged().size)
    }

    @Test
    fun syncNowResultIsDiscardedAfterResetOrWhenTicketIsNoLongerCurrent() {
        // 世代: 同期中にアカウント・タブが変わって reset → 返った結果は新しい状態へ混ざらない
        val store = EventWindowStore()
        val w = EventWindow(nov29)
        store.put(w, page1().first, tokyo)
        val p = store.markInFlight(w)
        store.reset()
        val fresh = store.markInFlight(w) // 新しいアカウントのスクロール由来の取得
        assertEquals(discarded, store.finish(p, ok(page1().first), tokyo, true))
        assertTrue(store.merged().isEmpty())
        assertEquals(applied, store.finish(fresh, ok(page2().first), tokyo, true))
        assertEquals(page2().first.map { it.eventId }, store.merged().map { it.eventId })
        // 札: 同じ世代でも、札が古ければ（サインアウト等）成功も失敗も反映しない。取得中の印は外す
        val q = store.markInFlight(EventWindow(nov29.plusWeeks(6)))
        assertEquals(discarded, store.finish(q, ok(page1().first), tokyo, stillCurrent = false))
        assertEquals(page2().first.map { it.eventId }, store.merged().map { it.eventId })
        assertEquals(EventWindow(nov29.plusWeeks(6)), windowToFetch(weeks(nov29.plusWeeks(6), 6), store.coveredWeeks()))
    }
}
