package com.scanand.pro;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import com.capacitorjs.plugins.pushnotifications.MessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

/** Recibe los avisos de Firebase. Si es una alerta, lanza la sirena a pantalla completa. */
public class AlarmService extends MessagingService {
    public static final String CH_ALARM = "scanand_alarm_v1";
    public static final String CH_MSG = "scanand_msg_v1";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage msg) {
        super.onMessageReceived(msg);
        Map<String, String> d = msg.getData();
        String title = d.containsKey("title") ? d.get("title") : "Scan & Pro";
        String body = d.containsKey("body") ? d.get("body") : "";
        String url = d.containsKey("url") ? d.get("url") : "/app/";
        if ("1".equals(d.get("alarm"))) showAlarm(this, title, body, d.get("ev"), url);
        else showMessage(this, title, body, url, d.get("tag"));
    }

    static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CH_ALARM) == null) {
            NotificationChannel ch = new NotificationChannel(CH_ALARM, "Alertas (sirena)", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Alertas de tus códigos: sirena a pantalla completa");
            Uri snd = Uri.parse("android.resource://" + c.getPackageName() + "/" + R.raw.siren);
            ch.setSound(snd, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            ch.enableVibration(true); ch.setVibrationPattern(new long[]{0, 700, 300, 700, 300, 700});
            ch.setBypassDnd(true); ch.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        if (nm.getNotificationChannel(CH_MSG) == null) {
            NotificationChannel ch = new NotificationChannel(CH_MSG, "Mensajes y chats", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
        }
    }

    static void showAlarm(Context c, String title, String body, String ev, String url) {
        channels(c);
        Intent full = new Intent(c, AlarmActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("title", title).putExtra("body", body).putExtra("ev", ev).putExtra("url", url);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(c, 1001, full, flags);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_ALARM)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title).setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true).setAutoCancel(true)
            .setContentIntent(pi).setFullScreenIntent(pi, true);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(AlarmActivity.NOTIF_ID, b.build());
        // si se puede, abrir directamente la pantalla de alarma
        try { c.startActivity(full); } catch (Exception ignored) {}
    }

    static void showMessage(Context c, String title, String body, String url, String tag) {
        channels(c);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("url", url);
        PendingIntent pi = PendingIntent.getActivity(c, (tag == null ? 0 : tag.hashCode()), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_MSG)
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle(title).setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(pi);
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(tag == null ? 2 : tag.hashCode(), b.build());
    }
}
