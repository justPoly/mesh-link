package com.orliczspace.mesh_link.network

/**
 * Encodes / decodes probe messages into a compact string format.
 *
 * Format:
 * TYPE|SENDER|SEQ|TIMESTAMP|INTERNET
 */
object ProbeCodec {

    fun encode(message: ProbeMessage): ByteArray {
        val internetFlag = if (message.capabilities?.hasInternet == true) "1" else "0"

        val payload = listOf(
            message.type.name,
            message.senderId,
            message.sequence.toString(),
            message.timestamp.toString(),
            internetFlag
        ).joinToString("|")

        return payload.toByteArray(Charsets.UTF_8)
    }

    fun decode(data: ByteArray, length: Int): ProbeMessage {
        val text = String(data, 0, length, Charsets.UTF_8)
        val parts = text.split("|")

        require(parts.size >= 5) { "Invalid probe packet" }

        return ProbeMessage(
            type = ProbeMessage.Type.valueOf(parts[0]),
            senderId = parts[1],
            sequence = parts[2].toLong(),
            timestamp = parts[3].toLong(),
            capabilities = Capabilities(
                hasInternet = parts[4] == "1"
            )
        )
    }
}
