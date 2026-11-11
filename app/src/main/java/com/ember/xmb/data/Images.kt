package com.ember.xmb.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.net.HttpURLConnection
import java.net.URL

/** Rasterise any Drawable (adaptive icons included) at an exact size. */
fun Drawable.toImageBitmap(width: Int, height: Int): ImageBitmap {
    if (this is BitmapDrawable) {
        bitmap?.let { return Bitmap.createScaledBitmap(it, width, height, true).asImageBitmap() }
    }
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, width, height)
    draw(Canvas(out))
    return out.asImageBitmap()
}

/**
 * Artwork for Continue Watching cards.
 *
 * Watch Next rows hand back a poster URI that may be content://, file:// or https://,
 * so all three are handled. Decoding is two-pass so a 2000px poster is downsampled
 * before it ever reaches memory — on a TV with 1–1.5 GB of RAM that matters.
 *
 * Call [load] off the main thread.
 */
class ArtLoader(context: Context) {

    private val appContext = context.applicationContext
    private val cache = LruCache<String, ImageBitmap>(32)

    fun cached(uri: String): ImageBitmap? = cache.get(uri)

    fun load(uri: String, reqWidth: Int = 512): ImageBitmap? {
        cache.get(uri)?.let { return it }
        val bitmap = try {
            if (uri.startsWith("http://") || uri.startsWith("https://")) fromHttp(uri, reqWidth)
            else fromResolver(uri, reqWidth)
        } catch (t: Throwable) {
            null
        } ?: return null
        return bitmap.asImageBitmap().also { cache.put(uri, it) }
    }

    private fun fromResolver(uri: String, reqWidth: Int): Bitmap? {
        val parsed = Uri.parse(uri)
        val cr = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, reqWidth)
        }
        return cr.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun fromHttp(uri: String, reqWidth: Int): Bitmap? {
        val conn = (URL(uri).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 8000
            instanceFollowRedirects = true
        }
        val bytes = try {
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, reqWidth)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun sampleSize(srcWidth: Int, reqWidth: Int): Int {
        var sample = 1
        if (srcWidth <= 0 || reqWidth <= 0) return sample
        while (srcWidth / sample > reqWidth * 2) sample *= 2
        return sample
    }
}
