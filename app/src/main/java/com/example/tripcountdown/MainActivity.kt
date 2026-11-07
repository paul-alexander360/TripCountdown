package com.example.tripcountdown

import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

private fun countdown(epochDay: Long): String {
    val d = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.ofEpochDay(epochDay))
    return when {
        d > 1 -> "$d days to go"
        d == 1L -> "Tomorrow!"
        d == 0L -> "Today!"
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
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) { Text("+", style = MaterialTheme.typography.headlineMedium) }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(24.dp))
            Text("My Trips", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))
            if (sorted.isEmpty()) {
                Text("No trips yet. Tap + to add your first one.")
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

@Composable
fun TripPhoto(path: String?, modifier: Modifier) {
    val bmp = remember(path) { path?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    if (bmp != null) {
        Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier.background(Color(0xFFCFD8DC)), contentAlignment = Alignment.Center) { Text("No photo") }
    }
}

@Composable
fun TripCard(trip: Trip, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            TripPhoto(trip.photoPath, Modifier.fillMaxWidth().height(160.dp))
            Column(Modifier.padding(12.dp)) {
                Text(trip.name, style = MaterialTheme.typography.titleLarge)
                Text(LocalDate.ofEpochDay(trip.epochDay).format(dateFmt))
                Text(countdown(trip.epochDay), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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
    var photo by remember { mutableStateOf(initial?.photoPath) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) TripStore.importPhoto(ctx, uri)?.let { newPath ->
            if (photo != null && photo != initial?.photoPath) TripStore.deletePhoto(photo)
            photo = newPath
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New trip" else "Edit trip") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Trip name") }, singleLine = true)
                OutlinedButton(onClick = {
                    DatePickerDialog(ctx, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                        date.year, date.monthValue - 1, date.dayOfMonth).show()
                }) { Text(date.format(dateFmt)) }
                TripPhoto(photo, Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(8.dp)))
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
                onClick = { onSave(Trip(id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name.trim(), epochDay = date.toEpochDay(), photoPath = photo)) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
