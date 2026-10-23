package io.ionic.starter;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
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

    private void handleAlarmIntent(Intent intent) {
        boolean isAlarm = (intent != null && intent.getBooleanExtra(ScreenPinningPlugin.EXTRA_ALARM_TRIGGERED, false))
                || ScreenPinningPlugin.getIsAlarmActive(this);
        if (isAlarm) {
            ScreenPinningPlugin.notifyAlarmTriggered(this);
        }
    }
}
