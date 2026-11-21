package jp.hayashida.musecal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * V1-1: Calendar 認可で要求する scope が読み取り専用のみであることを固定する。
 * 書き込み scope を足す場合は v2 の仕様変更として扱う（proposal の範囲外）。
 */
class CalendarAuthTest {

    @Test
    fun calendarScopeIsReadOnly() {
        assertEquals(
            "https://www.googleapis.com/auth/calendar.readonly",
            CalendarAuth.CALENDAR_READONLY_SCOPE,
        )
    }
}
