package jp.hayashida.musecal

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** V1-2: calendarList → 下部タブの対応づけ（[mapCalendarTabs]）と calendarList JSON の読み取り。 */
class CalendarsTest {

    private val main = CalendarListEntry("kou1995yurin530@gmail.com", "kou1995yurin530@gmail.com", backgroundColor = "#9fe1e7", primary = true)
    private val mokuyou = CalendarListEntry("mokuyoukai.ai@gmail.com", "AI木曜会", backgroundColor = "#fad165")
    private val holiday = CalendarListEntry("ja.japanese#holiday@group.v.calendar.google.com", "日本の祝日", backgroundColor = "#9a9cff")

    @Test
    fun realCalendarListMapsToThreeTabsInFixedOrderWithCalendarColors() {
        // calendarList の返却順はタブ順と違ってもよい（タブ順は KNOWN_TABS の順）
        val tabs = mapCalendarTabs(listOf(holiday, main, mokuyou))
        assertEquals(
            listOf(
                CalendarTab("kou1995yurin530@gmail.com", "メイン", Color(0xFF9FE1E7)),
                CalendarTab("mokuyoukai.ai@gmail.com", "AI木曜会", Color(0xFFFAD165)),
                CalendarTab("ja.japanese#holiday@group.v.calendar.google.com", "日本の祝日", Color(0xFF9A9CFF)),
            ),
            tabs,
        )
    }

    @Test
    fun tabColorComesFromCalendarListNotFromFallback() {
        val tabs = mapCalendarTabs(listOf(main.copy(backgroundColor = "#123456")))
        assertEquals(listOf(CalendarTab(main.id, "メイン", Color(0xFF123456))), tabs)
    }

    @Test
    fun unreadableColorFallsBackToKnownTabColor() {
        val tabs = mapCalendarTabs(listOf(mokuyou.copy(backgroundColor = null), holiday.copy(backgroundColor = "zzz")))
        assertEquals(listOf(Color(0xFFFAD165), Color(0xFF9A9CFF)), tabs.map { it.color })
    }

    @Test
    fun matchingIsExactIdOnly() {
        // 大文字違い・前後空白・名前の一致では既存タブに対応づけない（未知カレンダー扱いになる）
        val tabs = mapCalendarTabs(
            listOf(
                main.copy(id = "Kou1995yurin530@gmail.com"),
                mokuyou.copy(id = " mokuyoukai.ai@gmail.com"),
                CalendarListEntry("other@group.calendar.google.com", "日本の祝日", backgroundColor = "#9a9cff"),
            )
        )
        assertEquals(listOf("Kou1995yurin530@gmail.com", " mokuyoukai.ai@gmail.com", "other@group.calendar.google.com"), tabs.map { it.calendarId })
        // ラベルは既存タブ名ではなく calendarList の summary
        assertEquals(listOf("kou1995yurin530@gmail.com", "AI木曜会", "日本の祝日"), tabs.map { it.label })
    }

    @Test
    fun unknownCalendarsAreAppendedAfterKnownTabsInListOrder() {
        val sports = CalendarListEntry("sports@group.calendar.google.com", "スポーツ", backgroundColor = "#7bd148")
        val priv = CalendarListEntry("private@group.calendar.google.com", "private", summaryOverride = "プライベート", backgroundColor = "#ff887c")
        val tabs = mapCalendarTabs(listOf(sports, holiday, priv, main, mokuyou))
        assertEquals(
            listOf(main.id, mokuyou.id, holiday.id, sports.id, priv.id),
            tabs.map { it.calendarId },
        )
        assertEquals(listOf("メイン", "AI木曜会", "日本の祝日", "スポーツ", "プライベート"), tabs.map { it.label })
        assertEquals(Color(0xFF7BD148), tabs[3].color)
    }

    @Test
    fun unknownCalendarWithoutNameOrColorUsesIdAndGray() {
        val tabs = mapCalendarTabs(listOf(CalendarListEntry("x@group.calendar.google.com", "")))
        assertEquals(listOf(CalendarTab("x@group.calendar.google.com", "x@group.calendar.google.com", Color(0xFF9E9E9E))), tabs)
    }

    @Test
    fun missingKnownCalendarIsNotShownAndDuplicatesAreDropped() {
        val tabs = mapCalendarTabs(listOf(main, main.copy(backgroundColor = "#000000"), holiday))
        assertEquals(listOf("メイン", "日本の祝日"), tabs.map { it.label })
        assertEquals(Color(0xFF9FE1E7), tabs[0].color)
        assertEquals(emptyList<CalendarTab>(), mapCalendarTabs(emptyList()))
    }

    @Test
    fun parseHexColorAcceptsRgbAndArgbOnly() {
        assertEquals(Color(0xFF9FE1E7), parseHexColor("#9fe1e7"))
        assertEquals(Color(0x809FE1E7), parseHexColor("#809fe1e7"))
        assertNull(parseHexColor("#fff"))
        assertNull(parseHexColor(null))
        assertNull(parseHexColor("#gggggg"))
    }

    @Test
    fun parsesCalendarListPage() {
        val json = """
            {"items":[
              {"id":"kou1995yurin530@gmail.com","summary":"kou1995yurin530@gmail.com","backgroundColor":"#9fe1e7","primary":true},
              {"id":"mokuyoukai.ai@gmail.com","summary":"AI木曜会","summaryOverride":"木曜会","backgroundColor":"#fad165"},
              {"summary":"id なしは捨てる"}
            ],"nextPageToken":"p2"}
        """.trimIndent()
        val (entries, next) = parseCalendarListPage(json)
        assertEquals(
            listOf(
                CalendarListEntry("kou1995yurin530@gmail.com", "kou1995yurin530@gmail.com", null, "#9fe1e7", true),
                CalendarListEntry("mokuyoukai.ai@gmail.com", "AI木曜会", "木曜会", "#fad165", false),
            ),
            entries,
        )
        assertEquals("p2", next)
        assertNull(parseCalendarListPage("""{"items":[]}""").second)
    }

    @Test
    fun sampleCalendarNamesResolveToKnownCalendarIds() {
        assertEquals("kou1995yurin530@gmail.com", knownCalendarIdForName("メイン"))
        assertEquals("ja.japanese#holiday@group.v.calendar.google.com", knownCalendarIdForName("日本の祝日"))
        assertNull(knownCalendarIdForName("スポーツ"))
    }
}
