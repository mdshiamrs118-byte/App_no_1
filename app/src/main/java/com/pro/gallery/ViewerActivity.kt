package com.pro.gallery

import android.app.WallpaperManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ViewerActivity : AppCompatActivity() {
    private lateinit var items: MutableList<Media>
    private lateinit var pager: ViewPager2
    private lateinit var fav: ImageButton
    private val h = Handler(Looper.getMainLooper())
    private var playing = false
    private val tick = object : Runnable {
        override fun run() { if (!playing) return; pager.setCurrentItem((pager.currentItem + 1) % items.size, true); h.postDelayed(this, 3000) }
    }
    private val cur: Media get() = items[pager.currentItem]

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); setContentView(R.layout.activity_viewer)
        items = Session.items.toMutableList()
        if (items.isEmpty()) { finish(); return }
        pager = findViewById(R.id.pager); fav = findViewById(R.id.vFav)
        pager.adapter = PA(); pager.setCurrentItem(intent.getIntExtra("i", 0), false)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() { override fun onPageSelected(position: Int) { sync() } })
        sync()
        findViewById<View>(R.id.vBack).setOnClickListener { finish() }
        fav.setOnClickListener { Prefs.toggle(this, cur.id); sync() }
        findViewById<View>(R.id.vShare).setOnClickListener { Repo.share(this, listOf(cur)) }
        findViewById<View>(R.id.vEdit).setOnClickListener {
            if (cur.video) Ui.toast(this, "Videos can't be edited") else startActivity(Intent(this, EditorActivity::class.java).setData(cur.uri))
        }
        findViewById<View>(R.id.vDel).setOnClickListener {
            Ui.dialog(this, "Delete this item?", "It will be permanently removed from your device.", "Delete", danger = true) {
                Repo.delete(this, listOf(cur)); val p = pager.currentItem; items.removeAt(p)
                if (items.isEmpty()) { finish(); return@dialog }
                pager.adapter!!.notifyItemRemoved(p); sync()
            }
        }
        findViewById<View>(R.id.vMore).setOnClickListener {
            Ui.sheet(this, "More", listOf("Details", "Start slideshow", "Set as wallpaper")) {
                when (it) { 0 -> details(); 1 -> slideshow(); else -> wallpaper() }
            }
        }
    }

    private fun sync() {
        if (items.isEmpty()) return
        findViewById<TextView>(R.id.vTitle).text = cur.name
        val on = cur.id.toString() in Prefs.favs(this)
        fav.setImageResource(if (on) R.drawable.ic_heart_on else R.drawable.ic_heart)
        fav.setColorFilter(if (on) Ui.DANGER else Ui.TEXT)
    }

    private fun bars() {
        if (playing) { playing = false; Ui.toast(this, "Slideshow stopped"); return }
        val v = if (findViewById<View>(R.id.top).visibility == View.VISIBLE) View.GONE else View.VISIBLE
        findViewById<View>(R.id.top).visibility = v; findViewById<View>(R.id.bottom).visibility = v
    }

    private fun details() {
        val m = cur
        Ui.details(this, "Details", listOf("Name" to m.name, "Album" to m.album, "Type" to if (m.video) "Video" else "Image",
            "Size" to Formatter.formatShortFileSize(this, m.size), "Resolution" to "${m.w} × ${m.h}",
            "Modified" to SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()).format(Date(m.date * 1000))))
    }

    private fun slideshow() {
        playing = true; h.postDelayed(tick, 3000)
        findViewById<View>(R.id.top).visibility = View.GONE; findViewById<View>(R.id.bottom).visibility = View.GONE
        Ui.toast(this, "Slideshow started – tap to stop")
    }

    private fun wallpaper() {
        val u = cur.uri
        Thread {
            val ok = try { contentResolver.openInputStream(u)?.use { WallpaperManager.getInstance(this).setStream(it) }; true } catch (e: Exception) { false }
            runOnUiThread { Ui.toast(this, if (ok) "Wallpaper updated" else "Couldn't set wallpaper") }
        }.start()
    }

    override fun onDestroy() { playing = false; h.removeCallbacks(tick); super.onDestroy() }

    inner class PA : RecyclerView.Adapter<PA.VH>() {
        inner class VH(f: FrameLayout, val z: ZoomImageView, val p: ImageView) : RecyclerView.ViewHolder(f)
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, t: Int): VH {
            val c = parent.context; val f = FrameLayout(c)
            f.layoutParams = ViewGroup.LayoutParams(-1, -1)
            val z = ZoomImageView(c); f.addView(z, -1, -1)
            val p = ImageView(c).apply { setImageResource(R.drawable.ic_play); setPadding(c.dp(18), c.dp(18), c.dp(18), c.dp(18)); background = shape(0x99000000.toInt(), c.dp(40).toFloat()) }
            f.addView(p, FrameLayout.LayoutParams(c.dp(80), c.dp(80), Gravity.CENTER))
            return VH(f, z, p)
        }
        override fun onBindViewHolder(vh: VH, i: Int) {
            val m = items[i]; vh.z.reset()
            Glide.with(vh.z).load(m.uri).override(2048, 2048).fitCenter().into(vh.z)
            vh.p.visibility = if (m.video) View.VISIBLE else View.GONE
            val play = { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(m.uri, "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
            vh.p.setOnClickListener { play() }
            vh.z.onTap = { if (m.video && !playing) play() else bars() }
        }
    }
}
