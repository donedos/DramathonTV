package com.dramathon.dramathonplus

import android.graphics.Color
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import org.json.JSONArray

/**
 * Shared subtitle language options used by the Settings screen and the
 * in-player subtitle menu. Display labels are what gets persisted in
 * `subtitle_lang`; [languageCode] maps them to ISO-639 codes for Media3
 * track selection. `null` code means subtitles are disabled.
 */
object SubtitleLanguages {

    const val OFF_LABEL = "Off / Disabled"

    val options: List<String> = listOf(
        "English",
        "Spanish",
        "Indonesian",
        "Thai",
        OFF_LABEL
    )

    fun languageCode(label: String): String? = when (label) {
        "English" -> "en"
        "Spanish" -> "es"
        "Indonesian" -> "id"
        "Thai" -> "th"
        OFF_LABEL -> null
        else -> null
    }

    fun isDisabled(label: String): Boolean = label == OFF_LABEL

    fun buildSubtitleConfigurations(subLanguageVoList: JSONArray?): List<MediaItem.SubtitleConfiguration> {
        if (subLanguageVoList == null || subLanguageVoList.length() == 0) return emptyList()
        val list = mutableListOf<MediaItem.SubtitleConfiguration>()
        for (i in 0 until subLanguageVoList.length()) {
            val item = subLanguageVoList.optJSONObject(i) ?: continue
            val lang = item.optString("captionLanguage", "").lowercase()
            val rawUrl = item.optString("url", "").trim()
            if (lang.isEmpty() || lang == "none" || rawUrl.isEmpty()) continue

            // Normalize URL slashes (e.g. "https://domain.com//path/to/sub.srt")
            val cleanUrl = when {
                rawUrl.startsWith("https://", ignoreCase = true) -> {
                    "https://" + rawUrl.substring(8).replace(Regex("/{2,}"), "/")
                }
                rawUrl.startsWith("http://", ignoreCase = true) -> {
                    "http://" + rawUrl.substring(7).replace(Regex("/{2,}"), "/")
                }
                else -> rawUrl
            }

            // Normalize "in" (legacy Indonesian ISO code used by DramaBox) to "id"
            val normalizedLang = if (lang == "in") "id" else lang

            val isDefault = item.optInt("isDefault", 0) == 1

            val mimeType = if (cleanUrl.contains(".vtt", ignoreCase = true)) {
                MimeTypes.TEXT_VTT
            } else {
                MimeTypes.APPLICATION_SUBRIP
            }

            val config = MediaItem.SubtitleConfiguration.Builder(cleanUrl.toUri())
                .setMimeType(mimeType)
                .setLanguage(normalizedLang)
                .setSelectionFlags(if (isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                .build()

            list.add(config)
        }
        return list
    }

    @OptIn(UnstableApi::class)
    fun applySubtitleStyle(playerView: PlayerView) {
        val backgroundColor = Color.argb((255 * 0.85f).toInt(), 0, 0, 0)
        val captionStyle = CaptionStyleCompat(
            Color.WHITE,
            backgroundColor,
            Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
            Color.BLACK,
            null
        )
        playerView.subtitleView?.setStyle(captionStyle)
    }
}
