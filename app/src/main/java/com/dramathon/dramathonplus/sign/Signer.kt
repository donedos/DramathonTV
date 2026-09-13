package com.dramathon.dramathonplus.sign

import android.util.Base64
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

object Signer {
    init {
        System.loadLibrary("native-lib")
    }

    private external fun getPart1(): String
    private external fun getPart2(): String
    private external fun getPart3(): String
    private external fun getPart4(): String

    private val privateKey: PrivateKey by lazy {
        val fullPem = getPart1() + getPart2() + getPart3() + getPart4()
        val keyBytes = Base64.decode(fullPem, Base64.DEFAULT)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance("RSA")
        kf.generatePrivate(spec)
    }

    fun sign(data: String): String {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }
}
