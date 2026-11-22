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

# 4) icono de la app a partir del logo
src = Image.open(os.path.join(ROOT, 'app', 'icon-512.png')).convert('RGBA')
for d, s in {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}.items():
    p = os.path.join(RES, 'mipmap-' + d); os.makedirs(p, exist_ok=True)
    for name in ('ic_launcher.png', 'ic_launcher_round.png', 'ic_launcher_foreground.png'):
        src.resize((s, s), Image.LANCZOS).save(os.path.join(p, name))
shutil.rmtree(os.path.join(RES, 'mipmap-anydpi-v26'), ignore_errors=True)

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
