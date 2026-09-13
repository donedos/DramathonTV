package com.dramathon.dramathonplus.decrypt

import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object VideoDecryptor {
    init {
        System.loadLibrary("native-lib")
    }

    private external fun getMasterKey(): String

    private val MASTER_KEY by lazy { getMasterKey().toByteArray() }

    fun deriveAESKey(aliyunB64: String): ByteArray {
        val encBlob = Base64.decode(aliyunB64, Base64.DEFAULT)
        
        val d1 = Cipher.getInstance("AES/CBC/PKCS5Padding")
        d1.init(Cipher.DECRYPT_MODE, SecretKeySpec(MASTER_KEY, "AES"), IvParameterSpec(MASTER_KEY))
        val jsonStr = String(d1.doFinal(encBlob))
        val json = JSONObject(jsonStr)
        
        val clientRand = json.getString("ClientRand")
        val serverRand = json.getString("ServerRand")
        val plainText = json.getString("PlainText")
        
        val md5cr = md5(clientRand.toByteArray())
        val keyA = bytesToHex(md5cr.sliceArray(4 until 12)).toByteArray()
        
        val srBytes = Base64.decode(serverRand, Base64.DEFAULT)
        val d2 = Cipher.getInstance("AES/CBC/PKCS5Padding")
        d2.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyA, "AES"), IvParameterSpec(keyA))
        val srDec = d2.doFinal(srBytes)
        
        val md5c = md5((clientRand + String(srDec)).toByteArray())
        val keyB = bytesToHex(md5c.sliceArray(4 until 12)).toByteArray()
        
        val ptBytes = Base64.decode(plainText, Base64.DEFAULT)
        val d3 = Cipher.getInstance("AES/CBC/PKCS5Padding")
        d3.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyB, "AES"), IvParameterSpec(keyA))
        val ptDec = d3.doFinal(ptBytes)
        
        return Base64.decode(String(ptDec), Base64.DEFAULT)
    }

    private fun md5(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("MD5").digest(data)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun decryptSamples(data: ByteArray, aesKey: ByteArray, tracks: Mp4Parser.Tracks): ByteArray {
        val output = data.clone()
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"))
        
        for (s in tracks.video) {
            if (s.size < 16) continue
            val nB = s.size / 16
            val encLen = nB * 16
            val dec = cipher.doFinal(data, s.offset.toInt(), encLen)
            System.arraycopy(dec, 0, output, s.offset.toInt(), encLen)
        }
        
        for (s in tracks.audio) {
            if (s.size < 16) continue
            val nB = s.size / 16
            val encLen = nB * 16
            val dec = cipher.doFinal(data, s.offset.toInt(), encLen)
            System.arraycopy(dec, 0, output, s.offset.toInt(), encLen)
        }
        
        return output
    }
}
