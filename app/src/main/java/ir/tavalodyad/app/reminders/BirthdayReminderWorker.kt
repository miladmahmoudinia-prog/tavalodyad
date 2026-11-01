package ir.tavalodyad.app.reminders

import android.Manifest
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
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ir.tavalodyad.app.MainActivity
import ir.tavalodyad.app.data.AppDatabase
import ir.tavalodyad.app.util.PersianCalendarUtils
import ir.tavalodyad.app.util.computeBirthdayInfo
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BirthdayReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return reminderLock.withLock {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return Result.success()
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "یادآوری تولد", NotificationManager.IMPORTANCE_DEFAULT)
            applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val today = PersianCalendarUtils.getTodayJalali()
        val currentDate = LocalDate.now().toString()
        val preferences = applicationContext.getSharedPreferences("birthday_reminders", Context.MODE_PRIVATE)
        val people = AppDatabase.getInstance(applicationContext).personDao().getAll()
        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        for (person in people) {
            val days = computeBirthdayInfo(person, today).daysLeft
            if (days != 0L && days != 1L) continue
            val key = "${person.id}:$days"
            if (preferences.getString(key, null) == currentDate) continue
            val message = if (days == 0L) "امروز تولد ${person.name} است 🎉" else "فردا تولد ${person.name} است 🎂"
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("تولدیاد")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            try {
                manager.notify("birthday:${person.id}:$days", 0, notification)
                preferences.edit().putString(key, currentDate).apply()
            } catch (exception: SecurityException) {
                return Result.success()
            }
        }
        Result.success()
        }
    }

    companion object {
        private const val CHANNEL = "birthday_reminders"
        private val reminderLock = Mutex()
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BirthdayReminderWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("birthday_reminders", ExistingPeriodicWorkPolicy.KEEP, request)
        }
        fun checkNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("birthday_reminders_now", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<BirthdayReminderWorker>().build())
        }
    }
}
