package jp.hayashida.musecal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CalendarVisibilityPreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun selectionRoundTripIncludingTurningBackOn() {
        CalendarVisibilityPreferences(context).saveHidden("roundtrip@example.test", setOf("work", "holiday"))
        val reopened = CalendarVisibilityPreferences(context)
        assertEquals(setOf("work", "holiday"), reopened.loadHidden("roundtrip@example.test"))
        reopened.saveHidden("roundtrip@example.test", setOf("holiday"))
        assertEquals(setOf("holiday"), CalendarVisibilityPreferences(context).loadHidden("roundtrip@example.test"))
        reopened.saveHidden("roundtrip@example.test", emptySet())
        assertTrue(CalendarVisibilityPreferences(context).loadHidden("roundtrip@example.test").isEmpty())
    }

    @Test fun unknownCalendarsAreOnAndRemovedCalendarsDoNotHideOthers() {
        val prefs = CalendarVisibilityPreferences(context)
        assertTrue(prefs.loadHidden("new@example.test").isEmpty())
        prefs.saveHidden("new@example.test", setOf("removed", "off"))
        val hidden = CalendarVisibilityPreferences(context).loadHidden("new@example.test")
        assertEquals(listOf("new", "on"), listOf("new", "off", "on").filter { it !in hidden })
    }

    @Test fun accountsAreIsolatedAndAuthSignOutDoesNotEraseSelection() {
        val prefs = CalendarVisibilityPreferences(context)
        prefs.saveHidden("a@example.test", setOf("shared"))
        assertTrue(prefs.loadHidden("b@example.test").isEmpty())
        prefs.saveHidden("b@example.test", setOf("other"))
        context.getSharedPreferences("calendar_auth", Context.MODE_PRIVATE).edit().clear().apply()
        val reopened = CalendarVisibilityPreferences(context)
        assertEquals(setOf("shared"), reopened.loadHidden("a@example.test"))
        assertEquals(setOf("other"), reopened.loadHidden("b@example.test"))
    }
}
