package com.dramathon.dramathonplus.decrypt

import java.nio.ByteBuffer
import java.nio.ByteOrder

object Mp4Parser {
    data class Box(val start: Int, val end: Int, val dataStart: Int)
    data class Sample(val offset: Long, val size: Int)
    data class Tracks(val video: List<Sample>, val audio: List<Sample>)

    fun findBox(buf: ByteBuffer, start: Int, end: Int, type: String): Box? {
        var p = start
        while (p + 8 <= end) {
            if (p + 8 > buf.limit()) break
            val sz = buf.getInt(p)
            val tp = getBoxType(buf, p + 4)
            if (sz < 8) break
            if (tp == type) return Box(p, Math.min(p + sz, end), p + 8)
            p += sz
        }
        return null
    }

    private fun getBoxType(buf: ByteBuffer, start: Int): String {
        val bytes = ByteArray(4)
        val oldPos = buf.position()
        buf.position(start)
        buf.get(bytes)
        buf.position(oldPos)
        return String(bytes, Charsets.US_ASCII)
    }

    fun extractAliyunUri(data: ByteArray): String? {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val keysMarker = "keys".toByteArray()
        var ki = indexOf(data, keysMarker)
        
        while (ki != -1) {
            if (ki >= 4) {
                val boxSz = buf.getInt(ki - 4)
                if (boxSz in 21..10000) {
                    val start = ki - 4
                    val keysSize = buf.getInt(start)
                    val cnt = buf.getInt(start + 12)
                    val names = mutableListOf<String>()
                    var kp = start + 16
                    for (i in 0 until cnt) {
                        if (kp + 8 > data.size) break
                        val ks = buf.getInt(kp)
                        val name = String(data, kp + 8, ks - 8, Charsets.UTF_8)
                        names.add(name)
                        kp += ks
                    }
                    
                    val ilstMarker = "ilst".toByteArray()
                    var ilstIdx = indexOf(data, ilstMarker, start)
                    if (ilstIdx != -1) {
                        val ilstStart = ilstIdx - 4
                        val ilstSize = buf.getInt(ilstStart)
                        var pos = ilstStart + 8
                        val ilstEnd = ilstStart + ilstSize
                        while (pos + 8 < ilstEnd && pos + 8 < data.size) {
                            val itemSz = buf.getInt(pos)
                            if (itemSz < 8) break
                            val idx = buf.getInt(pos + 4)
                            if (pos + 24 <= pos + itemSz) {
                                val dSz = buf.getInt(pos + 8)
                                val dTag = String(data, pos + 12, 4, Charsets.US_ASCII)
                                if (dTag == "data" && dSz > 16) {
                                    val valBytes = data.sliceArray(pos + 24 until pos + 8 + dSz)
                                    val kn = if (idx > 0 && idx <= names.size) names[idx - 1] else ""
                                    if (kn.contains("PrivateKeyUri")) {
                                        return String(valBytes, Charsets.UTF_8).trim()
                                    }
                                }
                            }
                            pos += itemSz
                        }
                    }
                    break
                }
            }
            ki = indexOf(data, keysMarker, ki + 5)
        }
        return null
    }

    private fun indexOf(data: ByteArray, target: ByteArray, start: Int = 0): Int {
        if (target.isEmpty()) return 0
        outer@ for (i in start..data.size - target.size) {
            for (j in target.indices) {
                if (data[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }

    fun parseTracks(data: ByteArray): Tracks {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val moov = findBox(buf, 0, data.size, "moov") ?: return Tracks(emptyList(), emptyList())
        
        val videoSamples = mutableListOf<Sample>()
        val audioSamples = mutableListOf<Sample>()
        
        var p = moov.dataStart
        while (p + 8 <= moov.end) {
            val sz = buf.getInt(p)
            val tp = getBoxType(buf, p + 4)
            val te = Math.min(p + sz, moov.end)
            if (tp == "trak") {
                val mdia = findBox(buf, p + 8, te, "mdia")
                if (mdia != null) {
                    val hdlr = findBox(buf, mdia.dataStart, mdia.end, "hdlr")
                    if (hdlr != null) {
                        val ht = getBoxType(buf, hdlr.dataStart + 8)
                        val minf = findBox(buf, mdia.dataStart, mdia.end, "minf")
                        if (minf != null) {
                            val stbl = findBox(buf, minf.dataStart, minf.end, "stbl")
                            if (stbl != null) {
                                val samples = getSamples(buf, stbl.dataStart, stbl.end)
                                if (ht == "vide") videoSamples.addAll(samples)
                                else if (ht == "soun") audioSamples.addAll(samples)
                            }
                        }
                    }
                }
            }
            p = te
        }
        return Tracks(videoSamples, audioSamples)
    }

    private fun getSamples(buf: ByteBuffer, start: Int, end: Int): List<Sample> {
        val sz = parseStsz(buf, start, end)
        val ch = parseStco(buf, start, end)
        val sc = parseStsc(buf, start, end)
        
        val result = mutableListOf<Sample>()
        var si = 0
        for (ci in ch.indices) {
            var spc = 1
            for (j in sc.indices.reversed()) {
                if (ci + 1 >= sc[j].fc) {
                    spc = sc[j].spc
                    break
                }
            }
            var off = ch[ci]
            for (k in 0 until spc) {
                if (si < sz.size) {
                    result.add(Sample(off, sz[si]))
                    off += sz[si]
                    si++
                }
            }
        }
        return result
    }

    private fun parseStsz(buf: ByteBuffer, start: Int, end: Int): List<Int> {
        val x = findBox(buf, start, end, "stsz") ?: return emptyList()
        val d = x.dataStart
        val def = buf.getInt(d + 4)
        val n = buf.getInt(d + 8)
        val r = mutableListOf<Int>()
        if (def == 0) {
            for (i in 0 until n) r.add(buf.getInt(d + 12 + i * 4))
        } else {
            for (i in 0 until n) r.add(def)
        }
        return r
    }

    private fun parseStco(buf: ByteBuffer, start: Int, end: Int): List<Long> {
        var x = findBox(buf, start, end, "stco")
        if (x != null) {
            val n = buf.getInt(x.dataStart + 4)
            val r = mutableListOf<Long>()
            for (i in 0 until n) r.add(buf.getInt(x.dataStart + 8 + i * 4).toLong() and 0xFFFFFFFFL)
            return r
        }
        x = findBox(buf, start, end, "co64")
        if (x != null) {
            val n = buf.getInt(x.dataStart + 4)
            val r = mutableListOf<Long>()
            for (i in 0 until n) r.add(buf.getLong(x.dataStart + 8 + i * 8))
            return r
        }
        return emptyList()
    }

    data class StscEntry(val fc: Int, val spc: Int)
    private fun parseStsc(buf: ByteBuffer, start: Int, end: Int): List<StscEntry> {
        val x = findBox(buf, start, end, "stsc") ?: return emptyList()
        val n = buf.getInt(x.dataStart + 4)
        val r = mutableListOf<StscEntry>()
        for (i in 0 until n) {
            r.add(StscEntry(buf.getInt(x.dataStart + 8 + i * 12), buf.getInt(x.dataStart + 12 + i * 12)))
        }
        return r
    }
}
