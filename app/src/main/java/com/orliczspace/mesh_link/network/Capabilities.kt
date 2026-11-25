package com.orliczspace.mesh_link.network

data class Capabilities(
    val hasInternet: Boolean,
    val deviceName: String = "Unknown device",
    val meshPort: Int = 9999
)
