package com.orliczspace.mesh_link.network.packet

enum class PacketType {
    HELLO,
    RTT_PROBE,
    RTT_RESPONSE,
    GATEWAY_ANNOUNCE,
    NAT_FORWARD,
    DATA,
    ACK,
    KEY_EXCHANGE
}
