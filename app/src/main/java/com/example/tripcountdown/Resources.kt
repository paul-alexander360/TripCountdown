package com.example.tripcountdown

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

data class ResourceLink(
    val category: String,
    val text: String,
    val description: String,
    val url: String
)

/**
 * Every travel link is built here. To switch to affiliate links later, change the partner
 * addresses below (or wrap them in an affiliate address) and nothing else in the app changes.
 */
object ResourceLinks {
    fun destinationOf(trip: Trip): String = trip.destination.ifBlank { trip.name }.trim()

    private fun enc(s: String): String = Uri.encode(s)

    // --- Partner addresses: replace these with affiliate addresses later ---
    private fun flightsUrl(origin: String, dest: String, start: String, end: String): String {
        val from = if (origin.isNotBlank()) "from $origin " else ""
        return "https://www.google.com/travel/flights?q=" + enc("Flights " + from + "to " + dest + " on " + start + " through " + end)
    }

    private fun hotelsUrl(dest: String, start: String, end: String): String =
        "https://www.booking.com/searchresults.html?ss=" + enc(dest) + "&checkin=" + start + "&checkout=" + end

    private fun carsUrl(dest: String): String =
        "https://www.google.com/search?q=" + enc("rental cars " + dest)

    private fun insuranceUrl(dest: String): String =
        "https://www.google.com/search?q=" + enc("travel insurance for a trip to " + dest)

    private fun activitiesUrl(dest: String): String =
        "https://www.getyourguide.com/s/?q=" + enc(dest)
    // -----------------------------------------------------------------------

    fun build(ctx: Context, trip: Trip): List<ResourceLink> {
        val dest = destinationOf(trip)
        val origin = NotifSettings.getString(ctx, "originCity", "").trim()
        val start = LocalDate.ofEpochDay(trip.epochDay).toString()
        val end = LocalDate.ofEpochDay(trip.endEpochDay).toString()
        return listOf(
            ResourceLink(
                "Airfare",
                "Compare airfare to $dest",
                if (origin.isNotBlank()) "Flights from $origin, " + start + " to " + end else "Flights for " + start + " to " + end,
                flightsUrl(origin, dest, start, end)
            ),
            ResourceLink(
                "Hotels",
                "Hotel options around $dest",
                "Places to stay, " + start + " to " + end,
                hotelsUrl(dest, start, end)
            ),
            ResourceLink(
                "Rental cars",
                "Rental cars for $dest",
                "Compare car rental companies",
                carsUrl(dest)
            ),
            ResourceLink(
                "Travel insurance",
                "Travel insurance for your trip to $dest",
                "Cover cancellations, delays, and medical needs",
                insuranceUrl(dest)
            ),
            ResourceLink(
                "Activities",
                "Activities and tours in $dest",
                "Things to do while you are there",
                activitiesUrl(dest)
            )
        )
    }

    fun open(ctx: Context, url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
        }
    }
}

@Composable
fun ResourcesSummary(trip: Trip) {
    val ctx = LocalContext.current
    val links = ResourceLinks.build(ctx, trip)
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(Color(0xFFE0E6EB))
        )
        Text(
            "Travel resources",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)
        )
        links.forEach { link ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { ResourceLinks.open(ctx, link.url) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    link.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF1565C0),
                    modifier = Modifier.weight(1f)
                )
                Text("\u203A", style = MaterialTheme.typography.titleMedium, color = Color(0xFF78909C))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun ResourceCard(link: ResourceLink, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .cardShadow()
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            link.category.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF455A64)
        )
        Text(
            link.text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF1565C0)
        )
        Text(
            link.description,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF455A64)
        )
    }
}

@Composable
fun ResourcesScreen(trip: Trip, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val links = ResourceLinks.build(ctx, trip)
    val origin = NotifSettings.getString(ctx, "originCity", "").trim()

    Column(Modifier.fillMaxSize().background(Color(0xFFE8EEF4))) {
        AppBar(title = "Travel Resources", backLabel = "Back", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(trip.name, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Destination: " + ResourceLinks.destinationOf(trip),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF455A64)
                )
            }
            Text(
                if (origin.isNotBlank()) "Flight searches start from $origin. You can change this in Settings (the gear)."
                else "Tip: add your home city in Settings (the gear) to search flights from where you live.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF455A64)
            )
            links.forEach { link -> ResourceCard(link) { ResourceLinks.open(ctx, link.url) } }
            Text(
                "These links open travel websites in your browser.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF455A64)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
