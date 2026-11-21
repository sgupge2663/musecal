package jp.hayashida.musecal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * V1-1: Calendar 認可で実際に要求する scope が読み取り専用のみであることを固定する。
 * 定数の文字列だけでなく、認可要求の組み立て元である要求 scope の集合
 * （[CalendarAuth.REQUESTED_SCOPE_URIS]）そのものが calendar.readonly 1 件のみであることを検証する。
 * AuthorizationClient へはこの一覧を Scope オブジェクトへ 1 対 1 にラップして渡す。
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

    @Test
    fun requestedScopesAreExactlyCalendarReadOnly() {
        assertEquals(
            listOf("https://www.googleapis.com/auth/calendar.readonly"),
            CalendarAuth.REQUESTED_SCOPE_URIS,
        )
    }
}
