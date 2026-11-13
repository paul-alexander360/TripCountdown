package com.example.tripcountdown

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val itDateFmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
private val itShortFmt = DateTimeFormatter.ofPattern("MMM d")
private val itShape = RoundedCornerShape(12.dp)

private fun dayLabel(trip: Trip, epochDay: Long): String? {
    val n = epochDay - trip.epochDay + 1
    return if (n >= 1 && n <= trip.endEpochDay - trip.epochDay + 1) "Day $n" else null
}

@Composable
fun ShadowField(label: String, value: String, onChange: (String) -> Unit, singleLine: Boolean) {
    TextField(
        value, onChange,
        label = { Text(label) },
        singleLine = singleLine,
        shape = itShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth().cardShadow()
    )
}

@Composable
fun ItineraryRow(item: ItineraryItem, trip: Trip, onClick: (() -> Unit)?, onDelete: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { m -> if (onClick != null) m.clickable(onClick = onClick) else m }
            .padding(
                start = 16.dp,
                end = if (onDelete != null) 4.dp else 16.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.width(72.dp)) {
            Text(
                LocalDate.ofEpochDay(item.epochDay).format(itShortFmt),
                style = MaterialTheme.typography.titleSmall
            )
            val day = dayLabel(trip, item.epochDay)
            if (day != null) {
                Text(day, style = MaterialTheme.typography.bodySmall, color = Color(0xFF455A64))
            }
        }
        Column(Modifier.weight(1f)) {
            if (item.place.isNotBlank()) {
                Text(
                    item.place,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
            if (item.activity.isNotBlank()) {
                Text(item.activity, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (onDelete != null) {
            Text(
                "\u2715",
                color = Color(0xFF78909C),
                modifier = Modifier.clickable { onDelete() }.padding(12.dp)
            )
        }
    }
}

@Composable
fun ItinerarySummary(trip: Trip) {
    val items = trip.itinerary.sortedBy { it.epochDay }
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(Color(0xFFE0E6EB))
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Itinerary", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (items.isNotEmpty()) {
                Text(
                    "${items.size} planned",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF455A64)
                )
            }
        }
        if (items.isEmpty()) {
            Text(
                "No plans yet. Tap Itinerary to add some.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF78909C),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            )
        }
        items.forEachIndexed { i, item ->
            if (i > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(1.dp)
                        .background(Color(0xFFE0E6EB))
                )
            }
            ItineraryRow(item, trip, null, null)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun ItineraryEditor(item: ItineraryItem, onSave: (ItineraryItem) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var date by remember { mutableStateOf(LocalDate.ofEpochDay(item.epochDay)) }
    var place by remember { mutableStateOf(item.place) }
    var activity by remember { mutableStateOf(item.activity) }

    AlertDialog(
        containerColor = Color(0xFFE8EEF4),
        onDismissRequest = onDismiss,
        title = { Text("Edit plan") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FieldButton(onClick = {
                    DatePickerDialog(
                        ctx,
                        { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                        date.year, date.monthValue - 1, date.dayOfMonth
                    ).show()
                }) { Text(date.format(itDateFmt)) }
                ShadowField("Where (up to 12 characters)", place, { place = it.take(12) }, true)
                ShadowField("Excursion or plans", activity, { activity = it }, false)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(item.copy(epochDay = date.toEpochDay(), place = place.trim(), activity = activity.trim()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ItineraryScreen(trip: Trip, onBack: () -> Unit, onChange: (List<ItineraryItem>) -> Unit) {
    val ctx = LocalContext.current
    val items = trip.itinerary
    val sorted = items.sortedBy { it.epochDay }
    var date by remember { mutableStateOf(LocalDate.ofEpochDay(trip.epochDay)) }
    var place by remember { mutableStateOf("") }
    var activity by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ItineraryItem?>(null) }

    fun add() {
        val p = place.trim()
        val a = activity.trim()
        if (p.isEmpty() && a.isEmpty()) return
        onChange(items + ItineraryItem(epochDay = date.toEpochDay(), place = p, activity = a))
        place = ""
        activity = ""
        if (date.toEpochDay() < trip.endEpochDay) date = date.plusDays(1)
    }

    val dayText = dayLabel(trip, date.toEpochDay())
    val dateText = "Date: " + date.format(itDateFmt) + (if (dayText != null) " ($dayText)" else "")

    Column(Modifier.fillMaxSize().background(Color(0xFFE8EEF4))) {
        AppBar(title = "Itinerary", backLabel = "Back", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Add the location you are or an activity you are doing and the day of the trip it will fall on. (Example: Cabo San Lucas, Beach Day)",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF455A64)
            )
            Column {
                Text(trip.name, style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (items.isEmpty()) "No plans yet" else "${items.size} planned",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF455A64)
                )
            }
            FieldButton(onClick = {
                DatePickerDialog(
                    ctx,
                    { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                    date.year, date.monthValue - 1, date.dayOfMonth
                ).show()
            }) { Text(dateText) }
            ShadowField("Where (up to 12 characters)", place, { place = it.take(12) }, true)
            ShadowField("Excursion or plans", activity, { activity = it }, false)
            FieldButton(onClick = { add() }) { Text("Add to itinerary") }

            if (sorted.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().cardShadow().background(Color.White)) {
                    sorted.forEachIndexed { i, item ->
                        if (i > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .height(1.dp)
                                    .background(Color(0xFFE0E6EB))
                            )
                        }
                        ItineraryRow(
                            item, trip,
                            onClick = { editing = item },
                            onDelete = { onChange(items.filter { it.id != item.id }) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    editing?.let { cur ->
        ItineraryEditor(
            cur,
            onSave = { upd ->
                onChange(items.map { if (it.id == cur.id) upd else it })
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}
