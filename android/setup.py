#!/usr/bin/env python3
"""Prepara el proyecto Android generado por Capacitor: código nativo, sirena, iconos y permisos."""
import os, re, shutil, math, struct, wave
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)                      # raíz del repo web
APP = os.path.join(HERE, 'android', 'app', 'src', 'main')
RES = os.path.join(APP, 'res')

# 1) código Java
dst = os.path.join(APP, 'java', 'com', 'scanand', 'pro'); os.makedirs(dst, exist_ok=True)
JAVA = os.path.join(HERE, 'native', 'java')
for f in os.listdir(JAVA):
    if f.endswith('.java'): shutil.copy(os.path.join(JAVA, f), dst)

# 2) imágenes de la sirena (las mismas de la web)
os.makedirs(os.path.join(RES, 'drawable'), exist_ok=True)
for f in ('alert_red.png', 'alert_white.png'):
    shutil.copy(os.path.join(ROOT, 'app', f), os.path.join(RES, 'drawable', f))

# 3) sonido de sirena (barrido 650-1500 Hz, 1,8 s, se repite en bucle)
os.makedirs(os.path.join(RES, 'raw'), exist_ok=True)
sr, dur = 22050, 1.8
with wave.open(os.path.join(RES, 'raw', 'siren.wav'), 'w') as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr)
    ph, frames = 0.0, bytearray()
    for n in range(int(sr * dur)):
        t = n / sr; x = (t % 0.9) / 0.9; f = 650 + 850 * (2 * x if x < .5 else 2 * (1 - x))
        ph += 2 * math.pi * f / sr
        v = math.sin(ph) + 0.35 * math.sin(3 * ph)
        frames += struct.pack('<h', int(max(-1, min(1, v * 0.8)) * 32000))
    w.writeframes(bytes(frames))

# 4) iconos originales de 2014 (sacados del APK original) e imágenes para la web
import zipfile
import glob
_o = sorted(glob.glob(os.path.join(HERE, '*ScanAnd*.apk')))
ORIG = _o[0] if _o else os.path.join(HERE, 'ScanAnd-2014.apk')
WEBIMG = os.path.join(ROOT, 'app', 'img2014'); os.makedirs(WEBIMG, exist_ok=True)
have_orig = os.path.exists(ORIG)
if have_orig:
    z = zipfile.ZipFile(ORIG)
    for d in ('mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi'):
        src_name = f'res/drawable-{d}/ic_launcher.png'
        if src_name in z.namelist():
            p = os.path.join(RES, 'mipmap-' + d); os.makedirs(p, exist_ok=True)
            data = z.read(src_name)
            for name in ('ic_launcher.png', 'ic_launcher_round.png', 'ic_launcher_foreground.png'):
                open(os.path.join(p, name), 'wb').write(data)
        n = f'res/drawable-{d}/ic_notif.png'
        if n in z.namelist():
            p = os.path.join(RES, 'drawable-' + d); os.makedirs(p, exist_ok=True)
            open(os.path.join(p, 'ic_notif.png'), 'wb').write(z.read(n))
    for src_name, dst in (('res/drawable-xxhdpi/ic_chat_active.png', 'chat_ok.png'), ('res/drawable-xxhdpi/ic_chat_inactive.png', 'chat_blocked.png'),
                          ('res/drawable-xxhdpi/ic_action_camera.png', 'camera.png'), ('res/drawable-xxxhdpi/ic_launcher.png', 'icon.png'),
                          ('res/drawable-xxhdpi/ic_action_search.png', 'search.png')):
        if src_name in z.namelist(): open(os.path.join(WEBIMG, dst), 'wb').write(z.read(src_name))
    shutil.rmtree(os.path.join(RES, 'mipmap-anydpi-v26'), ignore_errors=True)

# 4b) si no está el APK original, icono a partir del logo
if not have_orig:
  src = Image.open(os.path.join(ROOT, 'app', 'icon-512.png')).convert('RGBA')
  for d, s in {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}.items():
    p = os.path.join(RES, 'mipmap-' + d); os.makedirs(p, exist_ok=True)
    for name in ('ic_launcher.png', 'ic_launcher_round.png', 'ic_launcher_foreground.png'):
        src.resize((s, s), Image.LANCZOS).save(os.path.join(p, name))
  shutil.rmtree(os.path.join(RES, 'mipmap-anydpi-v26'), ignore_errors=True)

# pantalla de inicio (splash) con el icono grande original de 2014, sobre negro
_sp = sorted(glob.glob(os.path.join(HERE, '*2014-02-14*.png')) + glob.glob(os.path.join(HERE, 'splash*.png')))
if _sp:
    sp = Image.open(_sp[0]).convert('RGB'); sp = sp.crop((0, 0, sp.width, int(sp.height * 0.86)))  # sin la barra de botones
    bbox = Image.eval(sp.convert('L'), lambda v: 255 if v > 24 else 0).getbbox() or (0, 0) + sp.size
    icon = sp.crop(bbox)
    for root, _, files in os.walk(RES):
        for f in files:
            if f == 'splash.png':
                p = os.path.join(root, f); w, h = Image.open(p).size
                out = Image.new('RGB', (w, h), (0, 0, 0)); side = int(min(w, h) * 0.6)
                ic = icon.resize((side, int(side * icon.height / icon.width)), Image.LANCZOS)
                out.paste(ic, ((w - ic.width) // 2, (h - ic.height) // 2)); out.save(p)
    # icono adaptativo (Android 8+): fondo negro + icono original en alta resolución, sin bordes blancos
    for d, sz in {'mdpi': 108, 'hdpi': 162, 'xhdpi': 216, 'xxhdpi': 324, 'xxxhdpi': 432}.items():
        p = os.path.join(RES, 'mipmap-' + d); os.makedirs(p, exist_ok=True)
        fg = Image.new('RGBA', (sz, sz), (0, 0, 0, 0)); side = int(sz * 0.64)
        ic = icon.resize((side, int(side * icon.height / icon.width)), Image.LANCZOS)
        fg.paste(ic, ((sz - ic.width) // 2, (sz - ic.height) // 2)); fg.save(os.path.join(p, 'ic_launcher_foreground.png'))
    ad = os.path.join(RES, 'mipmap-anydpi-v26'); os.makedirs(ad, exist_ok=True)
    xml = '<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@color/ic_launcher_background"/>\n    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n</adaptive-icon>\n'
    for n in ('ic_launcher.xml', 'ic_launcher_round.xml'): open(os.path.join(ad, n), 'w').write(xml)
    open(os.path.join(RES, 'values', 'ic_launcher_background.xml'), 'w').write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#000000</color>\n</resources>\n')

# Android 12+: fondo negro e icono original en la pantalla de inicio del sistema
st = os.path.join(RES, 'values', 'styles.xml'); x = open(st, encoding='utf-8').read()
if 'windowSplashScreenBackground' not in x:
    x = x.replace('<item name="android:background">@drawable/splash</item>', '<item name="android:background">@drawable/splash</item>\n        <item name="windowSplashScreenBackground">#000000</item>\n        <item name="windowSplashScreenAnimatedIcon">@mipmap/ic_launcher</item>\n        <item name="postSplashScreenTheme">@style/AppTheme.NoActionBar</item>')
    open(st, 'w', encoding='utf-8').write(x)

# icono pequeño de notificación (por si falta)
if not os.path.exists(os.path.join(RES, 'drawable-xxhdpi', 'ic_notif.png')):
    os.makedirs(os.path.join(RES, 'drawable'), exist_ok=True)
    Image.open(os.path.join(ROOT, 'app', 'icon-192.png')).convert('RGBA').resize((96, 96)).save(os.path.join(RES, 'drawable', 'ic_notif.png'))

# 5) permisos, pantalla de alarma y servicio de avisos en el manifiesto
mf = os.path.join(APP, 'AndroidManifest.xml'); m = open(mf, encoding='utf-8').read()
if 'xmlns:tools' not in m:
    m = m.replace('<manifest xmlns:android="http://schemas.android.com/apk/res/android"', '<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools"', 1)
perms = ['POST_NOTIFICATIONS', 'USE_FULL_SCREEN_INTENT', 'VIBRATE', 'WAKE_LOCK', 'MODIFY_AUDIO_SETTINGS', 'ACCESS_NOTIFICATION_POLICY', 'CAMERA']
add = ''.join(f'    <uses-permission android:name="android.permission.{p}" />\n' for p in perms if p not in m)
m = m.replace('</manifest>', add + '</manifest>')
comp = '''
        <activity android:name=".AlarmActivity" android:exported="false" android:showWhenLocked="true" android:turnScreenOn="true"
            android:launchMode="singleTop" android:excludeFromRecents="true" android:theme="@android:style/Theme.Material.Light.NoActionBar" />
        <service android:name=".AlarmService" android:exported="false">
            <intent-filter><action android:name="com.google.firebase.MESSAGING_EVENT" /></intent-filter>
        </service>
        <service android:name="com.capacitorjs.plugins.pushnotifications.MessagingService" tools:node="remove" />
'''
if 'AlarmActivity' not in m: m = m.replace('</application>', comp + '    </application>')
m = re.sub(r'android:launchMode="[^"]*"', 'android:launchMode="singleTask"', m, count=1)
open(mf, 'w', encoding='utf-8').write(m)

# 6) dependencia de Firebase visible para nuestro código (el plugin la declara como interna)
bg = os.path.join(HERE, 'android', 'app', 'build.gradle'); g = open(bg, encoding='utf-8').read()
if 'firebase-messaging' not in g:
    g = g.replace('dependencies {', 'dependencies {\n    implementation "com.google.firebase:firebase-messaging:25.0.1"', 1)
    open(bg, 'w', encoding='utf-8').write(g)

# 7) configuración de Firebase
gs = os.path.join(HERE, 'google-services.json')
if os.path.exists(gs): shutil.copy(gs, os.path.join(HERE, 'android', 'app', 'google-services.json'))
else: print('AVISO: falta google-services.json: la app no recibirá avisos')
print('setup ok')
