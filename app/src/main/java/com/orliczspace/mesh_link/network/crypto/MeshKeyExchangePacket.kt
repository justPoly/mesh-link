package com.orliczspace.mesh_link.network.crypto

import com.orliczspace.mesh_link.network.packet.PacketType
import com.orliczspace.mesh_link.network.packet.MeshPacket

data class MeshKeyExchangePacket(
    val sourceNodeId: String,
    val destinationNodeId: String,
    val publicKey: ByteArray,
    val isResponse: Boolean
) {
    fun toMeshPacket(): MeshPacket {
        return MeshPacket(
            sourceNodeId = sourceNodeId,
            destinationNodeId = destinationNodeId,
            payload = publicKey,
            type = PacketType.KEY_EXCHANGE
        )
    }

    companion object {
        fun fromMeshPacket(packet: MeshPacket, isResponse: Boolean): MeshKeyExchangePacket {
            return MeshKeyExchangePacket(
                sourceNodeId = packet.sourceNodeId,
                destinationNodeId = packet.destinationNodeId ?: "",
                publicKey = packet.payload,
                isResponse = isResponse
            )
        }
    }
}
