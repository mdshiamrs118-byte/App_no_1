package com.openmedia.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** One shared player so playback can continue in the background. */
object PlayerHolder {
    var player: ExoPlayer? = null

    fun get(ctx: Context): ExoPlayer {
        val p = player
        if (p != null) return p
        val n = create(ctx.applicationContext)
        player = n
        return n
    }

    private fun create(app: Context): ExoPlayer {
        Prefs.init(app)
        val selector = MediaCodecSelector { mime, secure, tunneling ->
            val l = MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling)
            // Software decoders first when the user prefers them
            if (Prefs.softwareDecoder) l.sortedBy { it.hardwareAccelerated } else l
        }
        val rf = DefaultRenderersFactory(app)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(selector)
        return ExoPlayer.Builder(app, rf)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(), true
            )
            .build()
    }

    fun release() {
        player?.release()
        player = null
    }
}

class PlaybackService : MediaSessionService() {
    var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val open = Intent(this, PlayerActivity::class.java).putExtra("resume", true)
        val pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val s = MediaSession.Builder(this, PlayerHolder.get(this)).setSessionActivity(pi).build()
        session = s
        addSession(s)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        PlayerHolder.player?.pause()
        stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        instance = null
        if (!PlayerActivity.alive) PlayerHolder.release()
        super.onDestroy()
    }

    companion object {
        var instance: PlaybackService? = null
    }
}
