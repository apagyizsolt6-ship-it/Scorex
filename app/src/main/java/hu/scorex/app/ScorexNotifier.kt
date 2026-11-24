package hu.scorex.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object ScorexNotifier {

    private const val CHANNEL_ID = "scorex_live_alerts"
    private const val CHANNEL_NAME = "Scorex Élő Események"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Gól- és végeredmény-értesítések kedvenc csapatokhoz és tippekhez."
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun sendNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()

        manager.notify(notificationId, notification)
    }

    fun detectAndNotifyChanges(
        context: Context,
        preferences: SharedPreferences,
        previousMatches: List<ScorexMatch>,
        currentMatches: List<ScorexMatch>,
        savedTips: List<String>
    ) {
        if (previousMatches.isEmpty() || currentMatches.isEmpty()) return

        val notifyTeams = preferences.getBoolean("notify_favorite_teams", true)
        val notifyTips = preferences.getBoolean("notify_saved_tips", true)
        if (!notifyTeams && !notifyTips) return

        val favoriteTeams = preferences.getStringSet("favorite_teams", emptySet()).orEmpty()
        val prevById = previousMatches.associateBy { it.id }

        currentMatches.forEach { curr ->
            val prev = prevById[curr.id] ?: return@forEach

            val isFavTeamMatch = notifyTeams && (
                favoriteTeamKey(curr.homeTeam) in favoriteTeams ||
                    favoriteTeamKey(curr.awayTeam) in favoriteTeams
                )

            val prefix = "${curr.homeTeam} – ${curr.awayTeam}: "
            val isTipMatch = notifyTips && savedTips.any { it.startsWith(prefix, ignoreCase = true) }

            if (!isFavTeamMatch && !isTipMatch) return@forEach

            val scoreChanged = (prev.homeScore != curr.homeScore || prev.awayScore != curr.awayScore) &&
                curr.homeScore != "-" && curr.awayScore != "-"
            val justFinished = !isMatchFinished(prev) && isMatchFinished(curr)

            if (scoreChanged) {
                val tag = if (isTipMatch) "TIPP • GÓL" else "KEDVENC • GÓL"
                sendNotification(
                    context = context,
                    notificationId = (curr.id + "_goal_${curr.homeScore}_${curr.awayScore}").hashCode(),
                    title = "$tag (${curr.matchTime})",
                    body = "${curr.homeTeam} ${curr.homeScore} : ${curr.awayScore} ${curr.awayTeam}"
                )
            } else if (justFinished) {
                val tag = if (isTipMatch) "TIPP • VÉGE" else "KEDVENC • VÉGE"
                sendNotification(
                    context = context,
                    notificationId = (curr.id + "_ft").hashCode(),
                    title = "$tag (${curr.matchTime})",
                    body = "${curr.homeTeam} ${curr.homeScore} : ${curr.awayScore} ${curr.awayTeam}"
                )
            }
        }
    }
}
