package com.scan2com.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
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
import java.util.ArrayList;
import java.util.List;

/** Puente con la web: lista de Bluetooth emparejados, permisos, ubicación y configuración del modo coche. */
@CapacitorPlugin(name = "ScanAuto")
public class ScanAutoPlugin extends Plugin {
    private boolean granted(String p) { return getContext().checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

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
}
