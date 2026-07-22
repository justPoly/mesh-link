package com.orliczspace.mesh_link.ui.model

data class UiNode(

    val id: String,

    val name: String,

    val ipAddress: String,

    val signalStrength: Int,

    val connected: Boolean,

    val gateway: Boolean,

    val latency: Int
)