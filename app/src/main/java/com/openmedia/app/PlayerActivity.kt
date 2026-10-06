package com.openmedia.app

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.InputType
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.util.Locale

class PlayerActivity : Activity() {

    companion object {
        @Volatile var alive = false
        private const val REQ_SUB = 91
    }

    private lateinit var root: FrameLayout
    private lateinit var pv: PlayerView
    private lateinit var controls: View
    private lateinit var hud: TextView
    private lateinit var nightView: View
    private lateinit var audioArt: View
    private lateinit var seek: SeekBar
    private lateinit var tCur: TextView
    private lateinit var tDur: TextView
    private lateinit var tTitle: TextView
    private lateinit var btnPlay: ImageView
    private lateinit var btnMute: ImageView
    private lateinit var btnBg: ImageView
    private lateinit var btnUnlock: ImageView
    private lateinit var player: ExoPlayer
    private lateinit var audio: AudioManager

    private val h = Handler(Looper.getMainLooper())
    private var locked = false
    private var dragging = false
    private var muted = false
    private var night = false
    private var mirror = false
    private var pipMode = false
    private var manualOrient = false
    private var errAsked = false
    private var abA = -1L
    private var abB = -1L
    private var resizeIdx = 0
    private var timerRun: Runnable? = null
    private var eq: Equalizer? = null
    private var boostFx: LoudnessEnhancer? = null

    // gesture state
    private var mode = 0
    private var scrubPos = 0L
    private var volF = 0f
    private var briF = 0.5f

    /* ---------------- lifecycle ---------------- */

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Prefs.init(this)
        alive = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        setContentView(R.layout.activity_player)
        audio = getSystemService(AUDIO_SERVICE) as AudioManager

        root = findViewById(R.id.root)
        pv = findViewById(R.id.pv)
        controls = findViewById(R.id.controls)
        hud = findViewById(R.id.hud)
        nightView = findViewById(R.id.nightOverlay)
        audioArt = findViewById(R.id.audioArt)
        seek = findViewById(R.id.seek)
        tCur = findViewById(R.id.tCur)
        tDur = findViewById(R.id.tDur)
        tTitle = findViewById(R.id.tTitle)
        btnPlay = findViewById(R.id.btnPlay)
        btnMute = findViewById(R.id.btnMute)
        btnBg = findViewById(R.id.btnBg)
        btnUnlock = findViewById(R.id.btnUnlock)

        val resume = intent.getBooleanExtra("resume", false)
        val data = intent.data
        if (!resume && Intent.ACTION_VIEW == intent.action && data != null) {
            Queue.items = listOf(Repo.fromUri(this, data))
            Queue.index = 0
        }
        if (!resume && Queue.items.isEmpty()) { finish(); return }

        player = PlayerHolder.get(this)
        if (resume && player.mediaItemCount == 0 && Queue.items.isEmpty()) { finish(); return }

        wire()
        attach()
        if (!(resume && player.mediaItemCount > 0)) load()
        startService(Intent(this, PlaybackService::class.java))
        hideSystem()
        updateTitle()
        updatePlayIcon()
        updateBgIcon()
        showControls()
    }

    override fun onStart() {
        super.onStart()
        if (::player.isInitialized) pv.player = player
    }

    override fun onPause() {
        super.onPause()
        if (::player.isInitialized) saveProgress()
    }

    override fun onStop() {
        super.onStop()
        if (!::player.isInitialized) return
        saveProgress()
        if (!(Prefs.bgPlay && player.isPlaying)) player.pause()
        pv.player = null
    }

    override fun onDestroy() {
        super.onDestroy()
        alive = false
        if (!::player.isInitialized) return
        h.removeCallbacksAndMessages(null)
        player.removeListener(listener)
        if (!(Prefs.bgPlay && player.isPlaying)) {
            eq?.release(); eq = null
            boostFx?.release(); boostFx = null
            PlayerHolder.release()
            stopService(Intent(this, PlaybackService::class.java))
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystem()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (locked) { showControls(); return }
        super.onBackPressed()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipMode = isInPictureInPictureMode
        if (pipMode) { controls.visibility = View.GONE; btnUnlock.visibility = View.GONE }
        else showControls()
    }

    @Suppress("DEPRECATION")
    private fun hideSystem() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    /* ---------------- player wiring ---------------- */

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayIcon()
            if (isPlaying) { schedHide(); tick() }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) showControls()
            updatePlayIcon()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            abA = -1; abB = -1; errAsked = false
            updateTitle()
            Queue.items.getOrNull(player.currentMediaItemIndex)?.let { Prefs.lastUri = it.uri.toString() }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) { autoOrient(videoSize) }

        override fun onTracksChanged(tracks: Tracks) {
            val hasVideo = tracks.isTypeSelected(C.TRACK_TYPE_VIDEO)
            audioArt.visibility = if (tracks.groups.isNotEmpty() && !hasVideo) View.VISIBLE else View.GONE
            if (tracks.groups.isNotEmpty() && !hasVideo && !manualOrient) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) { setupFx(audioSessionId) }

        override fun onPlayerError(error: PlaybackException) { handleError(error) }
    }

    private fun attach() {
        pv.player = player
        player.addListener(listener)
        setupFx(player.audioSessionId)
        applyVolume()
    }

    private fun detach() {
        player.removeListener(listener)
        pv.player = null
    }

    private fun toItem(m: Media): MediaItem =
        MediaItem.Builder()
            .setUri(m.uri)
            .setMediaId(m.uri.toString())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(m.name).build())
            .build()

    private fun load() {
        val items = Queue.items.map { toItem(it) }
        if (items.isEmpty()) { finish(); return }
        val idx = Queue.index.coerceIn(0, items.size - 1)
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = false
        player.setMediaItems(items, idx, C.TIME_UNSET)
        player.setPlaybackSpeed(Prefs.speed)
        val m = Queue.items[idx]
        val p = if (Prefs.resume) Prefs.getPos(m.uri) else 0L
        if (p > 5000) player.seekTo(idx, p)
        Prefs.lastUri = m.uri.toString()
        player.prepare()
        player.playWhenReady = true
    }

    private fun cur(): Media? = Queue.items.getOrNull(player.currentMediaItemIndex)

    private fun updateTitle() { tTitle.text = cur()?.name ?: "" }

    private fun updatePlayIcon() {
        btnPlay.setImageResource(if (player.isPlaying || player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun updateBgIcon() { btnBg.setColorFilter(if (Prefs.bgPlay) Pop.ACCENT else Color.WHITE) }

    private fun dur(): Long {
        val d = player.duration
        return if (d == C.TIME_UNSET || d < 0) 0L else d
    }

    private fun applyVolume() {
        player.volume = if (muted) 0f else if (night) 0.6f else 1f
    }

    private fun autoOrient(vs: VideoSize) {
        if (!Prefs.autoRotate || manualOrient || vs.width == 0 || vs.height == 0) return
        requestedOrientation = if (vs.width >= vs.height) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

    private fun saveProgress() {
        val m = cur() ?: return
        val p = player.currentPosition
        val d = dur()
        if (d > 0 && p > d - 5000) Prefs.clearPos(m.uri) else if (p > 3000) Prefs.setPos(m.uri, p)
    }

    /* ---------------- controls ---------------- */

    private val hideRun = Runnable {
        controls.visibility = View.GONE
        btnUnlock.visibility = View.GONE
    }
    private val hudHide = Runnable { hud.visibility = View.GONE }
    private val tickRun = object : Runnable {
        override fun run() {
            tickNow()
            if (player.isPlaying) h.postDelayed(this, 250)
        }
    }

    private fun tick() { h.removeCallbacks(tickRun); h.post(tickRun) }

    private fun tickNow() {
        val pos = player.currentPosition
        if (abA >= 0 && abB > abA && pos >= abB) player.seekTo(abA)
        if (controls.visibility == View.VISIBLE && !dragging) {
            val d = dur()
            seek.max = d.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            seek.progress = pos.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            tCur.text = U.time(pos)
            tDur.text = U.time(d)
        }
    }

    private fun showControls() {
        if (pipMode) return
        h.removeCallbacks(hideRun)
        if (locked) {
            btnUnlock.visibility = View.VISIBLE
            h.postDelayed(hideRun, 2500)
            return
        }
        controls.visibility = View.VISIBLE
        tickNow()
        schedHide()
    }

    private fun schedHide() {
        h.removeCallbacks(hideRun)
        if (player.isPlaying && !locked) h.postDelayed(hideRun, 3500)
    }

    private fun toggleControls() {
        if (controls.visibility == View.VISIBLE || btnUnlock.visibility == View.VISIBLE) {
            h.removeCallbacks(hideRun)
            hideRun.run()
        } else showControls()
    }

    private fun hudShow(s: String) {
        hud.text = s
        hud.visibility = View.VISIBLE
        h.removeCallbacks(hudHide)
        h.postDelayed(hudHide, 700)
    }

    private fun seekBy(ms: Long) {
        var t = player.currentPosition + ms
        if (t < 0) t = 0
        val d = dur()
        if (d > 0 && t > d) t = d
        player.seekTo(t)
        val s = ms / 1000
        hudShow(if (s > 0) "+${s}s" else "${s}s")
        tickNow()
    }

    private fun togglePlay() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.isPlaying) player.pause() else player.play()
        updatePlayIcon()
    }

    private fun lock(on: Boolean) {
        locked = on
        h.removeCallbacks(hideRun)
        controls.visibility = View.GONE
        if (on) {
            btnUnlock.visibility = View.VISIBLE
            h.postDelayed(hideRun, 2500)
        } else {
            btnUnlock.visibility = View.GONE
            showControls()
        }
    }

    private fun wire() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnList).setOnClickListener { showQueue() }
        findViewById<View>(R.id.btnRotate).setOnClickListener {
            manualOrient = true
            val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            requestedOrientation = if (land) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        btnMute.setOnClickListener {
            muted = !muted
            btnMute.setImageResource(if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_up)
            applyVolume()
        }
        findViewById<View>(R.id.btnShot).setOnClickListener { shot() }
        findViewById<View>(R.id.btnAudio).setOnClickListener { trackDialog(C.TRACK_TYPE_AUDIO, "Audio track", false) }
        findViewById<View>(R.id.btnSub).setOnClickListener { trackDialog(C.TRACK_TYPE_TEXT, "Subtitles", true) }
        btnBg.setOnClickListener { toggleBg() }
        findViewById<View>(R.id.btnPip).setOnClickListener { pip() }
        findViewById<View>(R.id.btnSpeed).setOnClickListener { showSpeed() }
        findViewById<View>(R.id.btnMenu).setOnClickListener { showMenu() }
        findViewById<View>(R.id.btnLock).setOnClickListener { lock(true) }
        btnUnlock.setOnClickListener { lock(false) }
        findViewById<View>(R.id.btnPrev).setOnClickListener { player.seekToPreviousMediaItem() }
        findViewById<View>(R.id.btnNext).setOnClickListener { player.seekToNextMediaItem() }
        btnPlay.setOnClickListener { togglePlay(); schedHide() }
        findViewById<View>(R.id.btnFit).setOnClickListener { cycleFit() }

        seek.setOnSeekBarChangeListener(object : SeekAdapter() {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { if (u) tCur.text = U.time(p.toLong()) }
            override fun onStartTrackingTouch(s: SeekBar?) { dragging = true; h.removeCallbacks(hideRun) }
            override fun onStopTrackingTouch(s: SeekBar?) {
                dragging = false
                player.seekTo((s?.progress ?: 0).toLong())
                tickNow()
                schedHide()
            }
        })

        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { toggleControls(); return true }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (locked) return true
                val w = root.width.toFloat()
                val ms = Prefs.seekSec * 1000L
                when {
                    e.x < w / 3f -> seekBy(-ms)
                    e.x > w * 2f / 3f -> seekBy(ms)
                    else -> togglePlay()
                }
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                if (locked || !Prefs.gestures || e1 == null) return false
                val w = root.width.toFloat()
                val hh = root.height.toFloat()
                if (mode == 0) {
                    if (Math.abs(dx) > Math.abs(dy)) { mode = 1; scrubPos = player.currentPosition }
                    else if (e1.x < w / 2f) {
                        mode = 2
                        val b = window.attributes.screenBrightness
                        briF = if (b < 0) 0.5f else b
                    } else {
                        mode = 3
                        volF = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                    }
                }
                when (mode) {
                    1 -> {
                        val d = dur()
                        scrubPos = (scrubPos - dx * 80f).toLong().coerceIn(0L, if (d > 0) d else 0L)
                        hudShow("${U.time(scrubPos)} / ${U.time(d)}")
                    }
                    2 -> {
                        briF = (briF + dy / hh * 1.3f).coerceIn(0.02f, 1f)
                        val lp = window.attributes
                        lp.screenBrightness = briF
                        window.attributes = lp
                        hudShow("Brightness ${(briF * 100).toInt()}%")
                    }
                    3 -> {
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        volF = (volF + dy / hh * max * 1.3f).coerceIn(0f, max.toFloat())
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, volF.toInt(), 0)
                        hudShow("Volume ${(volF / max * 100).toInt()}%")
                    }
                }
                return true
            }
        })
        root.setOnTouchListener { _, ev ->
            gd.onTouchEvent(ev)
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                if (mode == 1) { player.seekTo(scrubPos); tickNow() }
                mode = 0
            }
            true
        }
    }

    /* ---------------- features ---------------- */

    private fun cycleFit() {
        resizeIdx = (resizeIdx + 1) % 3
        val modes = intArrayOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_ZOOM, AspectRatioFrameLayout.RESIZE_MODE_FILL)
        pv.resizeMode = modes[resizeIdx]
        hudShow(arrayOf("Fit", "Crop", "Stretch")[resizeIdx])
    }

    private fun showQueue() {
        val names = Queue.items.map { it.name }
        if (names.isEmpty()) return
        Pop.list(this, "Playlist", names, player.currentMediaItemIndex) { i -> player.seekTo(i, 0L) }
    }

    private fun showSpeed() {
        val v = floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f)
        val c = player.playbackParameters.speed
        Pop.list(this, "Playback speed", v.map { "${it}x" }, v.indexOfFirst { Math.abs(it - c) < 0.01f }) { i ->
            player.setPlaybackSpeed(v[i])
        }
    }

    private fun shot() {
        val bmp = (pv.videoSurfaceView as? TextureView)?.bitmap
        if (bmp == null) { U.toast(this, "Nothing to capture"); return }
        try {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.insertImage(contentResolver, bmp, "OpenMedia_${System.currentTimeMillis()}", "Open Media screenshot")
            U.toast(this, "Saved to Pictures")
        } catch (e: Exception) {
            U.toast(this, "Couldn't save screenshot")
        }
    }

    private fun pip() {
        if (Build.VERSION.SDK_INT < 26 || !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            U.toast(this, "Picture-in-picture not supported")
            return
        }
        try {
            val b = PictureInPictureParams.Builder()
            val vs = player.videoSize
            if (vs.width > 0 && vs.height > 0) {
                val r = (vs.width.toFloat() / vs.height).coerceIn(0.43f, 2.38f)
                b.setAspectRatio(Rational((r * 1000).toInt(), 1000))
            }
            enterPictureInPictureMode(b.build())
        } catch (e: Exception) {
            U.toast(this, "Picture-in-picture unavailable")
        }
    }

    private fun toggleBg() {
        Prefs.bgPlay = !Prefs.bgPlay
        updateBgIcon()
        U.toast(this, if (Prefs.bgPlay) "Background play on" else "Background play off")
    }

    private fun toggleNight() {
        night = !night
        nightView.visibility = if (night) View.VISIBLE else View.GONE
        applyVolume()
        U.toast(this, if (night) "Night mode on" else "Night mode off")
    }

    private fun toggleMirror() {
        mirror = !mirror
        pv.scaleX = if (mirror) -1f else 1f
    }

    private fun toggleLoop() {
        val on = player.repeatMode != Player.REPEAT_MODE_ONE
        player.repeatMode = if (on) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        U.toast(this, if (on) "Loop on" else "Loop off")
    }

    private fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        U.toast(this, if (player.shuffleModeEnabled) "Shuffle on" else "Shuffle off")
    }

    private fun abToggle() {
        when {
            abA < 0 -> { abA = player.currentPosition; U.toast(this, "A set at ${U.time(abA)}") }
            abB < 0 -> {
                val b = player.currentPosition
                if (b <= abA) U.toast(this, "B must be after A")
                else { abB = b; player.seekTo(abA); U.toast(this, "Repeating A–B"); tick() }
            }
            else -> { abA = -1; abB = -1; U.toast(this, "A–B repeat off") }
        }
    }

    private fun showTimer() {
        val mins = intArrayOf(0, 5, 10, 15, 30, 45, 60)
        val opts = listOf("Off", "5 minutes", "10 minutes", "15 minutes", "30 minutes", "45 minutes", "60 minutes", "Custom…")
        Pop.list(this, "Sleep timer", opts, -1) { i ->
            if (i == 7) {
                Pop.input(this, "Minutes", "e.g. 20", InputType.TYPE_CLASS_NUMBER) { v ->
                    v.toIntOrNull()?.let { setTimer(it) }
                }
            } else setTimer(mins[i])
        }
    }

    private fun setTimer(min: Int) {
        timerRun?.let { h.removeCallbacks(it) }
        timerRun = null
        if (min > 0) {
            val r = Runnable { player.pause(); timerRun = null; U.toast(this, "Sleep timer finished") }
            timerRun = r
            h.postDelayed(r, min * 60_000L)
            U.toast(this, "Sleep timer: $min min")
        } else U.toast(this, "Timer off")
    }

    private fun toggleDecoder() {
        Prefs.softwareDecoder = !Prefs.softwareDecoder
        val idx = player.currentMediaItemIndex
        val pos = player.currentPosition
        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        val rep = player.repeatMode
        val shuf = player.shuffleModeEnabled
        val speed = player.playbackParameters.speed
        detach()
        eq?.release(); eq = null
        boostFx?.release(); boostFx = null
        PlayerHolder.release()
        player = PlayerHolder.get(this)
        PlaybackService.instance?.session?.player = player
        attach()
        player.repeatMode = rep
        player.shuffleModeEnabled = shuf
        player.setPlaybackSpeed(speed)
        if (items.isNotEmpty()) player.setMediaItems(items, idx, pos)
        player.prepare()
        player.playWhenReady = true
        pv.player = player
        U.toast(this, if (Prefs.softwareDecoder) "Software decoder" else "Hardware decoder")
    }

    private fun handleError(e: PlaybackException) {
        updatePlayIcon()
        if (errAsked) return
        errAsked = true
        val other = if (Prefs.softwareDecoder) "hardware" else "software"
        Pop.confirm(this, "Can't play this file", "${e.errorCodeName}\n\nTry the $other decoder?", "Try") { toggleDecoder() }
    }

    private fun showProps() {
        val m = cur() ?: return
        val sb = StringBuilder(U.props(m))
        val vf = player.videoFormat
        val vs = player.videoSize
        if (vf != null) {
            sb.append("\n\nVideo\n${vs.width}x${vs.height}  ${vf.sampleMimeType?.substringAfter('/') ?: ""}  ${vf.codecs ?: ""}")
            if (vf.frameRate > 0) sb.append("  ${vf.frameRate.toInt()} fps")
            if (vf.bitrate > 0) sb.append("  ${vf.bitrate / 1000} kbps")
        }
        val af = player.audioFormat
        if (af != null) {
            sb.append("\n\nAudio\n${af.sampleMimeType?.substringAfter('/') ?: ""}  ${af.channelCount} ch  ${af.sampleRate} Hz")
        }
        Pop.info(this, "Properties", sb.toString())
    }

    private fun share() { cur()?.let { U.share(this, it) } }

    private fun confirmDelete() {
        val idx = player.currentMediaItemIndex
        val m = cur() ?: return
        Pop.confirm(this, "Delete file?", m.name, "Delete") {
            Del.delete(this, m) {
                Queue.items = Queue.items.filterIndexed { i, _ -> i != idx }
                player.removeMediaItem(idx)
                if (player.mediaItemCount == 0) finish() else updateTitle()
            }
        }
    }

    /* ---------------- tracks & subtitles ---------------- */

    private fun describe(f: Format, n: Int): String {
        val lang = f.language?.let { if (it == "und") null else Locale(it).displayLanguage }
        val name = f.label ?: lang ?: "Track $n"
        val extra = ArrayList<String>()
        if (f.channelCount > 0) extra.add("${f.channelCount}ch")
        f.sampleMimeType?.substringAfter('/')?.let { extra.add(it) }
        return if (extra.isEmpty()) name else "$name  ·  ${extra.joinToString(" ")}"
    }

    private fun trackDialog(type: Int, title: String, allowOff: Boolean) {
        val names = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        var sel = -1
        if (allowOff) {
            names.add("Off")
            actions.add {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(type, true).build()
            }
            if (player.trackSelectionParameters.disabledTrackTypes.contains(type)) sel = 0
        }
        for (g in player.currentTracks.groups) {
            if (g.type != type) continue
            for (i in 0 until g.length) {
                names.add(describe(g.getTrackFormat(i), names.size + 1))
                if (g.isTrackSelected(i) && sel == -1) sel = names.size - 1
                actions.add {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(type, false)
                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
                        .build()
                }
            }
        }
        if (type == C.TRACK_TYPE_TEXT) {
            names.add("Load subtitle file…")
            actions.add { pickSub() }
        }
        if (actions.isEmpty()) { U.toast(this, "No tracks available"); return }
        Pop.list(this, title, names, sel) { i -> actions[i]() }
    }

    private fun pickSub() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        startActivityForResult(i, REQ_SUB)
    }

    private fun applySubtitle(uri: Uri) {
        var name = uri.lastPathSegment ?: ""
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { name = it }
            }
        } catch (_: Exception) {
        }
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = when (ext) {
            "vtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "ttml", "xml", "dfxp" -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val item = player.currentMediaItem ?: return
        val sub = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mime)
            .setLanguage("und")
            .setLabel(name)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val idx = player.currentMediaItemIndex
        val pos = player.currentPosition
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
        player.replaceMediaItem(idx, item.buildUpon().setSubtitleConfigurations(listOf(sub)).build())
        player.seekTo(idx, pos)
        U.toast(this, "Subtitle loaded")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Del.onResult(this, requestCode, resultCode)
        val u = data?.data
        if (requestCode == REQ_SUB && resultCode == RESULT_OK && u != null) applySubtitle(u)
    }

    /* ---------------- equalizer & boost ---------------- */

    private fun setupFx(sid: Int) {
        eq?.release(); eq = null
        boostFx?.release(); boostFx = null
        if (sid == C.AUDIO_SESSION_ID_UNSET || sid == 0) return
        try {
            val e = Equalizer(0, sid)
            for (b in 0 until e.numberOfBands.toInt()) {
                val lv = Prefs.eqLevel(b)
                if (lv != null) e.setBandLevel(b.toShort(), lv.toShort())
            }
            e.setEnabled(Prefs.eqOn)
            eq = e
        } catch (_: Exception) {
            eq = null
        }
        try {
            val le = LoudnessEnhancer(sid)
            le.setTargetGain(Prefs.boost)
            le.setEnabled(Prefs.boost > 0)
            boostFx = le
        } catch (_: Exception) {
            boostFx = null
        }
    }

    private fun showEq() {
        val e = eq
        if (e == null) { U.toast(this, "Equalizer unavailable right now"); return }
        val card = Pop.card(this, "Equalizer")
        val d = Pop.dialog(this, card)
        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.VERTICAL
        val sv = MaxScroll(this, (resources.displayMetrics.heightPixels * 0.62f).toInt())
        sv.addView(inner)

        val sw = Switch(this)
        sw.text = "Enabled"
        sw.setTextColor(Color.WHITE)
        sw.isChecked = Prefs.eqOn
        sw.setOnCheckedChangeListener { _, on ->
            try { e.setEnabled(on) } catch (_: Exception) {}
            Prefs.eqOn = on
        }
        inner.addView(sw)

        val range = e.bandLevelRange
        val min = range[0].toInt()
        val max = range[1].toInt()
        for (b in 0 until e.numberOfBands.toInt()) {
            val f = e.getCenterFreq(b.toShort()) / 1000
            val label = if (f >= 1000) "${f / 1000} kHz" else "$f Hz"
            inner.addView(Pop.text(this, label, 13f, Pop.DIM).also { it.setPadding(0, U.dp(this, 8), 0, 0) })
            val sb = SeekBar(this)
            sb.max = max - min
            sb.progress = e.getBandLevel(b.toShort()) - min
            sb.setOnSeekBarChangeListener(object : SeekAdapter() {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    if (!u) return
                    try { e.setBandLevel(b.toShort(), (p + min).toShort()) } catch (_: Exception) {}
                    Prefs.setEq(b, p + min)
                }
            })
            inner.addView(sb)
        }

        inner.addView(Pop.text(this, "Volume boost", 13f, Pop.DIM).also { it.setPadding(0, U.dp(this, 14), 0, 0) })
        val bs = SeekBar(this)
        bs.max = 1500
        bs.progress = Prefs.boost
        bs.setOnSeekBarChangeListener(object : SeekAdapter() {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                if (!u) return
                Prefs.boost = p
                try { boostFx?.setTargetGain(p); boostFx?.setEnabled(p > 0) } catch (_: Exception) {}
            }
        })
        inner.addView(bs)

        card.addView(sv)
        card.addView(Pop.buttons(this, d, null, "Done") {})
        d.show()
    }

    /* ---------------- menu popup (grid) ---------------- */

    private fun showMenu() {
        val items = listOf(
            GridItem("Background Play", R.drawable.ic_headphones, Prefs.bgPlay) { toggleBg() },
            GridItem("Equalizer", R.drawable.ic_eq, Prefs.eqOn) { showEq() },
            GridItem("Night Mode", R.drawable.ic_moon, night) { toggleNight() },
            GridItem("Timer", R.drawable.ic_timer, timerRun != null) { showTimer() },
            GridItem("AB Repeat", R.drawable.ic_ab, abA >= 0) { abToggle() },
            GridItem("Mirror", R.drawable.ic_mirror, mirror) { toggleMirror() },
            GridItem(if (Prefs.softwareDecoder) "SW Decoder" else "HW Decoder", R.drawable.ic_chip, Prefs.softwareDecoder) { toggleDecoder() },
            GridItem("Loop", R.drawable.ic_repeat, player.repeatMode == Player.REPEAT_MODE_ONE) { toggleLoop() },
            GridItem("Shuffle", R.drawable.ic_shuffle, player.shuffleModeEnabled) { toggleShuffle() },
            GridItem("Properties", R.drawable.ic_info) { showProps() },
            GridItem("Share", R.drawable.ic_share) { share() },
            GridItem("Delete", R.drawable.ic_delete) { confirmDelete() },
            GridItem("Feedback", R.drawable.ic_feedback) { U.feedback(this) }
        )
        Pop.grid(this, items)
    }
}
