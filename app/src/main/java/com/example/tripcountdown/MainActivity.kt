package com.example.tripcountdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Trip(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val epochDay: Long,
    val endEpochDay: Long = epochDay,
    val photoPath: String? = null
)

object TripStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "trips.json")

    fun load(ctx: Context): List<Trip> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Trip(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    epochDay = o.getLong("epochDay"),
                    endEpochDay = o.optLong("endEpochDay", o.getLong("epochDay")),
                    photoPath = if (o.isNull("photoPath")) null else o.getString("photoPath")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(ctx: Context, trips: List<Trip>) {
        val arr = JSONArray()
        trips.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("epochDay", it.epochDay)
                put("endEpochDay", it.endEpochDay)
                put("photoPath", it.photoPath ?: JSONObject.NULL)
            })
        }
        file(ctx).writeText(arr.toString())
    }

    /** Copies the chosen photo into the app's private storage, shrunk to max 1080px. */
    fun importPhoto(ctx: Context, uri: Uri): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 2200 || bounds.outHeight / sample > 2200) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            val scale = 1080f / maxOf(bmp.width, bmp.height)
            val out = if (scale < 1f)
                Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
            else bmp
            val dir = File(ctx.filesDir, "photos").apply { mkdirs() }
            val dest = File(dir, UUID.randomUUID().toString() + ".jpg")
            dest.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun deletePhoto(path: String?) {
        if (path != null) File(path).delete()
    }
}
