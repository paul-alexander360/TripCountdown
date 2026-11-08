package com.example.tripcountdown

import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { TripApp() } }
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

private val shortFmt = DateTimeFormatter.ofPattern("MMM d")
private val longFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")

private fun dateRange(t: Trip): String {
    val s = LocalDate.ofEpochDay(t.epochDay)
    val e = LocalDate.ofEpochDay(t.endEpochDay)
    val days = t.endEpochDay - t.epochDay + 1
    if (days <= 1) return s.format(longFmt)
    val first = if (s.year == e.year) s.format(shortFmt) else s.format(longFmt)
    return "$first \u2013 ${e.format(longFmt)} ($days days)"
}

private fun countdown(t: Trip): String {
    val today = LocalDate.now().toEpochDay()
    val d = t.epochDay - today
    return when {
        d > 1 -> "$d days to go"
        d == 1L -> "Tomorrow!"
        today <= t.endEpochDay ->
            if (t.endEpochDay == t.epochDay) "Today!"
            else if (d == 0L) "Starts today!"
            else "Day ${today - t.epochDay + 1} of ${t.endEpochDay - t.epochDay + 1}"
        else -> "Past trip"
    }
}

@Composable
fun TripApp() {
    val ctx = LocalContext.current
    val trips = remember { mutableStateListOf<Trip>().apply { addAll(TripStore.load(ctx)) } }
    var editing by remember { mutableStateOf<Trip?>(null) }
    var adding by remember { mutableStateOf(false) }

    fun persist() = TripStore.save(ctx, trips.toList())

    val sorted = trips.sortedBy { it.epochDay }

    Scaffold(
        containerColor = Color(0xFFE8EEF4),
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) { Text("+", style = MaterialTheme.typography.headlineMedium) }
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Spacer(Modifier.height(24.dp))
            Text("My Trips", style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Spacer(Modifier.height(4.dp))
            if (sorted.isEmpty()) {
                Text("No trips yet. Tap + to add your first one.",
                    modifier = Modifier.padding(horizontal = 16.dp))
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(sorted, key = { it.id }) { trip ->
                    TripCard(trip) { editing = trip }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (adding) {
        TripEditor(
            initial = null,
            onSave = { trips.add(it); persist(); adding = false },
            onDelete = null,
            onDismiss = { adding = false }
        )
    }
    editing?.let { current ->
        TripEditor(
            initial = current,
            onSave = { updated ->
                if (updated.photoPath != current.photoPath) TripStore.deletePhoto(current.photoPath)
                val i = trips.indexOfFirst { it.id == current.id }
                if (i >= 0) trips[i] = updated
                persist(); editing = null
            },
            onDelete = {
                TripStore.deletePhoto(current.photoPath)
                trips.removeAll { it.id == current.id }
                persist(); editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

fun cropRect(bw: Int, bh: Int, dw: Float, dh: Float, fx: Float, fy: Float, zoom: Float): Rect {
    val scale = maxOf(dw / bw, dh / bh) * zoom
    val sw = dw / scale
    val sh = dh / scale
    val left = (bw - sw).coerceAtLeast(0f) * fx.coerceIn(0f, 1f)
    val top = (bh - sh).coerceAtLeast(0f) * fy.coerceIn(0f, 1f)
    return Rect(left, top, left + sw, top + sh)
}

@Composable
fun TripPhoto(path: String?, modifier: Modifier, fx: Float = 0.5f, fy: Float = 0.5f, zoom: Float = 1f) {
    val bmp = remember(path) { path?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    if (bmp != null) {
        Canvas(modifier) {
            val r = cropRect(bmp.width, bmp.height, size.width, size.height, fx, fy, zoom)
            drawImage(
                bmp,
                srcOffset = IntOffset(r.left.toInt(), r.top.toInt()),
                srcSize = IntSize(r.width.toInt(), r.height.toInt()),
                dstSize = IntSize(size.width.toInt(), size.height.toInt())
            )
        }
    } else {
        Box(modifier.background(Color(0xFFCFD8DC)), contentAlignment = Alignment.Center) { Text("No photo") }
    }
}

private val textShadow = Shadow(color = Color.Black, offset = Offset(6f, 6f), blurRadius = 6f)

@Composable
fun ShadowText(text: String, style: TextStyle) {
    Box {
        repeat(2) { Text(text, color = Color.White, style = style.copy(shadow = textShadow)) }
    }
}

@Composable
fun TripCard(trip: Trip, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(12.dp), ambientColor = Color.Black, spotColor = Color.Black)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(200.dp)) {
            TripPhoto(trip.photoPath, Modifier.fillMaxSize(), trip.focusX, trip.focusY, trip.zoom)
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
            ) {
                ShadowText(trip.name, MaterialTheme.typography.titleLarge)
                ShadowText(dateRange(trip), MaterialTheme.typography.bodyLarge)
                ShadowText(countdown(trip), MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun TripEditor(
    initial: Trip?,
    onSave: (Trip) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var date by remember {
        mutableStateOf(initial?.let { LocalDate.ofEpochDay(it.epochDay) } ?: LocalDate.now().plusDays(30))
    }
    var endDate by remember {
        mutableStateOf(initial?.let { LocalDate.ofEpochDay(it.endEpochDay) } ?: LocalDate.now().plusDays(36))
    }
    var photo by remember { mutableStateOf(initial?.photoPath) }
    var fx by remember { mutableStateOf(initial?.focusX ?: 0.5f) }
    var fy by remember { mutableStateOf(initial?.focusY ?: 0.5f) }
    var zoom by remember { mutableStateOf(initial?.zoom ?: 1f) }
    val cardAspect = (LocalConfiguration.current.screenWidthDp - 32f) / 200f
    val dims = remember(photo) {
        photo?.let { p ->
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(p, o)
            Pair(o.outWidth, o.outHeight)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) TripStore.importPhoto(ctx, uri)?.let { newPath ->
            if (photo != null && photo != initial?.photoPath) TripStore.deletePhoto(photo)
            photo = newPath
            fx = 0.5f; fy = 0.5f; zoom = 1f
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New trip" else "Edit trip") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Trip name") }, singleLine = true)
                OutlinedButton(onClick = {
                    DatePickerDialog(ctx, { _, y, m, d ->
                        val len = ChronoUnit.DAYS.between(date, endDate)
                        date = LocalDate.of(y, m + 1, d)
                        endDate = date.plusDays(len)
                    }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                }) { Text("Starts: " + date.format(dateFmt)) }
                OutlinedButton(onClick = {
                    DatePickerDialog(ctx, { _, y, m, d -> endDate = LocalDate.of(y, m + 1, d) },
                        endDate.year, endDate.monthValue - 1, endDate.dayOfMonth).also {
                        it.datePicker.minDate = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }.show()
                }) { Text("Ends: " + endDate.format(dateFmt)) }
                val previewMod = Modifier.fillMaxWidth().aspectRatio(cardAspect).clip(RoundedCornerShape(8.dp))
                if (photo != null && dims != null && dims.first > 0 && dims.second > 0) {
                    TripPhoto(
                        photo,
                        previewMod.pointerInput(photo) {
                            detectTransformGestures { _, pan, gz, _ ->
                                val bw = dims.first.toFloat()
                                val bh = dims.second.toFloat()
                                zoom = (zoom * gz).coerceIn(1f, 5f)
                                val scale = maxOf(size.width / bw, size.height / bh) * zoom
                                val rx = bw - size.width / scale
                                val ry = bh - size.height / scale
                                if (rx > 0f) fx = (fx - pan.x / scale / rx).coerceIn(0f, 1f)
                                if (ry > 0f) fy = (fy - pan.y / scale / ry).coerceIn(0f, 1f)
                            }
                        },
                        fx, fy, zoom
                    )
                    Text("Drag to move the photo, pinch to zoom", style = MaterialTheme.typography.bodySmall)
                } else {
                    TripPhoto(photo, previewMod)
                }
                OutlinedButton(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text(if (photo == null) "Choose photo" else "Change photo") }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete trip", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(Trip(id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim(), epochDay = date.toEpochDay(), endEpochDay = endDate.toEpochDay(),
                        photoPath = photo, focusX = fx, focusY = fy, zoom = zoom))
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
