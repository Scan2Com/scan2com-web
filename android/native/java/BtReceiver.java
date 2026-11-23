package com.scan2com.app;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import java.util.List;
import org.json.JSONObject;

/** Bluetooth del coche: al desconectar activa el código (salvo en lugares seguros); al conectar, si se pidió, vigila si vas conduciendo. */
public class BtReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        BluetoothDevice dev = Build.VERSION.SDK_INT >= 33 ? i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class) : i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (dev == null) return;
        final String addr = dev.getAddress(), action = i.getAction();
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            if (!Auto.codesFor(c, addr, true).isEmpty()) {
                Intent s = new Intent(c, DriveService.class).putExtra("bt", addr);
                try { if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s); } catch (Exception ignored) {}
            }
            return;
        }
        if (!BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) return;
        final List<JSONObject> codes = Auto.codesFor(c, addr, false);
        if (codes.isEmpty()) return;
        try { c.stopService(new Intent(c, DriveService.class)); } catch (Exception ignored) {}
        final PendingResult pr = goAsync(); final Context ctx = c.getApplicationContext();
        new Thread(() -> {
            try {
                Location loc = Auto.location(ctx, 12000);
                String safe = Auto.safePlace(ctx, loc);
                if (safe != null) return;  // en casa / parking: no se activa
                StringBuilder names = new StringBuilder();
                for (JSONObject o : codes) if (Auto.setOn(ctx, o.optString("id"), true)) names.append(names.length() > 0 ? ", " : "").append(o.optString("name", "Coche"));
                if (names.length() > 0) Auto.notify(ctx, 5100, "Código activado: " + names, "Has bajado del coche. Quien escanee la pegatina ya puede avisarte.");
            } finally { pr.finish(); }
        }).start();
    }
}
