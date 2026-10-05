package com.pro.gallery

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore

data class Media(val id: Long, val uri: Uri, val name: String, val album: String, val date: Long,
                 val size: Long, val video: Boolean, val w: Int, val h: Int)

object Session { var items: List<Media> = emptyList() }

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("gallery", 0)
    fun favs(c: Context): Set<String> = sp(c).getStringSet("favs", emptySet())!!.toSet()
    fun set(c: Context, ids: Collection<Long>, on: Boolean) {
        val s = favs(c).toMutableSet()
        ids.forEach { if (on) s.add(it.toString()) else s.remove(it.toString()) }
        sp(c).edit().putStringSet("favs", s).apply()
    }
    fun toggle(c: Context, id: Long): Boolean { val on = id.toString() !in favs(c); set(c, listOf(id), on); return on }
}

object Repo {
    fun load(c: Context): List<Media> {
        val out = ArrayList<Media>()
        val proj = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME, MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE, MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT)
        val sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        c.contentResolver.query(MediaStore.Files.getContentUri("external"), proj, sel,
            arrayOf("1", "3"), "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { q ->
            while (q.moveToNext()) {
                val id = q.getLong(0); val video = q.getInt(5) == 3
                val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                out.add(Media(id, ContentUris.withAppendedId(base, id), q.getString(1) ?: "", q.getString(2) ?: "Other",
                    q.getLong(3), q.getLong(4), video, q.getInt(6), q.getInt(7)))
            }
        }
        return out
    }
    fun delete(c: Context, l: List<Media>) = l.forEach { try { c.contentResolver.delete(it.uri, null, null) } catch (e: Exception) {} }
    fun share(c: Context, l: List<Media>) {
        val i = if (l.size == 1) Intent(Intent.ACTION_SEND).apply {
            type = if (l[0].video) "video/*" else "image/*"; putExtra(Intent.EXTRA_STREAM, l[0].uri)
        } else Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(l.map { it.uri }))
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        c.startActivity(Intent.createChooser(i, "Share"))
    }
}
