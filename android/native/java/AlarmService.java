package com.scan2com.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.app.Notification;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import androidx.core.app.Person;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import com.capacitorjs.plugins.pushnotifications.MessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

/** Recibe los avisos de Firebase. Si es una alerta, lanza la sirena a pantalla completa. */
public class AlarmService extends MessagingService {
    public static final String CH_ALARM = "scan2com_alarm_v1";
    public static final String CH_MSG = "scan2com_msg_v1";
    public static final String CH_CALL = "scan2com_call_v1";
    public static final int CALL_NOTIF = 4242;

    @Override
    public void onMessageReceived(@NonNull RemoteMessage msg) {
        super.onMessageReceived(msg);
        Map<String, String> d = msg.getData();
        String title = d.containsKey("title") ? d.get("title") : "Scan2Com";
        String body = d.containsKey("body") ? d.get("body") : "";
        String url = d.containsKey("url") ? d.get("url") : "/app/";
        if (d.containsKey("call_end")) { stopCall(this); return; }
        if ("1".equals(d.get("call"))) { if (!MainActivity.visible) showCall(this, title, d.get("call_id")); return; }   // con la app en pantalla la llamada llega por tiempo real
        if ("1".equals(d.get("alarm"))) showAlarm(this, title, body, d.get("ev"), url);
        else if (!MainActivity.visible) showMessage(this, title, body, url, d.get("tag"));   // con la app en pantalla no hace falta la notificación
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
        if (nm.getNotificationChannel(CH_CALL) == null) {
            NotificationChannel ch = new NotificationChannel(CH_CALL, "Llamadas", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Llamadas de voz por la app");
            ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            ch.enableVibration(true); ch.setVibrationPattern(new long[]{0, 800, 600, 800, 600, 800});
            ch.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
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
            .setSmallIcon(R.drawable.ic_notif)
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

    /** Llamada entrante: notificación de llamada de Android (Contestar / Rechazar), suena hasta que se contesta,
     *  se rechaza o pasan 45 s; con el móvil bloqueado abre la pantalla de la llamada. */
    static void showCall(Context c, String title, String id) {
        if (id == null) return;
        channels(c);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("url", "/app/?call=" + id).putExtra("call", true);
        Intent ans = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("url", "/app/?call=" + id + "&answer=1").putExtra("call", true);
        Intent dec = new Intent(c, CallReceiver.class).setAction("decline").putExtra("id", id);
        PendingIntent piOpen = PendingIntent.getActivity(c, 3001, open, flags);
        PendingIntent piAns = PendingIntent.getActivity(c, 3002, ans, flags);
        PendingIntent piDec = PendingIntent.getBroadcast(c, 3003, dec, flags);
        String who = title == null ? "Scan2Com" : title.replaceFirst("^📞 ", "");
        Person caller = new Person.Builder().setName(who).setImportant(true).build();
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_CALL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(who).setContentText("Llamada de Scan2Com")
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, piDec, piAns))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true).setAutoCancel(true).setTimeoutAfter(45000)
            .setContentIntent(piOpen).setFullScreenIntent(piOpen, true);
        Notification n = b.build(); n.flags |= Notification.FLAG_INSISTENT;   // el tono se repite hasta contestar
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(CALL_NOTIF, n);
    }
    static void stopCall(Context c) { ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(CALL_NOTIF); }

    static void showMessage(Context c, String title, String body, String url, String tag) {
        channels(c);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("url", url);
        PendingIntent pi = PendingIntent.getActivity(c, (tag == null ? 0 : tag.hashCode()), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_MSG)
            .setSmallIcon(R.drawable.ic_notif).setContentTitle(title).setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(pi);
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(tag == null ? 2 : tag.hashCode(), b.build());
    }
}
