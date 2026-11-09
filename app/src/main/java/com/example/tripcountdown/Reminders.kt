package com.example.tripcountdown

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object NotifSettings {
    private fun p(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun getBool(ctx: Context, key: String, def: Boolean): Boolean = p(ctx).getBoolean(key, def)
    fun setBool(ctx: Context, key: String, v: Boolean) { p(ctx).edit().putBoolean(key, v).apply() }
    fun getInt(ctx: Context, key: String, def: Int): Int = p(ctx).getInt(key, def)
    fun setInt(ctx: Context, key: String, v: Int) { p(ctx).edit().putInt(key, v).apply() }
}

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        try {
            Reminders.evaluate(applicationContext)
        } catch (e: Exception) {
        }
        return Result.success()
    }
}

object Reminders {
    private const val CHANNEL = "trip_reminders"

    fun canNotify(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun ensureChannel(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL, "Trip reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    /** Checks once a day, around 9 AM. */
    fun schedule(ctx: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(9, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis()
        val req = PeriodicWorkRequest.Builder(ReminderWorker::class.java, 24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork("trip_reminders", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun checkNow(ctx: Context) {
        WorkManager.getInstance(ctx).enqueue(OneTimeWorkRequest.Builder(ReminderWorker::class.java).build())
    }

    fun sendTest(ctx: Context) {
        ensureChannel(ctx)
        send(ctx, 1, "Travel Tracker", "Notifications are working!")
    }

    private fun send(ctx: Context, id: Int, title: String, text: String) {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (e: SecurityException) {
        }
    }

    private fun plural(n: Int, unit: String): String = "$n $unit" + if (n == 1) "" else "s"

    fun evaluate(ctx: Context) {
        if (!NotifSettings.getBool(ctx, "enabled", true)) return
        if (!canNotify(ctx)) return
        ensureChannel(ctx)

        val log = ctx.getSharedPreferences("reminder_log", Context.MODE_PRIVATE)
        val today = LocalDate.now().toEpochDay()
        val last = log.getLong("last", today - 1)
        val from = maxOf(minOf(last + 1, today), today - 3)
        val sent = (log.getStringSet("sent", emptySet()) ?: emptySet()).toMutableSet()

        val earlyMonths = NotifSettings.getInt(ctx, "earlyMonths", 1)
        val finalStep = NotifSettings.getInt(ctx, "finalStep", 7)
        val todoOn = NotifSettings.getBool(ctx, "todo", true)
        val fmt = DateTimeFormatter.ofPattern("MMM d")
        val trips = TripStore.load(ctx)

        for (day in from..today) {
            for (trip in trips) {
                val start = LocalDate.ofEpochDay(trip.epochDay)
                val daysUntil = (trip.epochDay - day).toInt()

                if (daysUntil > 0) {
                    var phrase: String? = null
                    if (earlyMonths > 0) {
                        for (k in 1..60) {
                            if (start.minusMonths(k.toLong()).toEpochDay() == day && k % earlyMonths == 0) {
                                phrase = plural(k, "month")
                                break
                            }
                        }
                    }
                    if (phrase == null && finalStep > 0 && daysUntil <= 31 && daysUntil % finalStep == 0) {
                        phrase = if (daysUntil % 7 == 0) plural(daysUntil / 7, "week") else plural(daysUntil, "day")
                    }
                    if (phrase != null) {
                        val key = "c|${trip.id}|$day"
                        if (sent.add(key)) {
                            send(ctx, key.hashCode(), trip.name, "$phrase to go \u00B7 ${dateRange(trip)}")
                        }
                    }
                }

                if (todoOn && trip.endEpochDay >= day) {
                    for (t in trip.todos) {
                        val due = t.dueEpochDay ?: continue
                        if (t.done) continue
                        val diff = (due - day).toInt()
                        val label = when (diff) {
                            7 -> "due in 1 week"
                            1 -> "due tomorrow"
                            else -> null
                        } ?: continue
                        val key = "t$diff|${t.id}|$due"
                        if (sent.add(key)) {
                            send(
                                ctx, key.hashCode(), "To-do $label: ${t.text}",
                                "${trip.name} \u00B7 ${LocalDate.ofEpochDay(due).format(fmt)}"
                            )
                        }
                    }
                }
            }
        }

        val cutoff = today - 90
        val kept = sent.filter { k -> (k.substringAfterLast('|').toLongOrNull() ?: 0L) >= cutoff }
        log.edit().putLong("last", today).putStringSet("sent", kept.toSet()).apply()
    }
}
