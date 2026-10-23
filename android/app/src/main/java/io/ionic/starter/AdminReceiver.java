package io.ionic.starter;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AdminReceiver extends DeviceAdminReceiver {
    private static final String TAG = "AdminReceiver";

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.d(TAG, "Device Admin berhasil diaktifkan!");
        whitelistLockTask(context);
    }

    public static boolean whitelistLockTask(Context context) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName adminComponent = new ComponentName(context, AdminReceiver.class);
            if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                dpm.setLockTaskPackages(adminComponent, new String[]{context.getPackageName()});
                Log.d(TAG, "Paket " + context.getPackageName() + " berhasil didaftarkan ke LockTask allowlist (mode tanpa dialog)!");
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Gagal whitelistLockTask: " + e.getMessage(), e);
        }
        return false;
    }
}
