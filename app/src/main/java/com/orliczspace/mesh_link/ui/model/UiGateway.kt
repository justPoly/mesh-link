package com.orliczspace.mesh_link.ui.model

data class UiGateway(

    val nodeName: String,

    val internetAvailable: Boolean,

    val connectionType: String,

    val latency: Int,

    val score: Double
)