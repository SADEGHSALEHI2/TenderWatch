package ir.tenderwatch.sync

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ir.tenderwatch.R
import ir.tenderwatch.ui.MainActivity

object Notifier {
    private const val CHANNEL = "new_items"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "مناقصه و قرارداد جدید", NotificationManager.IMPORTANCE_DEFAULT)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyNew(context: Context, r: SyncResult) {
        val total = r.newTenders.size + r.newContracts.size
        if (total == 0) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val title = listOfNotNull(
            r.newTenders.size.takeIf { it > 0 }?.let { "$it مناقصه جدید" },
            r.newContracts.size.takeIf { it > 0 }?.let { "$it قرارداد جدید" },
        ).joinToString(" و ")
        val style = NotificationCompat.InboxStyle()
        (r.newTenders + r.newContracts).take(6).forEach { style.addLine(it.title) }

        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title)
            .setContentText((r.newTenders + r.newContracts).first().title)
            .setStyle(style)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(1, n)
        } catch (_: SecurityException) {
        }
    }
}
