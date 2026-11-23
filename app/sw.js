// Scan2Com — service worker: instalable + notificaciones push con la app cerrada
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', e => e.waitUntil(self.clients.claim()));
self.addEventListener('fetch', () => {});

self.addEventListener('push', e => {
  let d = {}; try { d = e.data.json(); } catch (x) { d = { title: 'Scan2Com', body: e.data ? e.data.text() : '' }; }
  const opt = {
    body: d.body || '', icon: '/app/icon-192.png', badge: '/app/icon-192.png', tag: d.tag || 'scan2com', renotify: true,
    data: { url: d.url || '/app/' }
  };
  if (d.alarm) { opt.requireInteraction = true; opt.vibrate = [600, 200, 600, 200, 600, 200, 600]; opt.image = '/app/alert_red.png'; }
  else opt.vibrate = [200, 100, 200];
  e.waitUntil((async () => {
    // si la app está a la vista, ella ya avisa (sirena / aviso dentro)
    const cs = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    if (!d.alarm && cs.some(c => c.visibilityState === 'visible' && c.focused)) return;
    await self.registration.showNotification(d.title || 'Scan2Com', opt);
  })());
});

self.addEventListener('notificationclick', e => {
  e.notification.close();
  const url = e.notification.data?.url || '/app/';
  e.waitUntil((async () => {
    const cs = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    const c = cs.find(x => new URL(x.url).pathname.startsWith('/app'));
    if (c) { await c.focus(); c.postMessage({ type: 'open', url }); return; }
    return self.clients.openWindow(url);
  })());
});
