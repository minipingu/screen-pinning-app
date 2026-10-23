package io.ionic.starter;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;
import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "ScreenPinning")
public class ScreenPinningPlugin extends Plugin {
    private static final String TAG = "ScreenPinningPlugin";
    public static final String EXTRA_ALARM_TRIGGERED = "io.ionic.starter.EXTRA_ALARM_TRIGGERED";
    private static final String PREFS_NAME = "math_alarm_prefs";
    private static final String KEY_IS_ALARM_ACTIVE = "is_alarm_active";

    private static ScreenPinningPlugin instance;
    private static MediaPlayer mediaPlayer;
    private static Vibrator vibrator;

    @Override
    public void load() {
        super.load();
        instance = this;
    }

    public static synchronized void setAlarmActive(Context context, boolean active) {
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_IS_ALARM_ACTIVE, active).apply();
        }
    }

    public static synchronized boolean getIsAlarmActive(Context context) {
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getBoolean(KEY_IS_ALARM_ACTIVE, false);
        }
        return false;
    }

    public static synchronized void notifyAlarmTriggered(Activity activity) {
        if (activity != null) {
            setAlarmActive(activity, true);
        }
        if (instance != null) {
            JSObject data = new JSObject();
            data.put("isAlarmActive", true);
            instance.notifyListeners("onAlarmTriggered", data, true);
        }
    }

    public static synchronized void startAlarmSound(Context context) {
        try {
            if (mediaPlayer == null) {
                Uri alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alertUri == null) {
                    alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                }
                if (alertUri == null) {
                    alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                }

                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(context, alertUri);
                mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                );
                mediaPlayer.setLooping(true);
                mediaPlayer.prepare();
                mediaPlayer.start();
            }

            if (vibrator == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                    if (vm != null) {
                        vibrator = vm.getDefaultVibrator();
                    }
                } else {
                    vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                }
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 800, 400, 800, 400};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Gagal memutar alarm suara: " + e.getMessage(), e);
        }
    }

    public static synchronized void stopAlarmSoundInternal(Context context) {
        setAlarmActive(context, false);
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            }
            if (vibrator != null) {
                vibrator.cancel();
                vibrator = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error menghentikan suara alarm: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void checkAlarmState(PluginCall call) {
        boolean active = getIsAlarmActive(getContext());
        JSObject ret = new JSObject();
        ret.put("isAlarmActive", active);
        call.resolve(ret);
    }

    @PluginMethod
    public void checkPermissions(PluginCall call) {
        Context context = getContext();
        boolean pinningEnabled = false;
        try {
            int val = Settings.System.getInt(context.getContentResolver(), "lock_to_app_enabled", 0);
            pinningEnabled = (val == 1);
        } catch (Exception ignored) {}

        boolean canFullScreen = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                canFullScreen = nm.canUseFullScreenIntent();
            }
        }

        boolean canOverlay = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            canOverlay = Settings.canDrawOverlays(context);
        }

        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        boolean isDeviceOwner = dpm != null && dpm.isDeviceOwnerApp(context.getPackageName());
        boolean isLockTaskPermitted = dpm != null && dpm.isLockTaskPermitted(context.getPackageName());

        JSObject ret = new JSObject();
        ret.put("pinningEnabled", pinningEnabled);
        ret.put("canFullScreen", canFullScreen);
        ret.put("canOverlay", canOverlay);
        ret.put("isDeviceOwner", isDeviceOwner);
        ret.put("isLockTaskPermitted", isLockTaskPermitted);
        call.resolve(ret);
    }

    @PluginMethod
    public void requestFullScreenPermission(PluginCall call) {
        Context context = getContext();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } else {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Gagal membuka setting full screen: " + e.getMessage());
        }
    }

    @PluginMethod
    public void requestOverlayPermission(PluginCall call) {
        Context context = getContext();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Gagal membuka setting overlay: " + e.getMessage());
        }
    }

    @PluginMethod
    public void setAlarm(PluginCall call) {
        Long timestamp = call.getLong("timestamp");
        if (timestamp == null) {
            Integer delaySeconds = call.getInt("delaySeconds");
            if (delaySeconds != null) {
                timestamp = System.currentTimeMillis() + (delaySeconds * 1000L);
            } else {
                call.reject("Parameter timestamp atau delaySeconds dibutuhkan");
                return;
            }
        }

        try {
            Context context = getContext();
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Intent permIntent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    permIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(permIntent);
                    call.reject("Izin SCHEDULE_EXACT_ALARM diperlukan");
                    return;
                }
            }

            // 1. showPendingIntent (Membuka Activity saat alarm clock diklik / berbunyi)
            Intent showIntent = new Intent(context, MainActivity.class);
            showIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            );
            showIntent.putExtra(EXTRA_ALARM_TRIGGERED, true);

            int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
            }

            Bundle optionsBundle = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions actOpts = ActivityOptions.makeBasic();
                actOpts.setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                optionsBundle = actOpts.toBundle();
            }

            PendingIntent showPendingIntent = PendingIntent.getActivity(context, 102, showIntent, pendingFlags, optionsBundle);

            // 2. receiverPendingIntent (Memicu AlarmReceiver untuk suara dering & notifikasi)
            Intent receiverIntent = new Intent(context, AlarmReceiver.class);
            PendingIntent receiverPendingIntent = PendingIntent.getBroadcast(context, 101, receiverIntent, pendingFlags);

            // Gunakan setAlarmClock untuk mendapatkan pengecualian resmi Android Background Activity Launch (BAL)
            AlarmManager.AlarmClockInfo clockInfo = new AlarmManager.AlarmClockInfo(timestamp, showPendingIntent);
            alarmManager.setAlarmClock(clockInfo, receiverPendingIntent);

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("alarmTime", timestamp);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(TAG, "Gagal menyetel alarm: " + e.getMessage(), e);
            call.reject("Gagal menyetel alarm: " + e.getMessage());
        }
    }

    @PluginMethod
    public void cancelAlarm(PluginCall call) {
        try {
            Context context = getContext();
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
            }

            Intent receiverIntent = new Intent(context, AlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 101, receiverIntent, pendingFlags);

            Intent showIntent = new Intent(context, MainActivity.class);
            PendingIntent showPendingIntent = PendingIntent.getActivity(context, 102, showIntent, pendingFlags);

            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent);
                alarmManager.cancel(showPendingIntent);
            }
            stopAlarmSoundInternal(context);
            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Gagal membatalkan alarm: " + e.getMessage());
        }
    }

    @PluginMethod
    public void startPinning(PluginCall call) {
        Activity activity = getActivity();
        if (activity == null) {
            call.reject("Activity tidak ditemukan");
            return;
        }

        activity.runOnUiThread(() -> {
            try {
                // Sembunyikan navigasi bawah dan topbar (jam & baterai)
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).hideSystemBars();
                }

                // Pastikan didaftarkan ke LockTask jika device owner
                AdminReceiver.whitelistLockTask(activity);

                activity.startLockTask();
                DevicePolicyManager dpm = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
                boolean isLockTaskPermitted = dpm != null && dpm.isLockTaskPermitted(activity.getPackageName());

                JSObject ret = new JSObject();
                ret.put("success", true);
                ret.put("isDirectLockTask", isLockTaskPermitted);
                call.resolve(ret);
            } catch (Exception e) {
                Log.e(TAG, "Gagal startLockTask: " + e.getMessage(), e);
                call.reject("Gagal mengaktifkan mode pinning: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void enableKioskMode(PluginCall call) {
        boolean ok = AdminReceiver.whitelistLockTask(getContext());
        JSObject ret = new JSObject();
        ret.put("success", ok);
        call.resolve(ret);
    }

    @PluginMethod
    public void stopPinning(PluginCall call) {
        Activity activity = getActivity();
        if (activity == null) {
            call.reject("Activity tidak ditemukan");
            return;
        }

        activity.runOnUiThread(() -> {
            try {
                activity.stopLockTask();
            } catch (Exception e) {
                Log.w(TAG, "Peringatan stopLockTask: " + e.getMessage());
            }

            // Kembalikan status bar dan navigation bar
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).showSystemBars();
            }

            stopAlarmSoundInternal(activity);
            JSObject ret = new JSObject();
            ret.put("success", true);
            call.resolve(ret);
        });
    }

    @PluginMethod
    public void isPinningEnabled(PluginCall call) {
        boolean enabled = false;
        try {
            int val = Settings.System.getInt(getContext().getContentResolver(), "lock_to_app_enabled", 0);
            enabled = (val == 1);
        } catch (Exception e) {
            Log.e(TAG, "Error memeriksa lock_to_app_enabled: " + e.getMessage());
        }
        JSObject ret = new JSObject();
        ret.put("enabled", enabled);
        call.resolve(ret);
    }

    @PluginMethod
    public void stopAlarm(PluginCall call) {
        stopAlarmSoundInternal(getContext());
        Activity activity = getActivity();
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).showSystemBars();
        }
        JSObject ret = new JSObject();
        ret.put("success", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void openScreenPinningSettings(PluginCall call) {
        Context context = getContext();

        Intent[] targets = new Intent[]{
            // 1. Samsung "More security settings" / "Other security settings"
            new Intent().setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$OtherSecuritySettingsActivity")),
            new Intent().setComponent(new ComponentName("com.android.settings", "com.samsung.android.settings.OtherSecuritySettingsActivity")),
            new Intent().setComponent(new ComponentName("com.android.settings", "com.samsung.android.settings.securesettings.SecOtherSecuritySettings")),
            // 2. Generic Screen Pinning Activity
            new Intent().setComponent(new ComponentName("com.android.settings", "com.android.settings.Settings$ScreenPinningSettingsActivity")),
            // 3. Action dengan fragment extra
            new Intent(Settings.ACTION_SECURITY_SETTINGS)
                .putExtra(":settings:show_fragment", "com.android.settings.security.screenlock.ScreenPinningSettings")
                .putExtra(":settings:fragment_args_key", "screen_pinning_settings"),
            // 4. Fallback standard Security Settings
            new Intent(Settings.ACTION_SECURITY_SETTINGS),
            // 5. Ultimate fallback
            new Intent(Settings.ACTION_SETTINGS)
        };

        boolean opened = false;
        for (Intent intent : targets) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                opened = true;
                break;
            } catch (Exception ignored) {
                // Lanjut ke intent berikutnya jika komponen internal tidak diizinkan oleh OEM
            }
        }

        JSObject ret = new JSObject();
        ret.put("success", opened);
        call.resolve(ret);
    }
}
