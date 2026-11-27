// Scan2Com · llamadas de voz por la app (WebRTC). Lo usan la app (/app/) y la web de escaneo (scan.html).
// La voz va directa entre los dos aparatos. El servidor solo guarda la llamada (tabla calls) y avisa al otro lado.
// Señalización: canal de Supabase Realtime "call:<id>" (mensajes ready / offer / answer / ice / bye).
//
//   S2CCall.open({ sb, rpc, id, role: 'caller' | 'callee', name, token, answer, onEnd })
//     sb    cliente de Supabase (para el canal)
//     rpc   (fn, args) => Promise<data>  (cada página llama a sus funciones a su manera)
//     token token del chat de quien escaneó (web sin cuenta); null en la app
//     answer true: contestar sin preguntar (se pulsó "Contestar" en la notificación de Android)
(() => {
  const ICE = [{ urls: ['stun:stun.l.google.com:19302', 'stun:stun1.l.google.com:19302'] }];
  const RING_MS = 45000;
  let cur = null;
  const css = `
.s2cc{position:fixed;inset:0;z-index:2000;background:linear-gradient(180deg,#10302a,#0b1b18 60%,#050b0a);color:#fff;display:flex;flex-direction:column;align-items:center;font-family:system-ui,-apple-system,Roboto,sans-serif;padding:calc(env(safe-area-inset-top) + 56px) 20px calc(env(safe-area-inset-bottom) + 40px)}
.s2cc .av{width:116px;height:116px;border-radius:50%;background:#1f5c4f;display:flex;align-items:center;justify-content:center;font-size:52px;font-weight:600;box-shadow:0 0 0 0 rgba(47,194,58,.5)}
.s2cc.ring .av{animation:s2ccp 1.6s infinite}
@keyframes s2ccp{0%{box-shadow:0 0 0 0 rgba(47,194,58,.55)}100%{box-shadow:0 0 0 34px rgba(47,194,58,0)}}
.s2cc .nm{font-size:28px;font-weight:600;margin-top:26px;text-align:center;max-width:100%;overflow:hidden;text-overflow:ellipsis}
.s2cc .st{font-size:16px;opacity:.75;margin-top:8px;min-height:22px;font-variant-numeric:tabular-nums}
.s2cc .br{font-size:13px;opacity:.5;margin-top:4px}
.s2cc .bt{margin-top:auto;display:flex;gap:44px;justify-content:center;align-items:flex-end}
.s2cc .b{display:flex;flex-direction:column;align-items:center;gap:8px;font-size:13px;opacity:.95;background:none;border:0;color:#fff;padding:0}
.s2cc .b i{width:72px;height:72px;border-radius:50%;display:flex;align-items:center;justify-content:center;background:rgba(255,255,255,.14)}
.s2cc .b.red i{background:#e53935}.s2cc .b.green i{background:#2fc23a}.s2cc .b.on i{background:#fff;color:#10302a}
.s2cc .b.green i{animation:s2ccb 1.2s infinite}@keyframes s2ccb{50%{transform:translateY(-6px)}}
.s2cc svg{width:32px;height:32px}`;
  const SV = p => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${p}</svg>`;
  const PH = '<path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1.9.4 1.8.7 2.7a2 2 0 0 1-.5 2.1L8.1 9.8a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.7.7a2 2 0 0 1 1.8 2z"/>';
  const IC = {
    up: SV(PH.replace('<path', '<path fill="currentColor" stroke="none"')),
    down: `<svg viewBox="0 0 24 24" style="transform:rotate(135deg)"><path fill="currentColor" d="${PH.match(/d="([^"]+)"/)[1]}"/></svg>`,
    mic: SV('<rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 10a7 7 0 0 0 14 0M12 19v3"/>'),
    micOff: SV('<path d="M2 2l20 20M9 9v1a3 3 0 0 0 5.1 2.1M15 9.3V5a3 3 0 0 0-5.9-.6M17 16.9A7 7 0 0 1 5 10M19 10a7 7 0 0 1-.1 1.2M12 19v3"/>'),
    spk: SV('<path d="M11 5 6 9H2v6h4l5 4z" fill="currentColor"/><path d="M15.5 8.5a5 5 0 0 1 0 7M19 5a10 10 0 0 1 0 14"/>'),
  };
  // tonos: llamada saliente (425 Hz, como en España) y entrante (dos tonos)
  let ac = null, toneT = null;
  function tone(kind) {
    stopTone();
    try { ac = ac || new (window.AudioContext || window.webkitAudioContext)(); ac.resume?.(); } catch (e) { return; }
    const beep = (f, ms) => { const o = ac.createOscillator(), g = ac.createGain(); o.frequency.value = f; g.gain.value = 0; o.connect(g).connect(ac.destination); const t = ac.currentTime; g.gain.linearRampToValueAtTime(0.18, t + 0.03); g.gain.setValueAtTime(0.18, t + ms / 1000 - 0.05); g.gain.linearRampToValueAtTime(0, t + ms / 1000); o.start(t); o.stop(t + ms / 1000 + 0.05); };
    const play = kind === 'back' ? () => beep(425, 1500) : () => { beep(880, 400); setTimeout(() => beep(660, 400), 450); setTimeout(() => { beep(880, 400); setTimeout(() => beep(660, 400), 450); }, 1000); };
    play(); toneT = setInterval(play, kind === 'back' ? 4500 : 3500);
    if (kind === 'in' && navigator.vibrate) navigator.vibrate([600, 400, 600, 400, 600]);
  }
  function stopTone() { clearInterval(toneT); toneT = null; if (navigator.vibrate) navigator.vibrate(0); }
  const nativeStop = done => { try { const A = window.Capacitor?.Plugins?.ScanAuto; done ? A?.callDone?.() : A?.callStop?.(); } catch (e) {} };
  const mmss = s => Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0');

  function open(o) {
    if (cur && cur.id === o.id) return cur;
    if (cur) cur.end('ended', true);
    if (!document.getElementById('s2ccss')) { const st = document.createElement('style'); st.id = 's2ccss'; st.textContent = css; document.head.appendChild(st); }
    const el = document.createElement('div'); el.className = 's2cc ring';
    const ini = (o.name || '?').trim().charAt(0).toUpperCase() || '?';
    el.innerHTML = `<div class="av">${ini}</div><div class="nm"></div><div class="st"></div><div class="br">Llamada de Scan2Com · sin números de teléfono</div><div class="bt"></div><audio autoplay playsinline></audio>`;
    el.querySelector('.nm').textContent = o.name || 'Scan2Com';
    document.body.appendChild(el);
    const $st = el.querySelector('.st'), $bt = el.querySelector('.bt'), au = el.querySelector('audio');
    const c = cur = { id: o.id, el, state: 'ringing', muted: false };
    let pc = null, mic = null, ch = null, t0 = 0, tick = null, ringT = null, readyT = null, gotOffer = false, iceQ = [], done = false;
    const status = t => { $st.textContent = t; };
    const send = (event, payload = {}) => ch?.send({ type: 'broadcast', event, payload });
    const buttons = list => { $bt.innerHTML = list.map(b => `<button class="b ${b.c || ''}" data-k="${b.k}"><i>${b.i}</i>${b.t}</button>`).join(''); list.forEach(b => { $bt.querySelector(`[data-k="${b.k}"]`).onclick = b.f; }); };
    const inCall = () => buttons([
      { k: 'mute', t: 'Silenciar', i: IC.mic, f: () => { c.muted = !c.muted; mic?.getAudioTracks().forEach(t => t.enabled = !c.muted); const b = $bt.querySelector('[data-k="mute"]'); b.classList.toggle('on', c.muted); b.querySelector('i').innerHTML = c.muted ? IC.micOff : IC.mic; } },
      { k: 'end', t: 'Colgar', c: 'red', i: IC.down, f: () => end('ended') },
    ]);
    async function getMic() {
      try { mic = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true } }); return true; }
      catch (e) { status('Sin permiso de micrófono'); setTimeout(() => end(o.role === 'caller' ? 'ended' : 'declined'), 1800); return false; }
    }
    function makePc() {
      pc = new RTCPeerConnection({ iceServers: ICE });
      mic.getTracks().forEach(t => pc.addTrack(t, mic));
      pc.ontrack = e => { au.srcObject = e.streams[0]; au.play?.().catch(() => {}); };
      pc.onicecandidate = e => { if (e.candidate) send('ice', { c: e.candidate.toJSON() }); };
      pc.onconnectionstatechange = () => {
        if (pc.connectionState === 'connected' && !t0) { stopTone(); el.classList.remove('ring'); t0 = Date.now(); tick = setInterval(() => status(mmss(Math.round((Date.now() - t0) / 1000))), 1000); status('0:00'); }
        if (pc.connectionState === 'failed') { status('No se ha podido conectar'); setTimeout(() => end('ended'), 1500); }
      };
    }
    const addIce = async cand => { try { await pc.addIceCandidate(cand); } catch (e) {} };
    async function onSignal(ev, p) {
      if (done) return;
      if (ev === 'bye') { status(p.r === 'declined' ? 'Llamada rechazada' : 'Llamada finalizada'); return end(null); }
      if (ev === 'ready' && o.role === 'caller' && !c.started) {
        c.started = true; clearTimeout(ringT); stopTone(); status('Conectando…');
        if (!await c.micP || done) return; makePc();
        await pc.setLocalDescription(await pc.createOffer()); send('offer', { sdp: pc.localDescription.toJSON() }); inCall();
      }
      if (ev === 'offer' && o.role === 'callee' && !gotOffer) {
        gotOffer = true; clearInterval(readyT); makePc();
        await pc.setRemoteDescription(p.sdp); for (const q of iceQ) await addIce(q); iceQ = [];
        await pc.setLocalDescription(await pc.createAnswer()); send('answer', { sdp: pc.localDescription.toJSON() });
      }
      if (ev === 'answer' && o.role === 'caller' && pc && !pc.currentRemoteDescription) { await pc.setRemoteDescription(p.sdp); for (const q of iceQ) await addIce(q); iceQ = []; }
      if (ev === 'ice') { if (pc?.remoteDescription) addIce(p.c); else iceQ.push(p.c); }
    }
    async function end(state, silent) {
      if (done) return; done = true; c.state = 'ended';
      stopTone(); nativeStop(true); clearInterval(tick); clearTimeout(ringT); clearInterval(readyT);
      if (state) { send('bye', { r: state }); try { await o.rpc('call_set', { p_call: o.id, p_state: state, p_token: o.token || null }); } catch (e) {} }
      try { pc?.close(); } catch (e) {} mic?.getTracks().forEach(t => t.stop());
      setTimeout(() => { try { ch && o.sb.removeChannel(ch); } catch (e) {} }, 300);
      if (!state && !silent) setTimeout(() => el.remove(), 1400); else el.remove();
      if (cur === c) cur = null; o.onEnd?.();
    }
    c.end = end;
    c.remote = st => { if (!done && st !== 'ringing' && !pc && !c.answering) { status(st === 'answered' ? 'Contestada en otro aparato' : 'Llamada finalizada'); end(null); } };
    ch = o.sb.channel('call:' + o.id, { config: { broadcast: { self: false } } });
    ['ready', 'offer', 'answer', 'ice', 'bye'].forEach(ev => ch.on('broadcast', { event: ev }, m => onSignal(ev, m.payload || {})));
    const joined = new Promise(ok => ch.subscribe(s => { if (s === 'SUBSCRIBED') ok(); }));
    async function answer() {
      c.answering = true; stopTone(); nativeStop(); status('Conectando…'); buttons([{ k: 'end', t: 'Colgar', c: 'red', i: IC.down, f: () => end('ended') }]);
      if (!await getMic()) return;
      await joined; try { await o.rpc('call_set', { p_call: o.id, p_state: 'answered', p_token: o.token || null }); } catch (e) {}
      let n = 0; send('ready'); readyT = setInterval(() => { if (++n > 15 || gotOffer) return clearInterval(readyT); send('ready'); }, 1000);
      inCall();
    }
    if (o.role === 'caller') {
      status('Llamando…'); tone('back'); buttons([{ k: 'end', t: 'Colgar', c: 'red', i: IC.down, f: () => end('ended') }]);
      c.micP = getMic();
      ringT = setTimeout(() => { status('No contesta'); setTimeout(() => end('missed'), 1200); }, RING_MS);
    } else if (o.answer) { answer(); }
    else {
      status('Te está llamando…'); tone('in');
      buttons([{ k: 'no', t: 'Rechazar', c: 'red', i: IC.down, f: () => end('declined') }, { k: 'yes', t: 'Contestar', c: 'green', i: IC.up, f: answer }]);
      ringT = setTimeout(() => end(null, true), RING_MS);
    }
    return c;
  }
  window.S2CCall = { open, get current() { return cur; } };
})();
