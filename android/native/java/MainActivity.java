package com.scan2com.app;

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
    private static final String SITE = "https://scan2com.com";

    @Override
    public void onCreate(Bundle b) {
        registerPlugin(ScanAutoPlugin.class);
        super.onCreate(b);
        AlarmService.channels(this);
        // los permisos (notificaciones, batería, autoinicio, pantalla completa) los pide la web con el asistente "Configura los avisos"
        openFrom(getIntent());
    }

    /** la app está en pantalla: los mensajes de chat no se muestran como notificación (la app ya los enseña) */
    public static volatile boolean visible = false;
    @Override public void onResume() { super.onResume(); visible = true; }
    @Override public void onPause() { visible = false; super.onPause(); }

    @Override
    protected void onNewIntent(Intent i) { super.onNewIntent(i); openFrom(i); }

    private void openFrom(Intent i) {
        if (i == null) return;
        String u = i.getStringExtra("url");
        // enlace https://scan2com.com/s/… abierto desde la cámara, WhatsApp, etc.
        if (u == null && i.getData() != null && String.valueOf(i.getData().getHost()).endsWith("scan2com.com")) u = i.getData().toString();
        if (u == null || getBridge() == null) return;
        // llamada entrante con el móvil bloqueado: la pantalla de la llamada se ve encima del bloqueo
        if (i.getBooleanExtra("call", false) && Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        final String full = u.startsWith("http") ? u : SITE + u;
        getBridge().getWebView().post(() -> getBridge().getWebView().loadUrl(full));
    }
}
