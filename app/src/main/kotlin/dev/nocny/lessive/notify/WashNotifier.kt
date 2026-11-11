package dev.nocny.lessive.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.nocny.lessive.MainActivity
import dev.nocny.lessive.R
import dev.nocny.lessive.ui.components.formatHour

/**
 * The machine does not report its progress. Phases are estimated from the program's
 * duration, split 5/3/2 between washing, rinsing and spinning.
 */
object WashNotifier {
    private const val RUNNING = "running"
    private const val DONE = "done"
    private const val ID = 1
    private const val SNOOZE_MINUTES = 15

    private val phases = listOf(R.string.phase_wash to 5, R.string.phase_rinse to 3, R.string.phase_spin to 2)

    fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(RUNNING, context.getString(R.string.channel_running), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(DONE, context.getString(R.string.channel_done), NotificationManager.IMPORTANCE_HIGH),
            )
        )
    }

    fun started(context: Context, title: String, startAt: Long, endsAt: Long) {
        val wash = Wash(title, startAt, endsAt)
        val alarms = context.getSystemService(AlarmManager::class.java)
        (10..13).forEach { alarms.cancel(broadcast(context, wash.intent(context, WashReceiver.TICK), it)) }
        var at = startAt
        val ticks = buildList {
            if (startAt > System.currentTimeMillis()) add(startAt)
            phases.dropLast(1).forEach { (_, weight) ->
                at += (endsAt - startAt) * weight / 10
                add(at)
            }
        }
        ticks.forEachIndexed { i, t -> schedule(context, t, wash.intent(context, WashReceiver.TICK), requestCode = 10 + i) }
        schedule(context, endsAt, wash.intent(context, WashReceiver.FINISH), requestCode = 20)
        showRunning(context, wash)
    }

    internal fun showRunning(context: Context, wash: Wash, now: Long = System.currentTimeMillis()) {
        val total = (wash.endsAt - wash.startAt).coerceAtLeast(1)
        val elapsed = (now - wash.startAt).coerceIn(0, total)
        val waiting = now < wash.startAt
        val phase = phases.runningFold(0L) { acc, (_, w) -> acc + total * w / 10 }
            .drop(1)
            .indexOfFirst { elapsed < it }
            .let { if (it < 0) phases.lastIndex else it }

        val builder = Notification.Builder(context, RUNNING)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(wash.title)
            .setContentText(
                if (waiting) context.getString(R.string.notif_scheduled_text, context.formatHour(wash.startAt, now), context.formatHour(wash.endsAt, now))
                else context.getString(R.string.notif_running_text, context.getString(phases[phase].first), context.formatHour(wash.endsAt, now))
            )
            .setSubText(context.getString(if (waiting) R.string.notif_scheduled else R.string.notif_estimate))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(openApp(context))
            .setWhen(wash.endsAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)

        if (!waiting) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                builder.setStyle(
                    Notification.ProgressStyle()
                        .setProgressSegments(phases.map { (_, w) -> Notification.ProgressStyle.Segment((total / 60_000 * w / 10).toInt().coerceAtLeast(1)) })
                        .setProgress((elapsed / 60_000).toInt())
                )
            } else {
                builder.setProgress(total.toInt() / 60_000, elapsed.toInt() / 60_000, false)
            }
        }
        manager(context).notify(ID, builder.build())
    }

    internal fun showDone(context: Context, wash: Wash) {
        val notification = Notification.Builder(context, DONE)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.notif_done))
            .setContentText(wash.title)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.snooze, SNOOZE_MINUTES),
                    broadcast(context, wash.intent(context, WashReceiver.SNOOZE), 30),
                ).build()
            )
            .addAction(Notification.Action.Builder(null, context.getString(R.string.action_ok), broadcast(context, Intent(context, WashReceiver::class.java).setAction(WashReceiver.DISMISS), 31)).build())
            .build()
        manager(context).notify(ID, notification)
    }

    internal fun snooze(context: Context, wash: Wash) {
        manager(context).cancel(ID)
        schedule(context, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L, wash.intent(context, WashReceiver.FINISH), requestCode = 20)
    }

    internal fun dismiss(context: Context) = manager(context).cancel(ID)

    // Exact alarms need an extra permission. A few minutes of delay is acceptable here.
    private fun schedule(context: Context, at: Long, intent: Intent, requestCode: Int) {
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, broadcast(context, intent, requestCode))
    }

    private fun broadcast(context: Context, intent: Intent, requestCode: Int) =
        PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
}

internal data class Wash(val title: String, val startAt: Long, val endsAt: Long) {
    fun intent(context: Context, action: String): Intent = Intent(context, WashReceiver::class.java)
        .setAction(action)
        .putExtra("title", title)
        .putExtra("startAt", startAt)
        .putExtra("endsAt", endsAt)

    companion object {
        fun from(intent: Intent) = intent.getStringExtra("title")?.let {
            Wash(it, intent.getLongExtra("startAt", 0), intent.getLongExtra("endsAt", 0))
        }
    }
}

class WashReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DISMISS) return WashNotifier.dismiss(context)
        val wash = Wash.from(intent) ?: return
        when (intent.action) {
            TICK -> WashNotifier.showRunning(context, wash)
            FINISH -> WashNotifier.showDone(context, wash)
            SNOOZE -> WashNotifier.snooze(context, wash)
        }
    }

    companion object {
        const val TICK = "dev.nocny.lessive.TICK"
        const val FINISH = "dev.nocny.lessive.FINISH"
        const val SNOOZE = "dev.nocny.lessive.SNOOZE"
        const val DISMISS = "dev.nocny.lessive.DISMISS"
    }
}
