package com.scan2com.app;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Pantalla de sirena: se muestra sobre la pantalla de bloqueo y suena a volumen máximo hasta pulsar Recibido. */
public class AlarmActivity extends Activity {
    public static final int NOTIF_ID = 4242;
    static volatile AlarmActivity current;   // la sirena que está en pantalla (para pararla desde un aviso de «terminado»)
    static volatile String lastEv;
    String ev() { return getIntent().getStringExtra("ev"); }
    private MediaPlayer player;
    private Vibrator vib;
    private int oldVol = -1;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean on = true;
    private ImageView red, shield;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true); setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        current = this;
        build(getIntent());
        start();
    }

    /** Quien envió la alerta la ha dado por resuelta o terminada: se calla y lo explica. */
    void ended(String title, String body) {
        stopAll();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.WHITE); root.setPadding(dp(28), dp(28), dp(28), dp(28));
        TextView ic = new TextView(this); ic.setText("✅"); ic.setTextSize(72); ic.setGravity(Gravity.CENTER);
        root.addView(ic);
        TextView t = new TextView(this); t.setText(title == null ? "Alerta terminada" : title);
        t.setTextSize(24); t.setTextColor(Color.parseColor("#1A9C16")); t.setGravity(Gravity.CENTER); t.setTypeface(Typeface.DEFAULT_BOLD); t.setPadding(0, dp(12), 0, 0);
        root.addView(t);
        TextView m = new TextView(this); m.setText(body == null ? "" : body);
        m.setTextSize(18); m.setTextColor(Color.parseColor("#444444")); m.setGravity(Gravity.CENTER); m.setPadding(0, dp(10), 0, dp(24));
        root.addView(m);
        Button ok = new Button(this); ok.setText("Ver"); ok.setAllCaps(false); ok.setTextSize(20); ok.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#1A9C16")); bg.setCornerRadius(dp(14)); ok.setBackground(bg);
        ok.setOnClickListener(v -> received());
        root.addView(ok, new LinearLayout.LayoutParams(dp(280), dp(60)));
        setContentView(root);
    }

    @Override
    protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); build(i); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private void build(Intent i) {
        String title = i.getStringExtra("title"), body = i.getStringExtra("body");
        boolean police = title != null && title.startsWith("🚓");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.WHITE); root.setPadding(dp(28), dp(28), dp(28), dp(28));

        TextView h2 = new TextView(this); h2.setText(police ? "Aviso de la Policía" : "¡Alerta!"); h2.setTextColor(Color.parseColor(police ? "#0B2A6B" : "#C4160B"));
        h2.setTextSize(police ? 30 : 34); h2.setTypeface(Typeface.DEFAULT_BOLD); h2.setGravity(Gravity.CENTER); h2.setPadding(0, police ? 0 : dp(12), 0, police ? dp(8) : 0);
        if (police) root.addView(h2);
        FrameLayout siren = new FrameLayout(this);
        ImageView white = new ImageView(this); white.setImageResource(R.drawable.alert_white);
        red = new ImageView(this); red.setImageResource(R.drawable.alert_red);
        siren.addView(white, new FrameLayout.LayoutParams(dp(240), dp(252)));
        siren.addView(red, new FrameLayout.LayoutParams(dp(240), dp(252)));
        if (police) {  // aviso de autoridad: la imagen alterna entre la sirena roja y el escudo, más grande
            siren.removeAllViews();
            shield = new ImageView(this); shield.setImageResource(R.drawable.police); shield.setVisibility(View.INVISIBLE);
            shield.setScaleType(ImageView.ScaleType.FIT_CENTER); shield.setPadding(dp(24), dp(12), dp(24), dp(12));
            siren.addView(red, new FrameLayout.LayoutParams(dp(300), dp(315)));
            siren.addView(shield, new FrameLayout.LayoutParams(dp(300), dp(315)));
            root.addView(siren, new LinearLayout.LayoutParams(dp(300), dp(315)));
        } else { shield = null; root.addView(siren, new LinearLayout.LayoutParams(dp(240), dp(252))); }

        if (!police) root.addView(h2);
        TextView t = new TextView(this); t.setText(title == null ? "" : title.replace("🚨 ¡Alerta! · ", "").replace("🚓 Aviso de la Policía · ", ""));
        t.setTextSize(22); t.setTextColor(Color.parseColor("#222222")); t.setGravity(Gravity.CENTER); t.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(t);
        TextView m = new TextView(this); m.setText(body == null || body.isEmpty() ? "Alguien ha escaneado tu código y te avisa." : "“" + body + "”");
        m.setTextSize(18); m.setTextColor(Color.parseColor("#444444")); m.setGravity(Gravity.CENTER); m.setPadding(0, dp(10), 0, dp(24));
        root.addView(m);

        Button ok = new Button(this); ok.setText("Recibido"); ok.setAllCaps(false); ok.setTextSize(20); ok.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.parseColor("#E8352B")); bg.setCornerRadius(dp(14)); ok.setBackground(bg);
        ok.setOnClickListener(v -> received());
        root.addView(ok, new LinearLayout.LayoutParams(dp(280), dp(60)));
        setContentView(root);
    }

    private void start() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        try { oldVol = am.getStreamVolume(AudioManager.STREAM_ALARM); am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0); } catch (Exception ignored) {}
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setDataSource(this, Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.siren));
            player.setLooping(true); player.prepare(); player.start();
        } catch (Exception ignored) {}
        vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        try { if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createWaveform(new long[]{0, 700, 300}, 0)); else vib.vibrate(new long[]{0, 700, 300}, 0); } catch (Exception ignored) {}
        h.post(new Runnable() { public void run() { on = !on; if (red != null) red.setVisibility(on ? View.VISIBLE : View.INVISIBLE); if (shield != null) shield.setVisibility(on ? View.INVISIBLE : View.VISIBLE); h.postDelayed(this, 600); } });
        // por seguridad, la sirena se para sola a los 3 minutos
        h.postDelayed(this::stopAll, 180000);
    }

    private void stopAll() {
        h.removeCallbacksAndMessages(null);
        try { if (player != null) { player.stop(); player.release(); player = null; } } catch (Exception ignored) {}
        try { if (vib != null) vib.cancel(); } catch (Exception ignored) {}
        try { if (oldVol >= 0) ((AudioManager) getSystemService(Context.AUDIO_SERVICE)).setStreamVolume(AudioManager.STREAM_ALARM, oldVol, 0); oldVol = -1; } catch (Exception ignored) {}
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIF_ID);
    }

    private void received() {
        stopAll();
        String ev = getIntent().getStringExtra("ev");
        String url = "/app/?tab=alerts" + (ev != null ? "&ack=" + ev : "");
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("url", url));
        finish();
    }

    @Override
    public void onBackPressed() { /* hay que pulsar Recibido */ }

    @Override
    protected void onDestroy() { if (current == this) current = null; stopAll(); super.onDestroy(); }
}
