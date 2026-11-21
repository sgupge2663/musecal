package jp.hayashida.musecal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V1-2（Codex レビュー条件）: 取得要求をアカウントに紐づけ、今のアカウントと一致しない結果を捨てる（[AccountRequestGate]）。
 *
 * MuseCalRoot と同じ手順（アカウントが決まる → begin、サインアウト → invalidate、結果が返る → isCurrent なら反映）を
 * 小さな模型（[Root]）で再現し、「取得中にサインアウト → 別アカウントへサインイン」で古い結果が反映されないことを確かめる。
 */
class AccountRequestGateTest {

    /** MuseCalRoot の状態遷移の模型。tabs は反映された結果（どのアカウントの一覧か）。 */
    private class Root {
        val gate = AccountRequestGate()
        var signedIn: String? = null
        var tabs: String? = null

        fun signIn(email: String): AccountRequestGate.Ticket {
            signedIn = email
            tabs = null
            return gate.begin(email)
        }

        fun signOut() {
            signedIn = null
            tabs = null
            gate.invalidate()
        }

        /** 取得結果が（遅れて）返ってきたとき。反映したら true。 */
        fun deliver(ticket: AccountRequestGate.Ticket, result: String): Boolean {
            if (!gate.isCurrent(ticket, signedIn)) return false
            tabs = result
            return true
        }
    }

    @Test
    fun lateResultOfPreviousAccountDoesNotOverwriteNewAccountTabs() {
        val root = Root()
        val ticketA = root.signIn("a@example.com")   // A の取得開始（まだ返らない）
        root.signOut()
        val ticketB = root.signIn("b@example.com")   // B の取得開始

        // A の結果が B より後に返っても捨てる
        assertTrue(root.deliver(ticketB, "B の一覧"))
        assertFalse(root.deliver(ticketA, "A の一覧"))
        assertEquals("B の一覧", root.tabs)
    }

    @Test
    fun lateResultOfPreviousAccountArrivingBeforeNewResultIsAlsoDiscarded() {
        val root = Root()
        val ticketA = root.signIn("a@example.com")
        root.signOut()
        val ticketB = root.signIn("b@example.com")

        // A の結果が B の結果より先に返る順でも、B のタブに A の一覧を出さない
        assertFalse(root.deliver(ticketA, "A の一覧"))
        assertEquals(null, root.tabs)
        assertTrue(root.deliver(ticketB, "B の一覧"))
        assertEquals("B の一覧", root.tabs)
    }

    @Test
    fun resultArrivingAfterSignOutIsDiscarded() {
        val root = Root()
        val ticketA = root.signIn("a@example.com")
        root.signOut()

        assertFalse(root.deliver(ticketA, "A の一覧"))
        assertEquals(null, root.tabs)
    }

    @Test
    fun resignInToSameAccountDiscardsResultOfEarlierSession() {
        val root = Root()
        val first = root.signIn("a@example.com")
        root.signOut()
        val second = root.signIn("a@example.com")

        assertFalse(root.deliver(first, "1回目の一覧"))
        assertTrue(root.deliver(second, "2回目の一覧"))
        assertEquals("2回目の一覧", root.tabs)
    }

    @Test
    fun currentTicketIsRejectedWhenSignedInAccountDiffers() {
        // 札が最新でも、反映時点のサインイン中アカウントが札のアカウントと違えば捨てる
        val gate = AccountRequestGate()
        val ticketA = gate.begin("a@example.com")
        assertFalse(gate.isCurrent(ticketA, "b@example.com"))
        assertFalse(gate.isCurrent(ticketA, null))
        assertTrue(gate.isCurrent(ticketA, "a@example.com"))
    }

    @Test
    fun beginInvalidatesEarlierTicketsEvenWithoutSignOut() {
        val gate = AccountRequestGate()
        val old = gate.begin("a@example.com")
        val new = gate.begin("a@example.com")
        assertFalse(gate.isCurrent(old, "a@example.com"))
        assertTrue(gate.isCurrent(new, "a@example.com"))
    }
}
