package io.ionic.starter;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(ScreenPinningPlugin.class);
        super.onCreate(savedInstanceState);

        configureWindowForAlarm();
        handleAlarmIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        configureWindowForAlarm();
        handleAlarmIntent(intent);
    }

    private void configureWindowForAlarm() {
        // Izinkan layar menyala dan tampil di atas lockscreen saat alarm berbunyi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) {
                km.requestDismissKeyguard(this, null);
            }
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /**
     * Sembunyikan bilah navigasi sistem (bottom bar) dan status bar (top bar dengan jam & baterai)
     * sehingga aplikasi masuk ke mode Immersive Fullscreen penuh.
     */
    public void hideSystemBars() {
        runOnUiThread(() -> {
            WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (controller != null) {
                // Sembunyikan status bar (atas) dan navigasi bar (bawah)
                controller.hide(WindowInsetsCompat.Type.systemBars());
                // Saat user swipe tepi layar, bilah hanya muncul transparan sesaat lalu hilang lagi
                controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                );
            }
        });
    }

    /**
     * Tampilkan kembali bilah sistem ke kondisi normal setelah alarm selesai dimatikan.
     */
    public void showSystemBars() {
        runOnUiThread(() -> {
            WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (controller != null) {
                controller.show(WindowInsetsCompat.Type.systemBars());
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Pastikan bar tetap tersembunyi saat aplikasi kembali fokus selama alarm aktif
        if (hasFocus && ScreenPinningPlugin.getIsAlarmActive(this)) {
            hideSystemBars();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // Mode Kiosk Mandiri (Tanpa ADB): Jika alarm sedang aktif dan user mencoba menekan
        // tombol Home atau Recent Apps, paksa aplikasi kembali tampil di depan seketika!
        if (ScreenPinningPlugin.getIsAlarmActive(this)) {
            Intent bringToFront = new Intent(this, MainActivity.class);
            bringToFront.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            );
            startActivity(bringToFront);
        }
    }

    @Override
    public void onBackPressed() {
        // Blokir tombol Back saat alarm sedang aktif agar pengguna tidak bisa keluar
        if (ScreenPinningPlugin.getIsAlarmActive(this)) {
            return;
        }
        super.onBackPressed();
    }

    private void handleAlarmIntent(Intent intent) {
        boolean isAlarm = (intent != null && intent.getBooleanExtra(ScreenPinningPlugin.EXTRA_ALARM_TRIGGERED, false))
                || ScreenPinningPlugin.getIsAlarmActive(this);
        if (isAlarm) {
            hideSystemBars();
            ScreenPinningPlugin.notifyAlarmTriggered(this);
        }
    }
}
