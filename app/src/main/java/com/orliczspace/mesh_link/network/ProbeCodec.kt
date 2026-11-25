package com.orliczspace.mesh_link.network

import android.util.Base64

object ProbeCodec {

    fun encode(message: ProbeMessage): ByteArray {

        val capabilities = message.capabilities

        val internetFlag =
            if (capabilities?.hasInternet == true) {
                "1"
            } else {
                "0"
            }

        val deviceName =
            capabilities?.deviceName ?: "Unknown device"

        val encodedDeviceName =
            Base64.encodeToString(
                deviceName.toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )

        val meshPort =
            capabilities?.meshPort ?: 9999

        val payload = listOf(
            message.type.name,
            message.senderId,
            message.sequence.toString(),
            message.timestamp.toString(),
            internetFlag,
            encodedDeviceName,
            meshPort.toString()
        ).joinToString("|")

        return payload.toByteArray(Charsets.UTF_8)
    }

    fun decode(
        data: ByteArray,
        length: Int
    ): ProbeMessage {

        require(length in 1..data.size) {
            "Invalid packet length"
        }

        val text =
            String(data, 0, length, Charsets.UTF_8)

        val parts =
            text.split("|")

        require(parts.size == 5 || parts.size == 7) {
            "Invalid probe packet"
        }

        val senderId = parts[1]

        require(senderId.isNotBlank()) {
            "Invalid sender ID"
        }

        require(parts[4] == "0" || parts[4] == "1") {
            "Invalid internet capability"
        }

        val deviceName =
            if (parts.size == 7) {

                String(
                    Base64.decode(
                        parts[5],
                        Base64.NO_WRAP
                    ),
                    Charsets.UTF_8
                ).ifBlank {
                    "Unknown device"
                }

            } else {
                "Unknown device"
            }

        val meshPort =
            if (parts.size == 7) {
                parts[6].toInt()
            } else {
                9999
            }

        require(meshPort in 1..65535) {
            "Invalid mesh port"
        }

        return ProbeMessage(
            type = ProbeMessage.Type.valueOf(parts[0]),
            senderId = senderId,
            sequence = parts[2].toLong(),
            timestamp = parts[3].toLong(),
            capabilities = Capabilities(
                hasInternet = parts[4] == "1",
                deviceName = deviceName,
                meshPort = meshPort
            )
        )
    }
}
