package jp.hayashida.musecal

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException

class FetchFailureTest {
    @Test fun networkFailure_hasAction_withoutRawDetails() {
        val message = fetchFailureMessage(IOException("private response"))
        assertTrue(message.contains("通信環境"))
        assertTrue(message.contains("再試行"))
        assertFalse(message.contains("private response"))
    }

    @Test fun permissionFailure_suggestsSigningInAgain() {
        for (error in listOf(EventsApi.HttpError(403, "detail"), CalendarListApi.HttpError(401, "detail"))) {
            assertTrue(fetchFailureMessage(error).contains("ログインし直してください"))
        }
    }

    @Test fun incompleteResponse_explainsIncompleteFetch() {
        assertTrue(fetchFailureMessage(EventsApi.IncompleteError("detail")).contains("最後まで"))
    }
}
