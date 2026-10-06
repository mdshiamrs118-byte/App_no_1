package com.openmedia.app

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import android.util.Size
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/* ---------- small helpers ---------- */

object U {
    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density + 0.5f).toInt()

    fun time(ms: Long): String {
        val s = (if (ms < 0) 0L else ms) / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%02d:%02d", m, sec)
    }

    fun size(b: Long): String {
        if (b < 1024) return "$b B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = b.toDouble()
        var i = -1
        while (v >= 1024 && i < 3) { v /= 1024; i++ }
        return String.format(Locale.US, "%.1f %s", v, units[i])
    }

    fun date(sec: Long): String = SimpleDateFormat("M/d/yy", Locale.US).format(Date(sec * 1000))

    fun toast(c: Context, s: String) = Toast.makeText(c, s, Toast.LENGTH_SHORT).show()

    fun props(m: Media): String {
        val sb = StringBuilder()
        sb.append("Name\n").append(m.name)
        if (m.path.isNotEmpty()) sb.append("\n\nPath\n").append(m.path)
        if (m.size > 0) sb.append("\n\nSize\n").append(size(m.size))
        if (m.duration > 0) sb.append("\n\nDuration\n").append(time(m.duration))
        if (m.added > 0) sb.append("\n\nAdded\n").append(date(m.added))
        return sb.toString()
    }

    fun share(c: Context, m: Media) {
        val i = Intent(Intent.ACTION_SEND)
        if (m.uri.scheme?.startsWith("http") == true) {
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_TEXT, m.uri.toString())
        } else {
            i.type = if (m.video) "video/*" else "audio/*"
            i.putExtra(Intent.EXTRA_STREAM, m.uri)
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        c.startActivity(Intent.createChooser(i, "Share"))
    }

    // Change this to your own address
    const val FEEDBACK_EMAIL = "feedback@example.com"

    fun feedback(c: Context) {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        i.putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
        i.putExtra(Intent.EXTRA_SUBJECT, "Open Media feedback")
        try { c.startActivity(i) } catch (e: Exception) { toast(c, "No email app found") }
    }
}

open class SeekAdapter : SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {}
    override fun onStartTrackingTouch(s: SeekBar?) {}
    override fun onStopTrackingTouch(s: SeekBar?) {}
}

/* ---------- preferences ---------- */

object Prefs {
    private var sp: SharedPreferences? = null
    fun init(c: Context) {
        if (sp == null) sp = c.applicationContext.getSharedPreferences("openmedia", Context.MODE_PRIVATE)
    }
    private val p: SharedPreferences get() = sp!!

    var resume: Boolean
        get() = p.getBoolean("resume", true)
        set(v) { p.edit().putBoolean("resume", v).apply() }
    var autoRotate: Boolean
        get() = p.getBoolean("autoRotate", true)
        set(v) { p.edit().putBoolean("autoRotate", v).apply() }
    var gestures: Boolean
        get() = p.getBoolean("gestures", true)
        set(v) { p.edit().putBoolean("gestures", v).apply() }
    var softwareDecoder: Boolean
        get() = p.getBoolean("swdec", false)
        set(v) { p.edit().putBoolean("swdec", v).apply() }
    var bgPlay: Boolean
        get() = p.getBoolean("bgplay", false)
        set(v) { p.edit().putBoolean("bgplay", v).apply() }
    var eqOn: Boolean
        get() = p.getBoolean("eqon", false)
        set(v) { p.edit().putBoolean("eqon", v).apply() }
    var seekSec: Int
        get() = p.getInt("seekSec", 10)
        set(v) { p.edit().putInt("seekSec", v).apply() }
    var speed: Float
        get() = p.getFloat("speed", 1f)
        set(v) { p.edit().putFloat("speed", v).apply() }
    var sort: Int
        get() = p.getInt("sort", 0)
        set(v) { p.edit().putInt("sort", v).apply() }
    var boost: Int
        get() = p.getInt("boost", 0)
        set(v) { p.edit().putInt("boost", v).apply() }
    var lastUri: String?
        get() = p.getString("last", null)
        set(v) { p.edit().putString("last", v).apply() }

    fun eqLevel(b: Int): Int? = if (p.contains("eq$b")) p.getInt("eq$b", 0) else null
    fun setEq(b: Int, v: Int) { p.edit().putInt("eq$b", v).apply() }

    private fun k(u: Uri) = "pos" + u.toString().hashCode()
    fun getPos(u: Uri): Long = p.getLong(k(u), 0L)
    fun setPos(u: Uri, v: Long) { p.edit().putLong(k(u), v).apply() }
    fun clearPos(u: Uri) { p.edit().remove(k(u)).apply() }
}

/* ---------- media model ---------- */

data class Media(
    val id: Long,
    val uri: Uri,
    val name: String,
    val path: String,
    val size: Long,
    val duration: Long,
    val added: Long,
    val video: Boolean
) {
    val folder: String get() = if (path.contains('/')) path.substringBeforeLast('/') else ""
}

object Queue {
    var items: List<Media> = emptyList()
    var index: Int = 0
}

object Repo {
    fun load(ctx: Context, video: Boolean): List<Media> {
        val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf("_id", "_display_name", "_data", "_size", "duration", "date_added")
        val sel = if (video) null else "is_music != 0"
        val out = ArrayList<Media>()
        try {
            ctx.contentResolver.query(base, proj, sel, null, "date_added DESC")?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    out.add(
                        Media(
                            id, ContentUris.withAppendedId(base, id),
                            c.getString(1) ?: "Unknown", c.getString(2) ?: "",
                            c.getLong(3), c.getLong(4), c.getLong(5), video
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun fromUri(ctx: Context, uri: Uri): Media {
        var name = uri.lastPathSegment ?: "Media"
        var size = 0L
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    size = c.getLong(1)
                }
            }
        } catch (_: Exception) {
        }
        val type = try { ctx.contentResolver.getType(uri) } catch (_: Exception) { null }
        val audio = type?.startsWith("audio") == true
        return Media(-1, uri, name, uri.toString(), size, 0, 0, !audio)
    }
}

/* ---------- delete (handles Android 10/11+ scoped storage) ---------- */

object Del {
    const val REQ = 4242
    private var pending: (() -> Unit)? = null
    private var pendingUri: Uri? = null

    fun delete(a: Activity, m: Media, done: () -> Unit) {
        pending = done
        pendingUri = m.uri
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val pi = MediaStore.createDeleteRequest(a.contentResolver, listOf(m.uri))
                a.startIntentSenderForResult(pi.intentSender, REQ, null, 0, 0, 0)
            } else {
                a.contentResolver.delete(m.uri, null, null)
                done()
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) {
                try {
                    a.startIntentSenderForResult(e.userAction.actionIntent.intentSender, REQ, null, 0, 0, 0)
                } catch (_: Exception) {
                    U.toast(a, "Can't delete this file")
                }
            } else U.toast(a, "Can't delete this file")
        } catch (e: Exception) {
            U.toast(a, "Can't delete this file")
        }
    }

    fun onResult(a: Activity, req: Int, res: Int) {
        if (req != REQ) return
        if (res == Activity.RESULT_OK) {
            if (Build.VERSION.SDK_INT < 30) {
                pendingUri?.let { try { a.contentResolver.delete(it, null, null) } catch (_: Exception) {} }
            }
            pending?.invoke()
        }
        pending = null
    }
}

/* ---------- thumbnails ---------- */

object Thumbs {
    private val cache = LruCache<Long, Bitmap>(80)
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun load(ctx: Context, m: Media, iv: ImageView) {
        iv.tag = m.id
        val c = cache.get(m.id)
        if (c != null) {
            iv.clearColorFilter()
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageBitmap(c)
            return
        }
        iv.scaleType = ImageView.ScaleType.CENTER_INSIDE
        iv.setColorFilter(0xFF6B7280.toInt())
        iv.setImageResource(R.drawable.ic_video)
        val app = ctx.applicationContext
        pool.execute {
            val bmp: Bitmap? = try {
                if (Build.VERSION.SDK_INT >= 29) {
                    app.contentResolver.loadThumbnail(m.uri, Size(240, 160), null)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Video.Thumbnails.getThumbnail(app.contentResolver, m.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
                }
            } catch (e: Exception) {
                null
            }
            if (bmp != null) {
                cache.put(m.id, bmp)
                main.post {
                    if (iv.tag == m.id) {
                        iv.clearColorFilter()
                        iv.scaleType = ImageView.ScaleType.CENTER_CROP
                        iv.setImageBitmap(bmp)
                    }
                }
            }
        }
    }
}
