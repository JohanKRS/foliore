package com.foliore.android.billing

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class UnlimitedReminderState(
    val presented: Boolean = false,
    val remindAtMillis: Long? = null,
) {
    fun delayUntilDue(nowMillis: Long): Long? = when {
        remindAtMillis != null -> (remindAtMillis - nowMillis).coerceAtLeast(0)
        !presented -> 0L
        else -> null
    }
}

/** Presentation preference only; it never grants or changes a purchase entitlement. */
internal class UnlimitedReminder(
    private val store: UnlimitedReminderStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this(PreferencesReminderStore(context.applicationContext))

    private val mutableState = MutableStateFlow(store.read())
    val state = mutableState.asStateFlow()

    // Consume the reminder when presented, so process interruption cannot repeat it.
    fun markPresented() = save(UnlimitedReminderState(presented = true))
    fun dismiss() = markPresented()
    fun remindInOneWeek() = save(UnlimitedReminderState(true, nowMillis() + ONE_WEEK_MILLIS))

    private fun save(state: UnlimitedReminderState) {
        store.write(state)
        mutableState.value = state
    }

    companion object {
        const val ONE_WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000
        fun eligible(bookCount: Int, billing: BillingState): Boolean =
            bookCount >= LibraryLimit.FREE_BOOKS - 5 && !billing.unlimited && !billing.pending && !billing.busy

        fun highlightInSettings(bookCount: Int, unlimited: Boolean): Boolean =
            bookCount >= LibraryLimit.FREE_BOOKS && !unlimited
    }
}

internal interface UnlimitedReminderStore {
    fun read(): UnlimitedReminderState
    fun write(state: UnlimitedReminderState)
}

private class PreferencesReminderStore(context: Context) : UnlimitedReminderStore {
    private val preferences = context.getSharedPreferences("foliore_unlimited_reminder", Context.MODE_PRIVATE)
    override fun read() = UnlimitedReminderState(
        presented = preferences.getBoolean("presented", false),
        remindAtMillis = if (preferences.contains("remind_at")) preferences.getLong("remind_at", 0) else null,
    )

    override fun write(state: UnlimitedReminderState) {
        preferences.edit().putBoolean("presented", state.presented).apply {
            if (state.remindAtMillis == null) remove("remind_at")
            else putLong("remind_at", state.remindAtMillis)
        }.apply()
    }
}
