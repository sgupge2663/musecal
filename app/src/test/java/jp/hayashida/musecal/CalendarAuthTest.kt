package jp.hayashida.musecal

import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** V2-1: 実際の要求スコープと SDK 応答の状態判定を固定する。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CalendarAuthTest {
    @Test fun requestedScopesAreExactlyCalendarEvents() {
        val expected = listOf("https://www.googleapis.com/auth/calendar.events")
        assertEquals(expected, CalendarAuth.REQUESTED_SCOPE_URIS)
        assertEquals(expected, CalendarAuth.requestedScopes().map { it.scopeUri })
    }

    private fun consentSender() = PendingIntent.getActivity(
        ApplicationProvider.getApplicationContext(), 0, Intent("consent"),
        PendingIntent.FLAG_IMMUTABLE,
    ).intentSender

    @Test fun tokenIsGrantedEvenWhenResolutionIsPresent() {
        assertEquals(CalendarAuth.CalendarAccess.Granted("ephemeral"),
            CalendarAuth.calendarAccessFromResult("ephemeral", consentSender()))
        assertEquals(CalendarAuth.CalendarAccess.Granted("ephemeral"),
            CalendarAuth.calendarAccessFromResult("ephemeral", null))
    }

    @Test fun missingTokenWithResolutionRequiresConsent() {
        val sender = consentSender()
        assertEquals(CalendarAuth.CalendarAccess.ConsentRequired(sender),
            CalendarAuth.calendarAccessFromResult(null, sender))
    }

    @Test fun missingTokenAndResolutionFails() {
        val result = CalendarAuth.calendarAccessFromResult(null, null)
        assertTrue(result is CalendarAuth.CalendarAccess.Failed)
        assertTrue((result as CalendarAuth.CalendarAccess.Failed).error is IllegalStateException)
    }
}
