package com.scanand.pro;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final String SITE = "https://scan.originalbim.com";

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        AlarmService.channels(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        // Android 14+: permiso para mostrar la sirena a pantalla completa
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (!nm.canUseFullScreenIntent()) {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) {}
            }
        }
        openFrom(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) { super.onNewIntent(i); openFrom(i); }

    private void openFrom(Intent i) {
        if (i == null) return;
        String u = i.getStringExtra("url");
        if (u == null || getBridge() == null) return;
        final String full = u.startsWith("http") ? u : SITE + u;
        getBridge().getWebView().post(() -> getBridge().getWebView().loadUrl(full));
    }
}
