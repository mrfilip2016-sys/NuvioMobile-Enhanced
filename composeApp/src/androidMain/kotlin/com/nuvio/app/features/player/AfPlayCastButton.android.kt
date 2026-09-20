package com.nuvio.app.features.player

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.CastButtonFactory

class AfPlayCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

@Composable
internal actual fun AfPlayCastButton(
    mediaUrl: String,
    title: String,
    subtitle: String?,
    positionMs: Long,
    subtitleUrl: String?,
    subtitleLanguage: String?,
    enabled: Boolean,
    buttonSize: Dp,
) {
    val context = LocalContext.current
    val castContext = remember(context) {
        runCatching { CastContext.getSharedInstance(context) }.getOrNull()
    }

    if (!enabled || !mediaUrl.isAfPlayCastableUrl() || castContext == null) return

    var lastLoadedKey by remember(mediaUrl) { mutableStateOf<String?>(null) }

    fun loadIntoSession(session: CastSession?) {
        if (session == null) return
        val remote = session.remoteMediaClient ?: return
        val sessionKey = "${session.sessionId}|$mediaUrl"
        if (lastLoadedKey == sessionKey) return

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                putString(MediaMetadata.KEY_SUBTITLE, it)
            }
        }

        val mediaBuilder = MediaInfo.Builder(mediaUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(mediaUrl.afPlayCastContentType())
            .setMetadata(metadata)

        val castSubtitleUrl = subtitleUrl
            ?.trim()
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

        val subtitleTrackId = if (castSubtitleUrl != null) {
            val track = MediaTrack.Builder(1L, MediaTrack.TYPE_TEXT)
                .setName(
                    when (subtitleLanguage?.lowercase()) {
                        "ro", "ron", "rum" -> "Română"
                        else -> subtitleLanguage?.takeIf { it.isNotBlank() } ?: "Subtitles"
                    },
                )
                .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                .setContentId(castSubtitleUrl)
                .setContentType(castSubtitleUrl.afPlaySubtitleContentType())
                .setLanguage(subtitleLanguage?.takeIf { it.isNotBlank() } ?: "ro")
                .build()
            mediaBuilder.setMediaTracks(listOf(track))
            1L
        } else {
            null
        }

        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaBuilder.build())
            .setAutoplay(true)
            .setCurrentTime(positionMs.coerceAtLeast(0L))
            .build()

        remote.load(request).setResultCallback { result ->
            if (result.status.isSuccess && subtitleTrackId != null) {
                remote.setActiveMediaTracks(longArrayOf(subtitleTrackId))
            }
        }
        lastLoadedKey = sessionKey
    }

    DisposableEffect(castContext, mediaUrl, title, subtitle, positionMs, subtitleUrl, subtitleLanguage) {
        val sessionManager = castContext.sessionManager
        val listener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarting(session: CastSession) = Unit

            override fun onSessionStarted(session: CastSession, sessionId: String) {
                loadIntoSession(session)
            }

            override fun onSessionStartFailed(session: CastSession, error: Int) = Unit

            override fun onSessionSuspended(session: CastSession, reason: Int) = Unit

            override fun onSessionResuming(session: CastSession, sessionId: String) = Unit

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                loadIntoSession(session)
            }

            override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit

            override fun onSessionEnding(session: CastSession) = Unit

            override fun onSessionEnded(session: CastSession, error: Int) {
                lastLoadedKey = null
            }
        }

        sessionManager.addSessionManagerListener(listener, CastSession::class.java)
        loadIntoSession(sessionManager.currentCastSession)

        onDispose {
            sessionManager.removeSessionManagerListener(listener, CastSession::class.java)
        }
    }

    AndroidView(
        factory = { viewContext ->
            MediaRouteButton(viewContext).apply {
                CastButtonFactory.setUpMediaRouteButton(viewContext, this)
                contentDescription = "Transmite pe TV"
            }
        },
        update = { button ->
            button.isEnabled = enabled
        },
        modifier = Modifier.size(buttonSize),
    )
}

private fun String.isAfPlayCastableUrl(): Boolean {
    val value = trim().lowercase()
    return value.startsWith("http://") || value.startsWith("https://")
}

private fun String.afPlayCastContentType(): String {
    val value = substringBefore('?').lowercase()
    return when {
        value.endsWith(".m3u8") -> "application/x-mpegURL"
        value.endsWith(".mpd") -> "application/dash+xml"
        value.endsWith(".mp4") || value.endsWith(".m4v") -> "video/mp4"
        value.endsWith(".mkv") -> "video/x-matroska"
        value.endsWith(".webm") -> "video/webm"
        else -> "video/mp4"
    }
}

private fun String.afPlaySubtitleContentType(): String {
    val value = substringBefore('?').lowercase()
    return when {
        value.endsWith(".vtt") -> "text/vtt"
        value.endsWith(".ttml") || value.endsWith(".xml") -> "application/ttml+xml"
        else -> "text/vtt"
    }
}
