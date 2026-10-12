package com.orliczspace.mesh_link.network

import androidx.compose.runtime.mutableStateListOf

data class NetworkActivity(
    val id: Long,
    val message: String,
    val detail: String?,
    val timestamp: Long
)

object NetworkActivityLog {

    private const val MAX_EVENTS = 50
    private const val DUPLICATE_COOLDOWN_MS = 30_000L

    private val _events = mutableStateListOf<NetworkActivity>()

    val events: List<NetworkActivity>
        get() = _events

    @Synchronized
    fun record(
        message: String,
        detail: String? = null
    ) {
        val now = System.currentTimeMillis()

        // Ignore the same event if it was recorded recently.
        val duplicateExists = _events.any { activity ->
            activity.message == message &&
                    activity.detail == detail &&
                    now - activity.timestamp in 0..DUPLICATE_COOLDOWN_MS
        }

        if (duplicateExists) {
            return
        }

        _events.add(
            0,
            NetworkActivity(
                id = System.nanoTime(),
                message = message,
                detail = detail,
                timestamp = now
            )
        )

        // Keep the activity history bounded.
        while (_events.size > MAX_EVENTS) {
            _events.removeAt(_events.lastIndex)
        }
    }

    @Synchronized
    fun clear() {
        _events.clear()
    }
}
