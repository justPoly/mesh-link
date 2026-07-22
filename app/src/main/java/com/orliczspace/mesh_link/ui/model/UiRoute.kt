package com.orliczspace.mesh_link.ui.model

data class UiRoute(

    val destination: String,

    val nextHop: String,

    val hops: Int,

    val latency: Int,

    val stability: Double,

    val active: Boolean
)