package com.scan2com.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** "Rechazar" en la notificación de llamada: deja de sonar sin abrir la app (quien llama verá "No contesta"). */
public class CallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) { AlarmService.stopCall(c); }
}
