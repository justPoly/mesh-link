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

    private val _events = mutableStateListOf<NetworkActivity>()

    val events: List<NetworkActivity>
        get() = _events

    fun record(
        message: String,
        detail: String? = null
    ) {
        _events.add(
            0,
            NetworkActivity(
                id = System.nanoTime(),
                message = message,
                detail = detail,
                timestamp = System.currentTimeMillis()
            )
        )

        if (_events.size > MAX_EVENTS) {
            _events.removeAt(_events.lastIndex)
        }
    }

    fun clear() {
        _events.clear()
    }
}