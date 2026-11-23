#!/usr/bin/env python3
"""Prepara el proyecto Android generado por Capacitor: código nativo, sirena, iconos y permisos."""
import io, os, re, shutil, math, struct, wave
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

# 2b) agente de policía para los avisos de autoridad: emoji 3D de Google Noto (Apache 2.0),
#     hombre con pelo corto, retocado con gesto serio (cejas fruncidas y boca recta)
def police_png():
    import urllib.request
    from PIL import ImageDraw, ImageFilter
    url = 'https://raw.githubusercontent.com/googlefonts/noto-emoji/main/3D/png/512/emoji_u1f46e_200d_2642.png'
    im = Image.open(io.BytesIO(urllib.request.urlopen(url, timeout=60).read())).convert('RGBA')
    px = im.load()
    def fill_cols(x0, x1, y0, y1):   # borra una zona rellenando cada columna con la piel de arriba y de abajo
        for x in range(x0, x1):
            top, bot = px[x, y0], px[x, y1]
            for y in range(y0, y1 + 1):
                t = (y - y0) / (y1 - y0); px[x, y] = tuple(round(top[i] * (1 - t) + bot[i] * t) for i in range(4))
    fill_cols(156, 232, 188, 213); fill_cols(280, 356, 188, 213); fill_cols(196, 316, 300, 360)
    blur = im.filter(ImageFilter.GaussianBlur(1.5)); msk = Image.new('L', im.size, 0); d = ImageDraw.Draw(msk)
    for r in ((196, 300, 316, 360), (156, 188, 232, 213), (280, 188, 356, 213)): d.rectangle(r, fill=255)
    im = Image.composite(blur, im, msk.filter(ImageFilter.GaussianBlur(2)))
    S = 4; big = im.resize((512 * S, 512 * S), Image.LANCZOS)
    lay = Image.new('RGBA', big.size, (0, 0, 0, 0)); d = ImageDraw.Draw(lay)
    def stroke(p, q, w, col):
        d.line([(p[0] * S, p[1] * S), (q[0] * S, q[1] * S)], fill=col, width=int(w * S))
        for c in (p, q): d.ellipse(((c[0] - w / 2) * S, (c[1] - w / 2) * S, (c[0] + w / 2) * S, (c[1] + w / 2) * S), fill=col)
    stroke((166, 196), (224, 205), 12, (104, 58, 40, 255)); stroke((288, 205), (346, 196), 12, (104, 58, 40, 255))
    stroke((230, 327), (282, 327), 7, (72, 34, 24, 255))
    big = Image.alpha_composite(big, lay.filter(ImageFilter.GaussianBlur(0.6 * S)))
    sh = Image.new('RGBA', big.size, (0, 0, 0, 0)); ImageDraw.Draw(sh).ellipse((238 * S, 335 * S, 274 * S, 343 * S), fill=(190, 110, 0, 60))
    big = Image.alpha_composite(big, sh.filter(ImageFilter.GaussianBlur(3 * S)))
    out = big.resize((512, 512), Image.LANCZOS); out = out.crop(out.getbbox())
    W, H = 480, 552; k = min(W * 0.96 / out.width, H * 0.96 / out.height)
    out = out.resize((int(out.width * k), int(out.height * k)), Image.LANCZOS)
    canvas = Image.new('RGBA', (W, H), (0, 0, 0, 0)); canvas.paste(out, ((W - out.width) // 2, (H - out.height) // 2), out)
    return canvas
try:
    pol = police_png()
except Exception as e:
    print('aviso: no se pudo generar el agente de policía:', e); pol = Image.open(os.path.join(ROOT, 'app', 'alert_red.png'))
pol.save(os.path.join(RES, 'drawable', 'police.png'))
_webimg = os.path.join(ROOT, 'app', 'img2014'); os.makedirs(_webimg, exist_ok=True)
pol.save(os.path.join(_webimg, 'police.png'), optimize=True)

# 2c) base de la pegatina alta del coche, recortada de la plantilla original de Luis (si está en el repo)
def sticker_base():
    from PIL import ImageDraw
    src = None   # la plantilla de pegatinas de coche: PNG apaisado de unos 2480 x 1181
    for f in sorted(os.listdir(HERE)):
        if f.lower().endswith('.png'):
            with Image.open(os.path.join(HERE, f)) as im:
                if im.width >= 1200 and 2.0 < im.width / im.height < 2.2: src = os.path.join(HERE, f)
    if not src: return
    t = Image.open(src).convert('RGB')
    k = t.width / 2480                              # la plantilla original mide 2480 x 1181
    box = [round(v * k) for v in (20, 20, 583, 792)]
    st = t.crop(box).resize((1126, 1544), Image.LANCZOS)
    d = ImageDraw.Draw(st); d.rounded_rectangle((114, 120, 1012, 1018), radius=80, fill='white')   # quita el QR de ejemplo
    st = st.convert('RGBA'); tmp = st.convert('RGB')
    for p in ((0, 0), (st.width - 1, 0), (0, st.height - 1), (st.width - 1, st.height - 1)):
        ImageDraw.floodfill(tmp, p, (1, 2, 3), thresh=40)                               # fondo blanco exterior -> transparente
    a = st.getchannel('A'); tp = tmp.load(); ap = a.load()
    for y in range(st.height):
        for x in range(st.width):
            if tp[x, y] == (1, 2, 3): ap[x, y] = 0
    st.putalpha(a); st.resize((563, 772), Image.LANCZOS).save(os.path.join(_webimg, 'pegatina_coche.png'), optimize=True)
sticker_base()

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
    # iconos de la barra de la pantalla del código: formas originales en azul holo #33b5e5
    for src_name, dst in (('ic_qr_fullscreen_disabled', 'bar_back.png'), ('ic_share', 'bar_share.png'), ('ic_email', 'bar_mail.png'),
                          ('ic_settings_enabled', 'bar_settings.png'), ('ic_save', 'bar_save.png')):
        n = f'res/drawable-xxhdpi/{src_name}.png'
        if n not in z.namelist(): continue
        im = Image.open(io.BytesIO(z.read(n))).convert('RGBA')
        a = im.getchannel('A'); bb = a.point(lambda v: 255 if v > 40 else 0).getbbox() or (0, 0) + im.size
        a = a.crop(bb); mx = max(a.getextrema()[1], 1); a = a.point(lambda v: min(255, v * 255 // mx))
        side = max(a.size); out = Image.new('RGBA', (side, side), (51, 181, 229, 0))
        col = Image.new('RGBA', a.size, (51, 181, 229, 255)); col.putalpha(a)
        out.paste(col, ((side - a.size[0]) // 2, (side - a.size[1]) // 2))
        if dst == 'bar_back.png':  # flechas hacia dentro (reducir/volver): cada cuadrante girado 180º
            pad = side // 8; big = Image.new('RGBA', (side + 2 * pad,) * 2, (51, 181, 229, 0)); big.paste(out, (pad, pad)); out = big; side = out.size[0]
            h = side // 2; inv = Image.new('RGBA', out.size, (51, 181, 229, 0))
            for x in (0, h):
                for y in (0, h): inv.paste(out.crop((x, y, x + h, y + h)).rotate(180), (x, y))
            out = inv
        out.save(os.path.join(WEBIMG, dst))
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
        fg = Image.new('RGBA', (sz, sz), (0, 0, 0, 0)); side = int(sz * 0.50)  # entero dentro del círculo visible
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
perms = ['POST_NOTIFICATIONS', 'USE_FULL_SCREEN_INTENT', 'VIBRATE', 'WAKE_LOCK', 'MODIFY_AUDIO_SETTINGS', 'ACCESS_NOTIFICATION_POLICY', 'CAMERA',
         'BLUETOOTH_CONNECT', 'ACCESS_FINE_LOCATION', 'ACCESS_COARSE_LOCATION', 'ACCESS_BACKGROUND_LOCATION', 'FOREGROUND_SERVICE', 'FOREGROUND_SERVICE_LOCATION', 'REQUEST_IGNORE_BATTERY_OPTIMIZATIONS']
add = ''.join(f'    <uses-permission android:name="android.permission.{p}" />\n' for p in perms if p not in m)
m = m.replace('</manifest>', add + '    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />\n</manifest>')
comp = '''
        <activity android:name=".AlarmActivity" android:exported="false" android:showWhenLocked="true" android:turnScreenOn="true"
            android:launchMode="singleTop" android:excludeFromRecents="true" android:theme="@android:style/Theme.Material.Light.NoActionBar" />
        <service android:name=".AlarmService" android:exported="false">
            <intent-filter><action android:name="com.google.firebase.MESSAGING_EVENT" /></intent-filter>
        </service>
        <service android:name="com.capacitorjs.plugins.pushnotifications.MessagingService" tools:node="remove" />
        <receiver android:name=".BtReceiver" android:exported="true">
            <intent-filter>
                <action android:name="android.bluetooth.device.action.ACL_CONNECTED" />
                <action android:name="android.bluetooth.device.action.ACL_DISCONNECTED" />
            </intent-filter>
        </receiver>
        <service android:name=".DriveService" android:exported="false" android:foregroundServiceType="location" />
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
