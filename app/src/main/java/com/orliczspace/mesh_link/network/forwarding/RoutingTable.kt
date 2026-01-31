package com.orliczspace.mesh_link.network.forwarding

import com.orliczspace.mesh_link.network.packet.MeshPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Simplified routing table for the mesh network.
 * Handles next-hop lookups, node discovery, and RTT tracking.
 */
class RoutingTable(val localNodeId: String) {

    /** NodeId -> Next-hop NodeId */
    private val nextHopMap = ConcurrentHashMap<String, String>()

    /** NodeId -> SocketAddress */
    private val socketMap = ConcurrentHashMap<String, InetSocketAddress>()

    /* ------------------------
     * ROUTING LOOKUPS
     * ------------------------ */

    /**
     * Return the next-hop nodeId for a given destination.
     */
    fun getNextHop(destinationNodeId: String): String? {
        return nextHopMap[destinationNodeId]
    }

    /**
     * Return the socket address for a nodeId.
     */
    fun getSocketAddress(nodeId: String): InetSocketAddress? {
        return socketMap[nodeId]
    }

    /**
     * Update / add a node socket address
     */
    fun updateNodeSocket(nodeId: String, address: InetSocketAddress) {
        socketMap[nodeId] = address
    }

    /* ------------------------
     * PACKET HANDLERS
     * ------------------------ */

    /**
     * Handle HELLO / discovery packets.
     */
    fun onHello(packet: MeshPacket) {
        // TODO: update node presence
        println("HELLO received from ${packet.sourceNodeId}")
    }

    /**
     * Handle RTT probe packets.
     */
    fun onRttProbe(packet: MeshPacket, sender: InetAddress) {
        // TODO: respond with RTT_RESPONSE
        println("RTT_PROBE received from ${packet.sourceNodeId}")
    }

    /**
     * Handle RTT response packets.
     */
    fun onRttResponse(packet: MeshPacket) {
        // TODO: update RTT statistics
        println("RTT_RESPONSE received from ${packet.sourceNodeId}")
    }

    /**
     * Handle gateway announcement packets.
     */
    fun onGatewayAnnounce(packet: MeshPacket) {
        // TODO: mark node as gateway
        println("GATEWAY_ANNOUNCE received from ${packet.sourceNodeId}")
    }

    /**
     * Add / update next-hop mapping
     */
    fun updateNextHop(destinationNodeId: String, nextHopNodeId: String) {
        nextHopMap[destinationNodeId] = nextHopNodeId
    }

}
