package com.andrerinas.openheadunit.main

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.aap.AapProjectionActivity
import com.andrerinas.openheadunit.aap.protocol.proto.MediaPlayback
import com.andrerinas.openheadunit.contract.MediaKeyIntent
import com.andrerinas.openheadunit.utils.protoUint32ToLong

class BackgroundNotification(private val context: Context) {

    companion object {
        private const val NOTIFICATION_MEDIA = 2
        const val mediaChannel = "media_v2"
    }

    fun notify(
        metadata: MediaPlayback.MediaMetaData,
        playbackSeconds: Long = 0L,
        isPlaying: Boolean = false,
        albumArtBitmap: Bitmap? = null
    ) {

        val playPauseKey = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        val nextKey = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
        val prevKey = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        val broadcastFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

        val playPause = PendingIntent.getBroadcast(context, 1, MediaKeyIntent(playPauseKey), broadcastFlags)
        val next = PendingIntent.getBroadcast(context, 2, MediaKeyIntent(nextKey), broadcastFlags)
        val prev = PendingIntent.getBroadcast(context, 3, MediaKeyIntent(prevKey), broadcastFlags)
        val durationSeconds = if (metadata.hasDurationSeconds()) metadata.durationSeconds.protoUint32ToLong() else 0L
        val clampedPlayback = playbackSeconds.coerceAtLeast(0L).coerceAtMost(durationSeconds.takeIf { it > 0 } ?: playbackSeconds.coerceAtLeast(0L))
        val progressText = if (durationSeconds > 0) {
            "${formatAsMmSs(clampedPlayback)} / ${formatAsMmSs(durationSeconds)}"
        } else {
            formatAsMmSs(clampedPlayback)
        }

        val notification = NotificationCompat.Builder(context, mediaChannel)
                .setContentTitle(metadata.song)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentText(metadata.artist)
                .setSubText(progressText)
                .setSmallIcon(R.drawable.ic_stat_aa)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(PendingIntent.getActivity(context, 0, AapProjectionActivity.intent(context),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT))
                .addAction(R.drawable.ic_skip_previous_black_24dp, context.getString(R.string.media_action_previous), prev)
                .addAction(
                    R.drawable.ic_play_arrow_black_24dp,
                    context.getString(if (isPlaying) R.string.media_action_pause else R.string.media_action_play),
                    playPause
                )
                .addAction(R.drawable.ic_skip_next_black_24dp, context.getString(R.string.media_action_next), next)


        if (albumArtBitmap != null) {
            notification
                .setStyle(NotificationCompat.BigPictureStyle().bigPicture(albumArtBitmap))
                .setLargeIcon(albumArtBitmap)
        }
        App.provide(context).notificationManager.notify(NOTIFICATION_MEDIA, notification.build())
    }

    fun cancel() {
        App.provide(context).notificationManager.cancel(NOTIFICATION_MEDIA)
    }

    private fun formatAsMmSs(totalSeconds: Long): String {
        val clamped = totalSeconds.coerceAtLeast(0L)
        val minutes = clamped / 60
        val seconds = clamped % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

}
