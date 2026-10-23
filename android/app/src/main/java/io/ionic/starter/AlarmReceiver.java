package io.ionic.starter;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import androidx.core.app.NotificationCompat;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmReceiver";
    private static final String CHANNEL_ID = "alarm_math_channel";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "AlarmReceiver dipicu! Menyalakan audio dan menampilkan layar tantangan alarm.");

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;
        if (pm != null) {
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                "ScreenPinningApp:AlarmWakeLock"
            );
            wakeLock.acquire(10 * 60 * 1000L); // 10 menit batas aman
        }

        // Tandai status alarm aktif dan mulai audio dering/getaran
        ScreenPinningPlugin.setAlarmActive(context, true);
        ScreenPinningPlugin.startAlarmSound(context);

        // Buat Intent untuk membuka MainActivity
        Intent launchIntent = new Intent(context, MainActivity.class);
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK |
            Intent.FLAG_ACTIVITY_CLEAR_TOP |
            Intent.FLAG_ACTIVITY_SINGLE_TOP |
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        );
        launchIntent.putExtra(ScreenPinningPlugin.EXTRA_ALARM_TRIGGERED, true);

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        Bundle optionsBundle = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions actOptions = ActivityOptions.makeBasic();
            actOptions.setPendingIntentCreatorBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            );
            optionsBundle = actOptions.toBundle();
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 1001, launchIntent, pendingFlags, optionsBundle
        );

        // Buat dan tampilkan Full-Screen Notification (wajib di Android saat aplikasi tertutup / layar terkunci)
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (soundUri == null) {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            }
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Math Pinning Alarm",
                    NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Saluran notifikasi alarm matematika");
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[]{0, 500, 200, 500});
                channel.setSound(soundUri, audioAttributes);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                channel.setBypassDnd(true);
                nm.createNotificationChannel(channel);
            }

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ Alarm Matematika Berbunyi!")
                .setContentText("Jawab soal untuk membuka dan mematikan alarm.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setAutoCancel(false);

            nm.notify(NOTIFICATION_ID, builder.build());
        }

        // Buka activity langsung
        try {
            if (optionsBundle != null) {
                context.startActivity(launchIntent, optionsBundle);
            } else {
                context.startActivity(launchIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Gagal memulai activity langsung dari receiver: " + e.getMessage(), e);
        }

        // Kirim pendingIntent dengan opsi BAL untuk Android 14+ sebagai jaminan kedua
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions sendOptions = ActivityOptions.makeBasic();
                sendOptions.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                pendingIntent.send(context, 0, null, null, null, null, sendOptions.toBundle());
            } else {
                pendingIntent.send();
            }
        } catch (Exception e) {
            Log.w(TAG, "Catatan pendingIntent.send: " + e.getMessage());
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}
