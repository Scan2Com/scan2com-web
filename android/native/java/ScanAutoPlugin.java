package com.scan2com.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Puente con la web: lista de Bluetooth emparejados, permisos, ubicación y configuración del modo coche. */
@CapacitorPlugin(name = "ScanAuto")
public class ScanAutoPlugin extends Plugin {
    private boolean granted(String p) { return getContext().checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }
    private boolean locGranted() { return granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION); }
    private boolean locEnabled() {
        LocationManager lm = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        if (Build.VERSION.SDK_INT >= 28) return lm.isLocationEnabled();
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    /** Avisos por zona: pide el permiso de ubicación. La primera vez sale la ventana de Android; si ya se denegó, se abren los ajustes de la app;
     *  si el permiso está pero la ubicación del móvil está apagada, se abre la pantalla para encenderla. */
    @PluginMethod
    public void askLocation(PluginCall call) {
        JSObject r = new JSObject();
        android.content.SharedPreferences sp = getContext().getSharedPreferences(Auto.PREFS, 0);
        if (!locGranted()) {
            boolean asked = sp.getBoolean("loc_asked", false);
            boolean rationale = getActivity().shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION);
            if (!asked || rationale) {
                sp.edit().putBoolean("loc_asked", true).apply();
                getActivity().requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 79);
                r.put("step", "prompt");
            } else { go(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName()))); r.put("step", "settings"); }
            call.resolve(r); return;
        }
        if (!locEnabled()) { go(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); r.put("step", "gps"); call.resolve(r); return; }
        r.put("step", "ok"); call.resolve(r);
    }

    // QR de wifi: Android 11+ muestra el diálogo del sistema «¿Guardar esta red?» y se conecta; antes, abre los ajustes de wifi
    @PluginMethod
    public void joinWifi(PluginCall call) {
        String ssid = call.getString("ssid", ""), pass = call.getString("pass", ""), type = call.getString("type", "");
        boolean hidden = Boolean.TRUE.equals(call.getBoolean("hidden", false));
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                android.net.wifi.WifiNetworkSuggestion.Builder b = new android.net.wifi.WifiNetworkSuggestion.Builder().setSsid(ssid).setIsHiddenSsid(hidden);
                String t = type == null ? "" : type.toUpperCase();
                if (pass != null && !pass.isEmpty() && !"NOPASS".equals(t)) {
                    if (t.contains("SAE")) b.setWpa3Passphrase(pass); else b.setWpa2Passphrase(pass);
                }
                ArrayList<android.os.Parcelable> list = new ArrayList<>(); list.add(b.build());
                Intent i = new Intent(Settings.ACTION_WIFI_ADD_NETWORKS);
                i.putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, list);
                getActivity().startActivity(i);
                JSObject r = new JSObject(); r.put("ok", true); r.put("mode", "dialog"); call.resolve(r);
            } else {
                getActivity().startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                JSObject r = new JSObject(); r.put("ok", true); r.put("mode", "settings"); call.resolve(r);
            }
        } catch (Exception e) { call.reject(e.getMessage()); }
    }

    @PluginMethod
    public void openAppSettings(PluginCall call) {
        go(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName())));
        call.resolve();
    }

    @PluginMethod
    public void status(PluginCall call) {
        JSObject r = new JSObject();
        r.put("bt", Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_CONNECT));
        r.put("loc", granted(Manifest.permission.ACCESS_FINE_LOCATION));
        r.put("bg", Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION));
        PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        r.put("battery", pm != null && pm.isIgnoringBatteryOptimizations(getContext().getPackageName()));
        call.resolve(r);
    }

    /** Pide el siguiente permiso que falte (uno cada vez; la web vuelve a llamar después). */
    @PluginMethod
    public void ask(PluginCall call) {
        List<String> need = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT)) need.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) { need.add(Manifest.permission.ACCESS_FINE_LOCATION); need.add(Manifest.permission.ACCESS_COARSE_LOCATION); }
        JSObject r = new JSObject();
        if (!need.isEmpty()) { getActivity().requestPermissions(need.toArray(new String[0]), 77); r.put("step", "basic"); call.resolve(r); return; }
        if (Build.VERSION.SDK_INT >= 29 && !granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            if (Build.VERSION.SDK_INT >= 30) {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName()));
                getActivity().startActivity(i);
            } else getActivity().requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 78);
            r.put("step", "background"); call.resolve(r); return;
        }
        PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getContext().getPackageName())) {
            @SuppressLint("BatteryLife") Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getContext().getPackageName()));
            try { getActivity().startActivity(i); } catch (Exception ignored) {}
            r.put("step", "battery"); call.resolve(r); return;
        }
        r.put("step", "done"); call.resolve(r);
    }

    @PluginMethod
    @SuppressLint("MissingPermission")
    public void bonded(PluginCall call) {
        JSArray list = new JSArray();
        try {
            BluetoothManager bm = (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter ad = bm != null ? bm.getAdapter() : null;
            if (ad != null) for (BluetoothDevice d : ad.getBondedDevices()) { JSObject o = new JSObject(); o.put("name", d.getName()); o.put("addr", d.getAddress()); list.put(o); }
        } catch (Exception ignored) {}
        JSObject r = new JSObject(); r.put("devices", list); call.resolve(r);
    }

    @PluginMethod
    public void here(PluginCall call) {
        new Thread(() -> {
            Location l = Auto.location(getContext(), 15000);
            if (l == null) { call.reject("Sin ubicación"); return; }
            JSObject r = new JSObject(); r.put("lat", l.getLatitude()); r.put("lng", l.getLongitude()); r.put("acc", l.getAccuracy()); call.resolve(r);
        }).start();
    }

    @PluginMethod
    public void setConfig(PluginCall call) {
        String json = call.getString("json", "{}");
        Auto.saveCfg(getContext(), json);
        call.resolve();
    }

    /* ---------- configuración de avisos: notificaciones, batería, autoinicio (Xiaomi, Oppo, Vivo, Huawei…) ---------- */
    private static final String[][] AUTOSTART = {
        {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
        {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
        {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
        {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
        {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
        {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
        {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
        {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
        {"com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"},
        {"com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"},
    };
    private static boolean needsAutostart() {
        String b = (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase();
        for (String k : new String[]{"xiaomi", "redmi", "poco", "oppo", "realme", "vivo", "iqoo", "huawei", "honor", "oneplus", "asus"}) if (b.contains(k)) return true;
        return false;
    }
    /** Xiaomi permite saber si el autoinicio está activado: 1 sí, 0 no, -1 no se sabe. */
    private int autostartOn() {
        String b = (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase();
        if (!(b.contains("xiaomi") || b.contains("redmi") || b.contains("poco"))) return -1;
        try {
            AppOpsManager ops = (AppOpsManager) getContext().getSystemService(Context.APP_OPS_SERVICE);
            Method m = AppOpsManager.class.getMethod("checkOpNoThrow", int.class, int.class, String.class);
            int r = (int) m.invoke(ops, 10008, android.os.Process.myUid(), getContext().getPackageName());
            return r == AppOpsManager.MODE_ALLOWED ? 1 : 0;
        } catch (Exception e) { return -1; }
    }

    @PluginMethod
    public void avisos(PluginCall call) {
        Context c = getContext();
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        JSObject r = new JSObject();
        r.put("notif", nm != null && nm.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)));
        r.put("battery", pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName()));
        r.put("autostart", needsAutostart());
        r.put("autostartOn", autostartOn());
        r.put("fullscreen", Build.VERSION.SDK_INT < 34 || (nm != null && nm.canUseFullScreenIntent()));
        r.put("brand", Build.MANUFACTURER);
        r.put("loc", locGranted());
        r.put("locOn", locEnabled());
        call.resolve(r);
    }

    private void go(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { getActivity().startActivity(i); }
        catch (Exception e) {
            try { getActivity().startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
        }
    }

    /** Si el permiso de notificaciones ya se denegó, Android no vuelve a preguntar: se abre su pantalla de ajustes. */
    @PluginMethod
    public void openNotif(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
        go(i); call.resolve();
    }

    @PluginMethod
    public void askBattery(PluginCall call) {
        @SuppressLint("BatteryLife") Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getContext().getPackageName()));
        go(i); call.resolve();
    }

    @PluginMethod
    public void openAutostart(PluginCall call) {
        for (String[] a : AUTOSTART) {
            Intent i = new Intent().setComponent(new ComponentName(a[0], a[1])).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { getActivity().startActivity(i); call.resolve(); return; } catch (Exception ignored) {}
        }
        go(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName())));
        call.resolve();
    }

    @PluginMethod
    public void openFullScreen(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 34) go(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getContext().getPackageName())));
        call.resolve();
    }

    /** llamada contestada, rechazada o terminada en la web: deja de sonar la notificación de llamada */
    @PluginMethod
    public void callStop(PluginCall call) { AlarmService.stopCall(getContext()); call.resolve(); }
    /** llamada terminada: la app deja de verse encima de la pantalla de bloqueo */
    @PluginMethod
    public void callDone(PluginCall call) {
        AlarmService.stopCall(getContext());
        if (Build.VERSION.SDK_INT >= 27 && getActivity() != null) getActivity().runOnUiThread(() -> { getActivity().setShowWhenLocked(false); getActivity().setTurnScreenOn(false); });
        call.resolve();
    }
}
