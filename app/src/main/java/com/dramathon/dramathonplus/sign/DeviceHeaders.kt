package com.dramathon.dramathonplus.sign

import java.util.UUID

object DeviceHeaders {
    var deviceId: String = UUID.randomUUID().toString()
    var androidId: String = randomAndroidId()
    var tn: String = ""

    private fun randomAndroidId(): String {
        val bytes = ByteArray(8)
        java.security.SecureRandom().nextBytes(bytes)
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return "00000000${hex}00000000"
    }

    private fun randomLetters(length: Int = 5): String {
        val chars = "abcdefghijklmnopqrstuvwxyz"
        val random = java.util.Random()
        return (1..length).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    fun getBaseHeaders(): Map<String, String> {
        return mapOf(
            "User-Agent" to "okhttp/4.12.0",
            "version" to "611",
            "package-name" to "com.storymatrix.drama",
            "p" to "61",
            "cid" to "DLLPF1127624",
            "apn" to "1",
            "mchid" to "DLLPF1127624",
            "mbid" to "41000116646",
            "tz" to "-480",
            "language" to "en",
            "mcc" to "510",
            "locale" to "en_ID",
            "is_root" to if (SecurityUtils.isRooted()) "1" else "0",
            "device-id" to deviceId,
            "nchid" to "DRA1000042",
            "md" to "SM-A042F",
            "store-source" to "store_google",
            "mf" to "SAMSUNG",
            "device-score" to "60",
            "local-time" to "2026-06-03 21:52:50.514 +0800",
            "time-zone" to "+0800",
            "brand" to "samsung",
            "lat" to "0",
            "is_emulator" to if (SecurityUtils.isEmulator()) "1" else "0",
            "current-language" to "en",
            "ov" to "13",
            "userid" to "292895731",
            "android-id" to androidId,
            "srn" to "720x1600",
            "is_vpn" to "1",
            "build" to "Build/TP1A.220624.014",
            "pline" to "ANDROID",
            "vn" to "6.1.1",
            "over-flow" to "new-fly",
            "tn" to tn,
            "st" to "cK4n10B_0tTQBrxF${randomLetters()}",
            "content-type" to "application/json; charset=UTF-8"
        )
    }
}
