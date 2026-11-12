package com.example.tripcountdown

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
 *
 * Partners: Skyscanner (flights), Booking.com (hotels), Discover Cars (rental cars),
 * Viator (activities), World Nomads (travel insurance).
 */
object ResourceLinks {
    fun destinationOf(trip: Trip): String = trip.destination.ifBlank { trip.name }.trim()

    private fun enc(s: String): String = Uri.encode(s)

    // --- Partner addresses: replace these with affiliate addresses later ---
    // Skyscanner needs airport codes in its address, so for now this opens its flights page.
    private fun flightsUrl(): String = "https://www.skyscanner.com/flights"

    private fun hotelsUrl(dest: String, start: String, end: String): String =
        "https://www.booking.com/searchresults.html?ss=" + enc(dest) + "&checkin=" + start + "&checkout=" + end

    private fun carsUrl(): String = "https://www.discovercars.com/"

    private fun insuranceUrl(): String = "https://www.worldnomads.com/travel-insurance"

    private fun activitiesUrl(dest: String): String =
        "https://www.viator.com/searchResults/all?text=" + enc(dest)
    // -----------------------------------------------------------------------

    /** Order matters: 0 flights, 1 hotels, 2 rental cars, 3 insurance, 4 activities. */
    fun build(ctx: Context, trip: Trip): List<ResourceLink> {
        val dest = destinationOf(trip)
        val origin = NotifSettings.getString(ctx, "originCity", "").trim()
        val start = LocalDate.ofEpochDay(trip.epochDay).toString()
        val end = LocalDate.ofEpochDay(trip.endEpochDay).toString()
        val route = if (origin.isNotBlank()) "from $origin to $dest" else "to $dest"
        return listOf(
            ResourceLink(
                "Airfare",
                "Compare airfare to $dest",
                "Opens Skyscanner. Search flights $route, $start to $end",
                flightsUrl()
            ),
            ResourceLink(
                "Hotels",
                "Hotel options around $dest",
                "Opens Booking.com for $start to $end",
                hotelsUrl(dest, start, end)
            ),
            ResourceLink(
                "Rental cars",
                "Rental cars for $dest",
                "Opens Discover Cars to compare rental companies",
                carsUrl()
            ),
            ResourceLink(
                "Travel insurance",
                "Travel insurance for your trip to $dest",
                "Opens World Nomads for trip cover",
                insuranceUrl()
            ),
            ResourceLink(
                "Activities",
                "Activities and tours in $dest",
                "Opens Viator with things to do in $dest",
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

/** The section on the trip details page: a bar of four links with a drop shadow. */
@Composable
fun ResourcesSummary(trip: Trip) {
    val ctx = LocalContext.current
    val links = ResourceLinks.build(ctx, trip)
    val bar = listOf(
        "Flights" to links[0],
        "Hotels" to links[1],
        "Cars" to links[2],
        "Activities" to links[4]
    )
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
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp)
        )
        Text(
            "Plan your trip to " + ResourceLinks.destinationOf(trip),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF455A64),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
        )
        Row(
            Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
                .fillMaxWidth()
                .height(48.dp)
                .shadow(8.dp, RectangleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .background(Color.White)
                .border(1.dp, Color.Black)
        ) {
            bar.forEachIndexed { i, pair ->
                if (i > 0) {
                    Box(Modifier.width(1.dp).fillMaxHeight().background(Color.Black))
                }
                TextButton(
                    onClick = { ResourceLinks.open(ctx, pair.second.url) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RectangleShape,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF1565C0))
                ) { Text(pair.first, maxLines = 1) }
            }
        }
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
