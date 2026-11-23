package com.scan2com.app;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import java.util.List;
import org.json.JSONObject;

/** Tras conectar con el coche: durante unos minutos mira la velocidad; a más de 20 km/h desactiva el código. */
public class DriveService extends Service implements LocationListener {
    private static final int NID = 5200;
    private String bt;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean done = false;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    @SuppressWarnings("MissingPermission")
    public int onStartCommand(Intent i, int flags, int id) {
        AlarmService.channels(this);
        Notification n = new NotificationCompat.Builder(this, AlarmService.CH_MSG).setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Scan2Com").setContentText("Comprobando si vas conduciendo…").setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true).build();
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION); else startForeground(NID, n);
        } catch (Exception e) { stopSelf(); return START_NOT_STICKY; }
        bt = i != null ? i.getStringExtra("bt") : null;
        if (bt == null || !Auto.hasLoc(this)) { stopSelf(); return START_NOT_STICKY; }
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 0, this, Looper.getMainLooper()); } catch (Exception ignored) {}
        h.postDelayed(this::stopSelf, 15 * 60 * 1000);  // como mucho 15 minutos
        return START_NOT_STICKY;
    }

    @Override
    public void onLocationChanged(@NonNull Location loc) {
        if (done || !loc.hasSpeed() || loc.getSpeed() < 5.6f) return;  // 5,6 m/s ≈ 20 km/h
        done = true;
        final List<JSONObject> codes = Auto.codesFor(this, bt, true);
        new Thread(() -> {
            StringBuilder names = new StringBuilder();
            for (JSONObject o : codes) if (Auto.setOn(getApplicationContext(), o.optString("id"), false)) names.append(names.length() > 0 ? ", " : "").append(o.optString("name", "Coche"));
            if (names.length() > 0) Auto.notify(getApplicationContext(), 5101, "Código desactivado: " + names, "Vas conduciendo. Se activará solo al bajar del coche.");
            h.post(this::stopSelf);
        }).start();
    }

    @Override
    public void onDestroy() {
        try { ((LocationManager) getSystemService(Context.LOCATION_SERVICE)).removeUpdates(this); } catch (Exception ignored) {}
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
