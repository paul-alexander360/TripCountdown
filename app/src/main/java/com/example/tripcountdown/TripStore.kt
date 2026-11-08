package com.example.tripcountdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
    val luggageIds: List<String> = emptyList()
)

object TripStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "trips.json")

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
                    photoPath = if (o.isNull("photoPath")) null else o.getString("photoPath"),
                    focusX = o.optDouble("focusX", 0.5).toFloat(),
                    focusY = o.optDouble("focusY", 0.5).toFloat(),
                    zoom = o.optDouble("zoom", 1.0).toFloat(),
                    todos = readTodos(o.optJSONArray("todos")),
                    attachments = readAttachments(o.optJSONArray("attachments")),
                    luggageIds = readStrings(o.optJSONArray("luggageIds"))
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
                put("focusX", it.focusX.toDouble())
                put("focusY", it.focusY.toDouble())
                put("zoom", it.zoom.toDouble())
                put("luggageIds", JSONArray(it.luggageIds))
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
        file(ctx).writeText(arr.toString())
    }

    private fun luggageFile(ctx: Context) = File(ctx.filesDir, "luggage.json")

    fun loadLuggage(ctx: Context): List<Luggage> {
        val f = luggageFile(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Luggage(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    photoPath = if (o.isNull("photoPath")) null else o.getString("photoPath"),
                    focusX = o.optDouble("focusX", 0.5).toFloat(),
                    focusY = o.optDouble("focusY", 0.5).toFloat(),
                    zoom = o.optDouble("zoom", 1.0).toFloat(),
                    tags = readAttachments(o.optJSONArray("tags"))
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
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
        luggageFile(ctx).writeText(arr.toString())
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
            dest.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, if (maxDim > 1080) 92 else 85, it) }
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun deletePhoto(path: String?) {
        if (path != null) File(path).delete()
    }
}
