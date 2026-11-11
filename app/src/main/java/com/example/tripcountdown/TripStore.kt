package com.example.tripcountdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class TodoItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val done: Boolean = false,
    val dueEpochDay: Long? = null
)

data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val path: String
)

data class ItineraryItem(
    val id: String = UUID.randomUUID().toString(),
    val epochDay: Long,
    val place: String = "",
    val activity: String = ""
)

data class Luggage(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val photoPath: String? = null,
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
    val zoom: Float = 1f,
    val tags: List<Attachment> = emptyList()
)

data class Trip(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val epochDay: Long,
    val endEpochDay: Long = epochDay,
    val photoPath: String? = null,
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
    val zoom: Float = 1f,
    val todos: List<TodoItem> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val luggageIds: List<String> = emptyList(),
    val itinerary: List<ItineraryItem> = emptyList(),
    val destination: String = ""
)

object TripStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "trips.json")

    private fun readItinerary(arr: JSONArray?): List<ItineraryItem> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            ItineraryItem(
                o.getString("id"),
                o.getLong("epochDay"),
                o.optString("place", ""),
                o.optString("activity", "")
            )
        }
    }

    private fun readStrings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }

    private fun readAttachments(arr: JSONArray?): List<Attachment> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Attachment(o.getString("id"), o.getString("label"), o.getString("path"))
        }
    }

    private fun readTodos(arr: JSONArray?): List<TodoItem> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            TodoItem(
                o.getString("id"), o.getString("text"), o.optBoolean("done", false),
                if (o.has("dueEpochDay")) o.getLong("dueEpochDay") else null
            )
        }
    }

    private fun backupCorrupt(f: File) {
        try {
            f.copyTo(File(f.parentFile, f.name + ".corrupt"), overwrite = true)
        } catch (e: Exception) {
        }
    }

    private fun writeAtomic(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }

    fun load(ctx: Context): List<Trip> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        val arr = try {
            JSONArray(f.readText())
        } catch (e: Exception) {
            backupCorrupt(f)
            return emptyList()
        }
        val out = mutableListOf<Trip>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                out.add(
                    Trip(
                        id = o.getString("id"),
                        name = o.optString("name", ""),
                        epochDay = o.getLong("epochDay"),
                        endEpochDay = o.optLong("endEpochDay", o.getLong("epochDay")),
                        photoPath = if (o.isNull("photoPath")) null else o.getString("photoPath"),
                        focusX = o.optDouble("focusX", 0.5).toFloat(),
                        focusY = o.optDouble("focusY", 0.5).toFloat(),
                        zoom = o.optDouble("zoom", 1.0).toFloat(),
                        todos = readTodos(o.optJSONArray("todos")),
                        attachments = readAttachments(o.optJSONArray("attachments")),
                        luggageIds = readStrings(o.optJSONArray("luggageIds")),
                        itinerary = readItinerary(o.optJSONArray("itinerary")),
                        destination = o.optString("destination", "")
                    )
                )
            } catch (e: Exception) {
            }
        }
        return out
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
                put("focusX", it.focusX.toDouble())
                put("focusY", it.focusY.toDouble())
                put("zoom", it.zoom.toDouble())
                put("luggageIds", JSONArray(it.luggageIds))
                put("destination", it.destination)
                put("itinerary", JSONArray().also { list ->
                    it.itinerary.forEach { e ->
                        list.put(JSONObject().apply {
                            put("id", e.id)
                            put("epochDay", e.epochDay)
                            put("place", e.place)
                            put("activity", e.activity)
                        })
                    }
                })
                put("attachments", JSONArray().also { list ->
                    it.attachments.forEach { a ->
                        list.put(JSONObject().apply {
                            put("id", a.id)
                            put("label", a.label)
                            put("path", a.path)
                        })
                    }
                })
                put("todos", JSONArray().also { list ->
                    it.todos.forEach { t ->
                        list.put(JSONObject().apply {
                            put("id", t.id)
                            put("text", t.text)
                            put("done", t.done)
                            t.dueEpochDay?.let { d -> put("dueEpochDay", d) }
                        })
                    }
                })
            })
        }
        writeAtomic(file(ctx), arr.toString())
    }

    private fun luggageFile(ctx: Context) = File(ctx.filesDir, "luggage.json")

    fun loadLuggage(ctx: Context): List<Luggage> {
        val f = luggageFile(ctx)
        if (!f.exists()) return emptyList()
        val arr = try {
            JSONArray(f.readText())
        } catch (e: Exception) {
            backupCorrupt(f)
            return emptyList()
        }
        val out = mutableListOf<Luggage>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                out.add(
                    Luggage(
                        id = o.getString("id"),
                        name = o.optString("name", ""),
                        photoPath = if (o.isNull("photoPath")) null else o.getString("photoPath"),
                        focusX = o.optDouble("focusX", 0.5).toFloat(),
                        focusY = o.optDouble("focusY", 0.5).toFloat(),
                        zoom = o.optDouble("zoom", 1.0).toFloat(),
                        tags = readAttachments(o.optJSONArray("tags"))
                    )
                )
            } catch (e: Exception) {
            }
        }
        return out
    }

    fun saveLuggage(ctx: Context, items: List<Luggage>) {
        val arr = JSONArray()
        items.forEach { b ->
            arr.put(JSONObject().apply {
                put("id", b.id)
                put("name", b.name)
                put("photoPath", b.photoPath ?: JSONObject.NULL)
                put("focusX", b.focusX.toDouble())
                put("focusY", b.focusY.toDouble())
                put("zoom", b.zoom.toDouble())
                put("tags", JSONArray().also { list ->
                    b.tags.forEach { a ->
                        list.put(JSONObject().apply {
                            put("id", a.id)
                            put("label", a.label)
                            put("path", a.path)
                        })
                    }
                })
            })
        }
        writeAtomic(luggageFile(ctx), arr.toString())
    }

    /** Turns a photo upright using the orientation tag the camera saved in it. */
    private fun applyExifOrientation(ctx: Context, uri: Uri, bmp: Bitmap): Bitmap {
        val orientation = try {
            ctx.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                m.postRotate(90f)
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                m.postRotate(270f)
                m.postScale(-1f, 1f)
            }
            else -> return bmp
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    /** Copies the chosen photo into the app's private storage, shrunk to max 1080px. */
    fun importPhoto(ctx: Context, uri: Uri, maxDim: Int = 1080): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            val scale = maxDim.toFloat() / maxOf(bmp.width, bmp.height)
            val out = if (scale < 1f)
                Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
            else bmp
            val dir = File(ctx.filesDir, "photos").apply { mkdirs() }
            val dest = File(dir, UUID.randomUUID().toString() + ".jpg")
            val upright = applyExifOrientation(ctx, uri, out)
            dest.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, if (maxDim > 1080) 92 else 85, it) }
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** Copies a chosen PDF into the app's private storage. */
    fun importPdf(ctx: Context, uri: Uri): String? {
        return try {
            val dir = File(ctx.filesDir, "docs").apply { mkdirs() }
            val dest = File(dir, UUID.randomUUID().toString() + ".pdf")
            val input = ctx.contentResolver.openInputStream(uri) ?: return null
            input.use { ins -> dest.outputStream().use { out -> ins.copyTo(out) } }
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private const val DATA_VERSION = 3

    private fun resolvePath(ctx: Context, p: String?): String? {
        if (p == null) return null
        val f = File(p)
        if (f.exists()) return p
        for (dir in listOf("photos", "docs")) {
            val c = File(File(ctx.filesDir, dir), f.name)
            if (c.exists()) return c.absolutePath
        }
        return null
    }

    /**
     * Runs every time the app opens. Keeps a backup when the data format changes, then checks every
     * saved trip and bag and repairs anything that does not fit the current version of the app.
     */
    fun migrate(ctx: Context) {
        try {
            val prefs = ctx.getSharedPreferences("data_version", Context.MODE_PRIVATE)
            val old = prefs.getInt("version", 0)
            if (old < DATA_VERSION) {
                for (name in listOf("trips.json", "luggage.json")) {
                    val src = File(ctx.filesDir, name)
                    if (src.exists()) {
                        src.copyTo(File(ctx.filesDir, "backup_v" + old + "_" + name), overwrite = true)
                    }
                }
            }
            val bags = loadLuggage(ctx)
            val fixedBags = bags.map { b ->
                b.copy(
                    name = b.name.ifBlank { "Luggage" },
                    photoPath = resolvePath(ctx, b.photoPath),
                    focusX = b.focusX.coerceIn(0f, 1f),
                    focusY = b.focusY.coerceIn(0f, 1f),
                    zoom = b.zoom.coerceIn(1f, 5f),
                    tags = b.tags.mapNotNull { a -> resolvePath(ctx, a.path)?.let { p -> a.copy(path = p) } }
                )
            }.distinctBy { it.id }
            val bagIds = fixedBags.map { it.id }.toSet()

            val trips = load(ctx)
            val fixedTrips = trips.map { t ->
                t.copy(
                    name = t.name.ifBlank { "Untitled trip" },
                    endEpochDay = maxOf(t.endEpochDay, t.epochDay),
                    photoPath = resolvePath(ctx, t.photoPath),
                    focusX = t.focusX.coerceIn(0f, 1f),
                    focusY = t.focusY.coerceIn(0f, 1f),
                    zoom = t.zoom.coerceIn(1f, 5f),
                    todos = t.todos.filter { it.text.isNotBlank() }.distinctBy { it.id },
                    attachments = t.attachments.mapNotNull { a -> resolvePath(ctx, a.path)?.let { p -> a.copy(path = p) } },
                    luggageIds = if (bagIds.isEmpty()) t.luggageIds else t.luggageIds.filter { it in bagIds }.distinct(),
                    itinerary = t.itinerary.distinctBy { it.id }
                )
            }.distinctBy { it.id }

            if (fixedBags != bags) saveLuggage(ctx, fixedBags)
            if (fixedTrips != trips) save(ctx, fixedTrips)
            prefs.edit().putInt("version", DATA_VERSION).apply()
        } catch (e: Exception) {
        }
    }

    /** Turns a saved photo a quarter turn clockwise and saves it as a new file. */
    fun rotatePhoto(ctx: Context, path: String): String? {
        try {
            val bmp = BitmapFactory.decodeFile(path) ?: return null
            val m = Matrix()
            m.postRotate(90f)
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            val dir = File(ctx.filesDir, "photos").apply { mkdirs() }
            val dest = File(dir, UUID.randomUUID().toString() + ".jpg")
            dest.outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            return dest.absolutePath
        } catch (e: Exception) {
            return null
        }
    }

    fun deletePhoto(path: String?) {
        if (path != null) File(path).delete()
    }
}
