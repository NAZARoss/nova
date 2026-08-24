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

    fun buildCommand(type: String): String {
        return "$CMD_PREFIX$type"
    }

    fun buildBrowserCommand(queryOrUrl: String): String {
        return "$CMD_PREFIX$TYPE_OPEN_BROWSER_PREFIX$queryOrUrl"
    }

    fun isPrankCommand(text: String): Boolean {
        return text.startsWith(CMD_PREFIX)
    }

    fun extractType(text: String): String? {
        if (!isPrankCommand(text)) return null
        return text.substringAfter(CMD_PREFIX).trim()
    }
}

