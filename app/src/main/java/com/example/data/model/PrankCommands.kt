package com.example.data.model

object PrankCommands {
    const val CMD_PREFIX = ":::PRANK:::"
    const val TYPE_BLINK = "BLINK"
    const val TYPE_FLASHLIGHT_ON = "FLASHLIGHT_ON"
    const val TYPE_FLASHLIGHT_OFF = "FLASHLIGHT_OFF"
    const val TYPE_BLOOD_RED_ON = "BLOOD_RED_ON"
    const val TYPE_BLOOD_RED_OFF = "BLOOD_RED_OFF"
    const val TYPE_OPEN_CAMERA_FRONT = "OPEN_CAMERA_FRONT"
    const val TYPE_OPEN_BROWSER_PREFIX = "OPEN_BROWSER:::"
    const val TYPE_SCREAMER_PREFIX = "SCREAMER:::"

    data class ScreamerPayload(
        val volumePercent: Int = 100,
        val mediaUrl: String
    )

    fun buildCommand(type: String): String {
        return "$CMD_PREFIX$type"
    }

    fun buildBrowserCommand(queryOrUrl: String): String {
        return "$CMD_PREFIX$TYPE_OPEN_BROWSER_PREFIX$queryOrUrl"
    }

    fun buildScreamerCommand(mediaUrl: String, volumePercent: Int = 100): String {
        val clamped = volumePercent.coerceIn(0, 100)
        return "$CMD_PREFIX$TYPE_SCREAMER_PREFIX$clamped:::$mediaUrl"
    }

    fun parseScreamerPayload(prankBody: String): ScreamerPayload? {
        if (!prankBody.startsWith(TYPE_SCREAMER_PREFIX)) return null
        val payload = prankBody.removePrefix(TYPE_SCREAMER_PREFIX)
        val parts = payload.split(":::", limit = 2)
        return if (parts.size == 2 && parts[0].toIntOrNull() != null) {
            ScreamerPayload(
                volumePercent = parts[0].toInt().coerceIn(0, 100),
                mediaUrl = parts[1].trim()
            )
        } else {
            ScreamerPayload(
                volumePercent = 100,
                mediaUrl = payload.trim()
            )
        }
    }

    fun isPrankCommand(text: String): Boolean {
        return text.startsWith(CMD_PREFIX)
    }

    fun extractType(text: String): String? {
        if (!isPrankCommand(text)) return null
        return text.substringAfter(CMD_PREFIX).trim()
    }
}

