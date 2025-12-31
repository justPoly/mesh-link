package com.orliczspace.mesh_link.network

data class ProbeMessage(
    val type: Type,
    val senderId: String,
    val sequence: Long,
    val timestamp: Long,
    val capabilities: Capabilities?
) {
    enum class Type {
        REQUEST,
        RESPONSE
    }
}
