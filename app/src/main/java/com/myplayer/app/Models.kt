package com.myplayer.app

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.util.Locale

data class MediaEntry(
    val key: String,
    val uri: Uri,
    val title: String,
    val subtitle: String,
    val folder: String,
    val durationMs: Long,
    val isVideo: Boolean,
) {
    fun toMediaItem(): MediaItem {
        val extras = Bundle()
        extras.putBoolean(EXTRA_VIDEO, isVideo)
        val meta = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(subtitle)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(key)
            .setUri(uri)
            .setMediaMetadata(meta)
            .build()
    }

    companion object {
        const val EXTRA_VIDEO = "is_video"
    }
}

fun formatTime(ms: Long): String {
    val total = maxOf(ms, 0L) / 1000L
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}

fun speedLabel(speed: Float): String {
    return String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.') + "x"
}
