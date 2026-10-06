package com.openmedia.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : Activity() {

    private lateinit var list: RecyclerView
    private lateinit var settingsView: View
    private lateinit var settingsBox: LinearLayout
    private lateinit var crumb: TextView
    private lateinit var empty: TextView
    private lateinit var fab: ImageView
    private lateinit var btnSearch: ImageView
    private lateinit var adapter: RowAdapter
    private val tabIcons = ArrayList<ImageView>()
    private val tabLabels = ArrayList<TextView>()

    private var tab = 0
    private var videos: List<Media> = emptyList()
    private var audios: List<Media> = emptyList()
    private var shown: List<Media> = emptyList()
    private var openFolder: String? = null
    private var query = ""
    private var loaded = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Prefs.init(this)
        setContentView(R.layout.activity_main)
        list = findViewById(R.id.list)
        settingsView = findViewById(R.id.settingsView)
        settingsBox = findViewById(R.id.settingsBox)
        crumb = findViewById(R.id.crumb)
        empty = findViewById(R.id.empty)
        fab = findViewById(R.id.fab)
        btnSearch = findViewById(R.id.btnSearch)

        list.layoutManager = LinearLayoutManager(this)
        adapter = RowAdapter({ onRow(it) }, { onMore(it) })
        list.adapter = adapter

        val ids = intArrayOf(R.id.tab0, R.id.tab1, R.id.tab2)
        for (i in 0..2) {
            val t = findViewById<LinearLayout>(ids[i])
            tabIcons.add(t.getChildAt(0) as ImageView)
            tabLabels.add(t.getChildAt(1) as TextView)
            t.setOnClickListener { setTab(i) }
        }

        btnSearch.setOnClickListener {
            Pop.input(this, "Search", "File name", InputType.TYPE_CLASS_TEXT, query) { q ->
                query = q
                refresh()
            }
        }
        findViewById<View>(R.id.btnMore).setOnClickListener { showMore() }
        crumb.setOnClickListener {
            if (query.isNotEmpty()) { query = ""; refresh() }
            else if (openFolder != null) { openFolder = null; refresh() }
        }
        fab.setOnClickListener { playLast() }
        empty.setOnClickListener { if (!hasPerm()) requestPerms() }

        buildSettings()
        setTab(0)
        if (hasPerm()) loadMedia() else requestPerms()
    }

    override fun onResume() {
        super.onResume()
        if (loaded && hasPerm()) loadMedia()
    }

    /* ---------- permissions ---------- */

    private fun hasPerm(): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
        return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPerms() {
        if (Build.VERSION.SDK_INT < 23) return
        val p = if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        requestPermissions(p, 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasPerm()) loadMedia() else refresh()
    }

    private fun loadMedia() {
        Thread {
            val v = Repo.load(this, true)
            val a = Repo.load(this, false)
            runOnUiThread {
                videos = v
                audios = a
                loaded = true
                refresh()
            }
        }.start()
    }

    /* ---------- tabs & list ---------- */

    private fun setTab(i: Int) {
        tab = i
        openFolder = null
        query = ""
        for (k in 0..2) {
            val c = if (k == i) Pop.ACCENT else Pop.DIM
            tabIcons[k].setColorFilter(c)
            tabLabels[k].setTextColor(c)
        }
        refresh()
    }

    private fun folderName(k: String) = if (k.isEmpty()) "Unknown" else k.substringAfterLast('/')

    private fun shortPath(k: String): String {
        val p = k.removePrefix("/storage/emulated/0/").removePrefix("/storage/emulated/0")
        if (p.startsWith("/storage/")) return "SD: " + p.removePrefix("/storage/").substringAfter('/', "")
        return p
    }

    private fun sorted(l: List<Media>): List<Media> = when (Prefs.sort) {
        1 -> l.sortedBy { it.name.lowercase() }
        2 -> l.sortedByDescending { it.size }
        3 -> l.sortedByDescending { it.duration }
        else -> l.sortedByDescending { it.added }
    }

    private fun mediaRow(m: Media) = Row(m.name, U.size(m.size), U.time(m.duration), m, null)

    private fun refresh() {
        val inSettings = tab == 2
        settingsView.visibility = if (inSettings) View.VISIBLE else View.GONE
        list.visibility = if (inSettings) View.GONE else View.VISIBLE
        fab.visibility = if (tab == 0 && openFolder == null && query.isEmpty()) View.VISIBLE else View.GONE
        btnSearch.setColorFilter(if (query.isNotEmpty()) Pop.ACCENT else Color.WHITE)
        if (inSettings) {
            crumb.text = "Settings"
            empty.visibility = View.GONE
            return
        }
        val src = if (tab == 0) videos else audios
        val rows = ArrayList<Row>()
        if (query.isNotEmpty()) {
            shown = sorted(src.filter { it.name.contains(query, true) })
            crumb.text = "Results for \"$query\"  ✕"
            shown.forEach { rows.add(mediaRow(it)) }
        } else if (tab == 0 && openFolder == null) {
            shown = emptyList()
            crumb.text = "All folders"
            val groups = src.groupBy { it.folder }
            val keys = if (Prefs.sort == 1) groups.keys.sortedBy { folderName(it).lowercase() }
            else groups.keys.sortedByDescending { k -> groups[k]!!.maxOf { it.added } }
            for (k in keys) {
                val g = groups[k]!!
                val sub = "${g.size} video${if (g.size == 1) "" else "s"}  ·  ${shortPath(k)}"
                rows.add(Row(folderName(k), sub, U.date(g.maxOf { it.added }), null, k))
            }
        } else {
            val base = if (tab == 0) src.filter { it.folder == openFolder } else src
            shown = sorted(base)
            crumb.text = if (tab == 0) "◂  ${folderName(openFolder ?: "")}" else "All music"
            shown.forEach { rows.add(mediaRow(it)) }
        }
        adapter.set(rows)
        if (rows.isEmpty()) {
            empty.visibility = View.VISIBLE
            empty.text = if (!hasPerm()) "Allow access to your media\n\nTap here to grant permission" else "Nothing here yet"
        } else empty.visibility = View.GONE
    }

    private fun onRow(r: Row) {
        if (r.folderKey != null) {
            openFolder = r.folderKey
            refresh()
            list.scrollToPosition(0)
            return
        }
        val m = r.media ?: return
        val idx = shown.indexOf(m)
        play(shown, if (idx < 0) 0 else idx)
    }

    private fun onMore(r: Row) {
        val m = r.media ?: return
        Pop.list(this, m.name, listOf("Play", "Share", "Properties", "Delete"), -1) { i ->
            when (i) {
                0 -> onRow(r)
                1 -> U.share(this, m)
                2 -> Pop.info(this, "Properties", U.props(m))
                3 -> Pop.confirm(this, "Delete file?", m.name, "Delete") {
                    Del.delete(this, m) { loadMedia() }
                }
            }
        }
    }

    private fun play(items: List<Media>, index: Int) {
        if (items.isEmpty()) return
        Queue.items = items
        Queue.index = index
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun playLast() {
        val u = Prefs.lastUri
        val m = videos.firstOrNull { it.uri.toString() == u }
        if (m == null) {
            U.toast(this, "Nothing to resume yet")
            return
        }
        val g = sorted(videos.filter { it.folder == m.folder })
        play(g, g.indexOf(m).coerceAtLeast(0))
    }

    /* ---------- menus ---------- */

    private fun showMore() {
        Pop.list(this, "Open Media", listOf("Open file…", "Network stream…", "Sort by…", "Refresh"), -1) { i ->
            when (i) {
                0 -> {
                    val pick = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    pick.addCategory(Intent.CATEGORY_OPENABLE)
                    pick.type = "*/*"
                    startActivityForResult(pick, 77)
                }
                1 -> Pop.input(this, "Network stream", "https://…", Pop.textInputTypeUrl()) { url ->
                    if (url.isNotEmpty()) {
                        val u = Uri.parse(if (url.contains("://")) url else "https://$url")
                        val name = u.lastPathSegment ?: url
                        play(listOf(Media(-1, u, name, u.toString(), 0, 0, 0, true)), 0)
                    }
                }
                2 -> Pop.list(
                    this, "Sort by",
                    listOf("Newest first", "Name (A–Z)", "Size (largest)", "Duration (longest)"), Prefs.sort
                ) { s -> Prefs.sort = s; refresh() }
                3 -> loadMedia()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Del.onResult(this, requestCode, resultCode)
        val u = data?.data
        if (requestCode == 77 && resultCode == RESULT_OK && u != null) {
            play(listOf(Repo.fromUri(this, u)), 0)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            query.isNotEmpty() -> { query = ""; refresh() }
            tab == 0 && openFolder != null -> { openFolder = null; refresh() }
            else -> super.onBackPressed()
        }
    }

    /* ---------- settings ---------- */

    private fun dp(v: Int) = U.dp(this, v)

    private fun section(t: String): TextView {
        val tv = Pop.text(this, t, 13f, Pop.ACCENT, true)
        tv.setPadding(dp(20), dp(18), dp(20), dp(6))
        return tv
    }

    private fun rowView(title: String, sub: String, trailing: View?, click: () -> Unit): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(20), dp(12), dp(20), dp(12))
        r.setBackgroundResource(android.R.drawable.list_selector_background)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(Pop.text(this, title, 16f, Color.WHITE))
        if (sub.isNotEmpty()) col.addView(Pop.text(this, sub, 13f, Pop.DIM))
        r.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (trailing != null) r.addView(trailing)
        r.setOnClickListener { click() }
        return r
    }

    private fun switchRow(title: String, sub: String, value: Boolean, set: (Boolean) -> Unit): View {
        val sw = Switch(this)
        sw.isChecked = value
        sw.isClickable = false
        val row = rowView(title, sub, sw) { sw.toggle(); set(sw.isChecked) }
        return row
    }

    private fun buildSettings() {
        settingsBox.removeAllViews()
        settingsBox.addView(section("PLAYBACK"))
        settingsBox.addView(switchRow("Resume playback", "Continue from where you stopped", Prefs.resume) { Prefs.resume = it })
        settingsBox.addView(switchRow("Auto-rotate to video", "Landscape for wide videos, portrait for tall ones", Prefs.autoRotate) { Prefs.autoRotate = it })
        settingsBox.addView(switchRow("Gestures", "Swipe for brightness, volume and seeking", Prefs.gestures) { Prefs.gestures = it })
        settingsBox.addView(switchRow("Background play", "Keep playing when the app is closed", Prefs.bgPlay) { Prefs.bgPlay = it })
        settingsBox.addView(switchRow("Software decoder", "Use if a video shows green/black or no picture", Prefs.softwareDecoder) { Prefs.softwareDecoder = it })

        val secs = listOf(5, 10, 15, 30)
        settingsBox.addView(rowView("Double-tap seek", "${Prefs.seekSec} seconds", null) {
            Pop.list(this, "Double-tap seek", secs.map { "$it seconds" }, secs.indexOf(Prefs.seekSec)) { i ->
                Prefs.seekSec = secs[i]; buildSettings()
            }
        })
        val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        settingsBox.addView(rowView("Default speed", "${Prefs.speed}x", null) {
            Pop.list(this, "Default speed", speeds.map { "${it}x" }, speeds.indexOfFirst { Math.abs(it - Prefs.speed) < 0.01f }) { i ->
                Prefs.speed = speeds[i]; buildSettings()
            }
        })

        settingsBox.addView(section("ABOUT"))
        settingsBox.addView(rowView("Send feedback", "", null) { U.feedback(this) })
        settingsBox.addView(rowView("Open Media", "Version 1.0  ·  com.openmedia.app", null) {})
    }
}
