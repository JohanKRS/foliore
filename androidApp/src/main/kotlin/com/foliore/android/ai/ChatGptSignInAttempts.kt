package com.foliore.android.ai

/** Serializes cancellation, Android timeouts and the final credential commit for a single sign-in. */
internal class ChatGptSignInAttempts {
    private var nextId = 0L
    private var active: Long? = null

    @Synchronized
    fun start(action: (Long) -> Unit) {
        if (active != null) return
        val id = ++nextId
        active = id
        action(id)
    }

    @Synchronized
    fun whileActive(id: Long, action: () -> Unit): Boolean {
        if (active != id) return false
        action()
        return true
    }

    @Synchronized
    fun finish(id: Long?, action: () -> Unit) {
        val current = active ?: return
        if (id != null && id != current) return
        active = null
        action()
    }
}
