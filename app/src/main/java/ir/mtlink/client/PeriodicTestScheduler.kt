package ir.mtlink.client

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object PeriodicTestScheduler {
    private const val REQUEST_CODE = 9417

    fun apply(context: Context, prefs: AppPreferences) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context)
        alarm.cancel(pending)
        if (!prefs.periodicTestEnabled) return
        val interval = prefs.periodicTestMinutes.coerceAtLeast(15) * 60_000L
        alarm.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + interval, interval, pending)
    }

    fun restoreIfEnabled(context: Context) = apply(context, MTLinkStore(context).appPreferences())

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, PeriodicTestReceiver::class.java).setAction("ir.mtlink.client.PERIODIC_TEST"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
