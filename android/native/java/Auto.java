package com.scan2com.app;

import android.Manifest;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Utilidades del modo "activar al bajar del coche": configuración, ubicación, llamadas al servidor y avisos. */
public class Auto {
    static final String PREFS = "scanauto";

    static JSONObject cfg(Context c) {
        try { return new JSONObject(c.getSharedPreferences(PREFS, 0).getString("cfg", "{}")); } catch (Exception e) { return new JSONObject(); }
    }
    static void saveCfg(Context c, String json) { c.getSharedPreferences(PREFS, 0).edit().putString("cfg", json).apply(); }

    /** Códigos que dependen de este Bluetooth (por dirección del dispositivo). */
    static List<JSONObject> codesFor(Context c, String addr, boolean onlyDrive) {
        List<JSONObject> out = new ArrayList<>();
        JSONArray a = cfg(c).optJSONArray("codes"); if (a == null || addr == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i); if (o == null) continue;
            if (addr.equalsIgnoreCase(o.optString("bt")) && (!onlyDrive || o.optBoolean("drive"))) out.add(o);
        }
        return out;
    }

    static boolean hasLoc(Context c) { return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED; }

    /** Ubicación actual (espera hasta 'ms'); si no llega, la última conocida reciente. */
    @SuppressWarnings("MissingPermission")
    static Location location(Context c, long ms) {
        if (!hasLoc(c)) return null;
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        final Location[] got = new Location[1];
        if (Build.VERSION.SDK_INT >= 30) {
            String prov = Build.VERSION.SDK_INT >= 31 && lm.hasProvider(LocationManager.FUSED_PROVIDER) ? LocationManager.FUSED_PROVIDER
                : lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            CountDownLatch l = new CountDownLatch(1);
            try { lm.getCurrentLocation(prov, null, Executors.newSingleThreadExecutor(), loc -> { got[0] = loc; l.countDown(); }); l.await(ms, TimeUnit.MILLISECONDS); } catch (Exception ignored) {}
        }
        if (got[0] != null) return got[0];
        Location best = null;
        for (String p : lm.getProviders(true)) {
            try { Location x = lm.getLastKnownLocation(p); if (x != null && (best == null || x.getTime() > best.getTime())) best = x; } catch (Exception ignored) {}
        }
        if (best != null && System.currentTimeMillis() - best.getTime() < 15 * 60 * 1000) return best;
        return best;
    }

    /** ¿Está dentro de algún lugar seguro? (si no hay ubicación, se considera que no) */
    static String safePlace(Context c, Location loc) {
        if (loc == null) return null;
        JSONArray a = cfg(c).optJSONArray("places"); if (a == null) return null;
        for (int i = 0; i < a.length(); i++) {
            JSONObject p = a.optJSONObject(i); if (p == null) continue;
            float[] d = new float[1];
            Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), p.optDouble("lat"), p.optDouble("lng"), d);
            if (d[0] <= p.optInt("radius", 150) + Math.min(loc.getAccuracy(), 100)) return p.optString("name");
        }
        return null;
    }

    /** Enciende/apaga un código en el servidor con la llave del móvil. */
    static boolean setOn(Context c, String code, boolean on) {
        JSONObject k = cfg(c);
        try {
            URL u = new URL(k.getString("url") + "/rest/v1/rpc/device_set_on");
            HttpURLConnection h = (HttpURLConnection) u.openConnection();
            h.setRequestMethod("POST"); h.setConnectTimeout(15000); h.setReadTimeout(15000); h.setDoOutput(true);
            h.setRequestProperty("Content-Type", "application/json"); h.setRequestProperty("apikey", k.getString("key"));
            JSONObject b = new JSONObject().put("p_token", k.getString("token")).put("p_code", code).put("p_on", on);
            try (OutputStream o = h.getOutputStream()) { o.write(b.toString().getBytes(StandardCharsets.UTF_8)); }
            int st = h.getResponseCode();
            try (InputStream in = st < 400 ? h.getInputStream() : h.getErrorStream()) { byte[] buf = new byte[2048]; String r = new String(buf, 0, Math.max(0, in.read(buf)), StandardCharsets.UTF_8); return st < 400 && r.replace(" ", "").contains("\"ok\":true"); }
        } catch (Exception e) { return false; }
    }

    static void notify(Context c, int id, String title, String text) {
        AlarmService.channels(c);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, AlarmService.CH_MSG).setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title).setContentText(text).setStyle(new NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true).setContentIntent(pi);
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id, b.build());
    }
}
