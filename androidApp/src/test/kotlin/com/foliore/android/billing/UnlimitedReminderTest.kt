package com.foliore.android.billing

import kotlin.test.*

class UnlimitedReminderTest {
    @Test fun offerStartsAtFortyFiveAndWaitsForPurchaseChecks() {
        assertFalse(UnlimitedReminder.eligible(44, BillingState()))
        assertTrue(UnlimitedReminder.eligible(45, BillingState()))
        assertTrue(UnlimitedReminder.eligible(55, BillingState(message = BillingMessage.UNAVAILABLE)))
        assertFalse(UnlimitedReminder.eligible(45, BillingState(unlimited = true)))
        assertFalse(UnlimitedReminder.eligible(50, BillingState(busy = true)))
        assertFalse(UnlimitedReminder.eligible(50, BillingState(pending = true)))
    }

    @Test fun presentationIsConsumedEvenIfTheProcessStopsBeforeAnAnswer() {
        val store = MemoryStore()
        val reminder = UnlimitedReminder(store)
        assertEquals(0L, reminder.state.value.delayUntilDue(100))
        reminder.markPresented()
        assertNull(UnlimitedReminder(store).state.value.delayUntilDue(Long.MAX_VALUE))
    }

    @Test fun snoozeSurvivesRestartAndExpiresAtOneWeekWithoutAutomaticRepetition() {
        val store = MemoryStore()
        val now = 1_800_000_000_000L
        val reminder = UnlimitedReminder(store) { now }
        reminder.markPresented()
        reminder.remindInOneWeek()
        val restarted = UnlimitedReminder(store)
        val due = now + UnlimitedReminder.ONE_WEEK_MILLIS
        assertEquals(UnlimitedReminder.ONE_WEEK_MILLIS, restarted.state.value.delayUntilDue(now))
        assertEquals(1L, restarted.state.value.delayUntilDue(due - 1))
        assertEquals(0L, restarted.state.value.delayUntilDue(due))
        assertEquals(0L, restarted.state.value.delayUntilDue(due + 1))
        restarted.markPresented()
        assertNull(UnlimitedReminder(store).state.value.delayUntilDue(due + UnlimitedReminder.ONE_WEEK_MILLIS))
    }

    @Test fun dismissCancelsAnExistingReminderPermanently() {
        val store = MemoryStore()
        val reminder = UnlimitedReminder(store) { 100L }
        reminder.remindInOneWeek()
        reminder.dismiss()
        assertNull(UnlimitedReminder(store).state.value.delayUntilDue(Long.MAX_VALUE))
    }

    @Test fun settingsPromotionStartsAtFiftyAndGoesAwayAfterUnlockOrDeletion() {
        assertFalse(UnlimitedReminder.highlightInSettings(49, false))
        assertTrue(UnlimitedReminder.highlightInSettings(50, false))
        assertTrue(UnlimitedReminder.highlightInSettings(70, false))
        assertFalse(UnlimitedReminder.highlightInSettings(70, true))
    }

    private class MemoryStore : UnlimitedReminderStore {
        private var value = UnlimitedReminderState()
        override fun read() = value
        override fun write(state: UnlimitedReminderState) { value = state }
    }
}
