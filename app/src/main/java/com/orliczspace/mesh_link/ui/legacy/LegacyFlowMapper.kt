package com.orliczspace.mesh_link.ui.legacy

import com.orliczspace.mesh_link.network.gateway.NatEntry
import com.orliczspace.mesh_link.ui.model.UiFlow

fun NatEntry.toUiFlow(): UiFlow {

    return UiFlow(
        sourceNode = sourceNodeId ?: "Unknown",
        destinationIp = destIp,
        destinationPort = destPort
    )
}