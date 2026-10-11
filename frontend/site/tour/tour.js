// Guided tour engine: narrates each step with the browser's built-in voice (Web Speech API), runs live calls against
// the platform through the storefront's same-origin proxy, and can drive a second window that shows each real tool.
'use strict';

(function () {
  const STEPS = window.TOUR;
  const $ = (s) => document.querySelector(s);
  const el = (tag, props = {}, ...kids) => {
    const n = Object.assign(document.createElement(tag), props);
    for (const k of kids) if (k !== null && k !== undefined) n.append(k);
    return n;
  };
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const CHAPTERS = { why: 'Why microservices', course: 'The course, session by session', live: 'Live platform tour' };
  const TOKEN_URL = '/auth/realms/ecommerce-platform/protocol/openid-connect/token';

  const state = {
    idx: 0, playing: false, auto: true, rate: 1, voice: null, run: 0,
    live: false, toolWin: null, password: '', token: null, tokenExp: 0,
    orderId: null, productId: null,
  };

  // ------------------------------------------------------------------ speech
  const synth = window.speechSynthesis;
  function voices() { return synth ? synth.getVoices().filter((v) => /^en/i.test(v.lang)) : []; }
  function fillVoices() {
    const list = voices();
    const sel = $('#voice');
    if (!list.length) { sel.replaceChildren(el('option', { textContent: synth ? 'Loading voices…' : 'No voice in this browser' })); return; }
    const prefer = ['Google UK English Male', 'Google US English', 'Microsoft Guy', 'Microsoft Aria', 'Daniel', 'Samantha'];
    const best = list.find((v) => prefer.some((p) => v.name.includes(p))) || list[0];
    let saved = null;
    try { saved = localStorage.getItem('tour-voice'); } catch (e) { /* storage blocked */ }
    state.voice = list.find((v) => v.name === saved) || best;
    sel.replaceChildren(...list.map((v) => el('option', { value: v.name, textContent: `${v.name} (${v.lang})`, selected: v === state.voice })));
  }
  if (synth) { fillVoices(); synth.onvoiceschanged = fillVoices; }

  function sentences(text) { return (text.match(/[^.!?]+[.!?]+["')]?|\S[^.!?]*$/g) || [text]).map((s) => s.trim()).filter(Boolean); }

  // Speak sentence by sentence (Chrome cuts long utterances). Resolves false if interrupted.
  async function speak(text, run) {
    if (!text) return true;
    for (const line of sentences(text)) {
      if (run !== state.run || !state.playing) return false;
      $('#caption').textContent = line;
      if (!synth) { await sleep(Math.max(1800, line.length * 55) / state.rate); continue; }
      const ok = await new Promise((resolve) => {
        const u = new SpeechSynthesisUtterance(line);
        if (state.voice) u.voice = state.voice;
        u.rate = state.rate;
        // Some browsers never fire onend (no voice installed, or a Chrome bug): move on after a generous estimate.
        const guard = setTimeout(() => resolve(true), (line.length * 90) / state.rate + 4000);
        u.onend = () => { clearTimeout(guard); resolve(true); };
        u.onerror = (e) => { clearTimeout(guard); resolve(e.error === 'interrupted' || e.error === 'canceled' ? false : true); };
        synth.speak(u);
      });
      if (!ok && run !== state.run) return false;
    }
    return run === state.run && state.playing;
  }
  function hush() { state.run += 1; if (synth) synth.cancel(); }

  // ------------------------------------------------------------------ render
  function renderNav() {
    const nav = $('#nav');
    nav.replaceChildren();
    for (const [key, name] of Object.entries(CHAPTERS)) {
      const list = el('ol');
      STEPS.forEach((s, i) => {
        if (s.chapter !== key) return;
        const label = s.kicker.split('·')[0].trim().startsWith('Session') ? `${s.kicker.split('·')[0].trim()} · ${s.title}` : s.title;
        list.append(el('li', {}, el('button', { className: 'step', textContent: label, onclick: () => go(i, state.playing), id: `nav-${s.id}` })));
      });
      nav.append(el('section', {}, el('h2', { textContent: name }), list));
    }
    $('#jump').replaceChildren(...STEPS.map((s, i) => el('option', { value: i, textContent: `${s.kicker} — ${s.title}` })));
  }

  function render() {
    const s = STEPS[state.idx];
    document.querySelectorAll('#nav .step').forEach((b) => b.classList.remove('now'));
    const cur = $(`#nav-${s.id}`);
    if (cur) { cur.classList.add('now'); cur.scrollIntoView({ block: 'nearest' }); }
    $('#jump').value = state.idx;
    $('#count').textContent = `${state.idx + 1} / ${STEPS.length}`;
    $('#progress').style.width = `${((state.idx + 1) / STEPS.length) * 100}%`;

    const stage = $('#stage');
    const parts = [el('p', { className: 'kicker', textContent: s.kicker }), el('h1', { textContent: s.title })];
    if (s.story) parts.push(el('p', { className: 'story', textContent: s.story }));
    if (s.idea) parts.push(el('div', { className: 'idea' }, el('span', { className: 'label', textContent: 'The idea' }), el('p', { textContent: s.idea })));
    if (s.table) {
      parts.push(el('div', { className: 'scroll' }, el('table', {},
        el('thead', {}, el('tr', {}, ...['Problem', 'Fix', 'Session'].map((h) => el('th', { textContent: h })))),
        el('tbody', {}, ...s.table.map((r) => el('tr', {}, ...r.map((c) => el('td', { textContent: c }))))))));
    }
    if (s.points) parts.push(el('ul', { className: 'points' }, ...s.points.map((p) => el('li', { textContent: p }))));
    if (s.project) parts.push(el('div', { className: 'ours' }, el('span', { className: 'label', textContent: 'In our project' }), el('p', { textContent: s.project })));
    if (s.tool) parts.push(el('p', {}, el('a', { className: 'toollink', href: s.tool.url, target: 't1tools', rel: 'noopener', textContent: `${s.tool.label} ↗` })));
    if (s.live) {
      const box = el('div', { className: 'live', id: 'live' });
      if (state.live) {
        box.append(el('div', { className: 'live-head' }, el('span', { className: 'label', textContent: 'Live result' }),
          el('button', { className: 'small', textContent: 'Run again', onclick: () => runLive(s, state.run) })));
        box.append(el('p', { className: 'muted', id: 'live-body', textContent: 'Press play, or Run again, to call the platform.' }));
      } else {
        box.append(el('span', { className: 'label', textContent: 'Live step' }),
          el('p', { className: 'muted', textContent: 'This step calls the running platform. Start it with docker compose and open http://localhost:8070/tour/ to see the real result here.' }));
      }
      parts.push(box);
    }
    stage.replaceChildren(...parts);
    stage.scrollTop = 0;
    $('#caption').textContent = s.say ? sentences(s.say)[0] : '';
    if (s.tool && state.toolWin && !state.toolWin.closed) {
      try { state.toolWin.location.href = s.tool.url; } catch (e) { /* window gone */ }
    }
  }

  // ------------------------------------------------------------------ play loop
  async function go(i, play) {
    hush();
    state.idx = Math.max(0, Math.min(STEPS.length - 1, i));
    render();
    setPlaying(play);
    if (play) perform(state.run);
  }

  async function perform(run) {
    const s = STEPS[state.idx];
    if (s.needsPassword && state.live && !state.password) {
      $('#pw').focus();
      $('#pw-row').classList.add('ask');
      await speak('Please type the customer password in the box at the top, then press play.', run);
      setPlaying(false);
      return;
    }
    const livePromise = s.live && state.live ? runLive(s, run) : Promise.resolve(null);
    const spoke = await speak(s.say, run);
    const result = await livePromise;
    if (!spoke) return;
    if (result && result.say && !(await speak(result.say, run))) return;
    if (!state.auto) { setPlaying(false); return; }
    await sleep(1100);
    if (run !== state.run || !state.playing) return;
    if (state.idx < STEPS.length - 1) go(state.idx + 1, true); else setPlaying(false);
  }

  function setPlaying(on) {
    state.playing = on;
    $('#play').textContent = on ? 'Pause' : 'Play';
    $('#play').setAttribute('aria-pressed', String(on));
    document.body.classList.toggle('playing', on);
  }

  // ------------------------------------------------------------------ live calls
  async function call(method, path, { body, auth, headers = {}, form } = {}) {
    const h = { ...headers };
    if (auth) h.Authorization = `Bearer ${await token()}`;
    let payload;
    if (form) payload = new URLSearchParams(form);
    else if (body !== undefined) { h['Content-Type'] = 'application/json'; payload = JSON.stringify(body); }
    const t0 = performance.now();
    const res = await fetch(path, { method, headers: h, body: payload });
    const ms = Math.round(performance.now() - t0);
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (e) { data = text; }
    return { status: res.status, ok: res.ok, data, ms };
  }

  async function token() {
    if (state.token && Date.now() < state.tokenExp) return state.token;
    if (!state.password) throw new Error('Type the customer password first.');
    const r = await call('POST', TOKEN_URL, { form: { grant_type: 'password', client_id: 'user-sign-in', username: 'customer-test', password: state.password } });
    if (!r.ok) { state.password = ''; $('#pw').value = ''; throw new Error(r.status === 401 ? 'Keycloak rejected the password.' : `Keycloak answered ${r.status}.`); }
    state.token = r.data.access_token;
    state.tokenExp = Date.now() + (r.data.expires_in - 20) * 1000;
    return state.token;
  }

  const claims = (jwt) => JSON.parse(decodeURIComponent(escape(atob(jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))));
  const code = (status) => el('span', { className: `code c${String(status)[0]}`, textContent: status });
  const row = (status, text, ms) => el('li', {}, code(status), el('span', { className: 'what', textContent: text }), ms !== undefined ? el('span', { className: 'ms', textContent: `${ms} ms` }) : null);
  const kv = (pairs) => el('dl', { className: 'kv' }, ...pairs.flatMap(([k, v]) => [el('dt', { textContent: k }), el('dd', { textContent: v })]));

  const LIVE = {
    async ping() {
      const r = await call('GET', '/api/v1/products?page=0&size=1');
      return {
        node: el('ul', { className: 'calls' }, row(r.status, `GET /api/v1/products through the Gateway — ${r.ok ? `${r.data.page.totalElements} products in the catalogue` : 'not answering'}`, r.ms)),
        say: r.ok ? `The gateway answered in ${r.ms} milliseconds. The platform is up.` : `The gateway answered ${r.status}. Check that every container is healthy.`,
      };
    },
    async config() {
      const r = await call('GET', '/tour-api/config/order-service/default');
      if (!r.ok) return { node: el('p', { className: 'bad', textContent: `Config Server answered ${r.status}.` }) };
      const src = Object.assign({}, ...r.data.propertySources.slice().reverse().map((p) => p.source));
      const keys = Object.keys(src).filter((k) => k.startsWith('resilience4j.')).sort().slice(0, 14);
      return {
        node: el('div', {}, el('p', { className: 'muted', textContent: `Sources: ${r.data.propertySources.map((p) => p.name.split('/').slice(-1)[0]).join(', ')}` }),
          kv(keys.map((k) => [k.replace('resilience4j.', ''), String(src[k])]))),
        say: `Config server sent ${keys.length} resilience settings for the order service, read from the config repo.`,
      };
    },
    async eureka() {
      const r = await call('GET', '/tour-api/eureka/apps', { headers: { Accept: 'application/json' } });
      if (!r.ok) return { node: el('p', { className: 'bad', textContent: `Eureka answered ${r.status}.` }) };
      const apps = (r.data.applications.application || []).slice().sort((a, b) => a.name.localeCompare(b.name));
      return {
        node: el('ul', { className: 'calls' }, ...apps.map((a) => {
          const up = a.instance.filter((x) => x.status === 'UP').length;
          return el('li', {}, el('span', { className: `code ${up ? 'c2' : 'c5'}`, textContent: up ? 'UP' : 'DOWN' }),
            el('span', { className: 'what', textContent: a.name }), el('span', { className: 'ms', textContent: `${a.instance.length} instance${a.instance.length > 1 ? 's' : ''}` }));
        })),
        say: `${apps.length} applications are registered in Eureka right now.`,
      };
    },
    async gateway() {
      const a = await call('GET', '/api/v1/products?page=0&size=4');
      const b = await call('POST', '/api/v1/products', { body: { name: 'Should be refused', price: 1, categoryId: 1 } });
      const names = a.ok ? a.data.content.map((p) => p.name).join(', ') : '';
      return {
        node: el('ul', { className: 'calls' }, row(a.status, `GET /api/v1/products (anonymous) — ${names}`, a.ms), row(b.status, 'POST /api/v1/products with no token', b.ms)),
        say: `The anonymous read answered ${a.status}, and the write without a token answered ${b.status}.`,
      };
    },
    async signin() {
      state.token = null;
      const t = await token();
      const c = claims(t);
      const roles = ((c.realm_access && c.realm_access.roles) || []).filter((x) => /^[A-Z]+$/.test(x)).join(', ');
      return {
        node: kv([['user (preferred_username)', c.preferred_username], ['sub (user id)', c.sub], ['azp (client)', c.azp], ['roles', roles],
          ['iss (issuer)', c.iss], ['expires in', `${Math.round((c.exp * 1000 - Date.now()) / 60000)} minutes`]]),
        say: `Signed in as ${c.preferred_username}, with the role ${roles || 'none'}. The token expires in about ${Math.round((c.exp * 1000 - Date.now()) / 60000)} minutes.`,
      };
    },
    async forbidden() {
      const r = await call('POST', '/api/v1/products', { auth: true, body: { name: 'Customer should not create this', price: 1, categoryId: 1 } });
      return { node: el('ul', { className: 'calls' }, row(r.status, 'POST /api/v1/products as CUSTOMER', r.ms)), say: `The gateway answered ${r.status}.` };
    },
    async cache() {
      const list = await call('GET', '/api/v1/products?page=0&size=1');
      const id = list.ok && list.data.content[0] ? list.data.content[0].id : 1;
      const rows = [];
      for (let i = 0; i < 5; i += 1) {
        const r = await call('GET', `/api/v1/products/${id}`);
        rows.push(row(r.status, `GET /api/v1/products/${id} — read ${i + 1}${i === 0 ? ' (may fill the cache)' : ' (from Redis)'}`, r.ms));
      }
      return { node: el('ul', { className: 'calls' }, ...rows), say: `Five reads of product ${id}. After the first, each one is served from Redis key products item ${id}.` };
    },
    async burst() {
      const t0 = performance.now();
      const res = await Promise.all(Array.from({ length: 250 }, () => fetch('/api/v1/products/1').then((r) => r.status).catch(() => 0)));
      const ms = Math.round(performance.now() - t0);
      const ok = res.filter((x) => x === 200).length;
      const lim = res.filter((x) => x === 429).length;
      return {
        node: el('ul', { className: 'calls' }, row(200, `${ok} requests served`, ms), row(429, `${lim} requests limited by the token bucket`)),
        say: lim ? `${ok} were served and ${lim} got 429. The token bucket ran dry.` : `All ${ok} were served in ${ms} milliseconds: the browser could not send faster than the limit. The k6 rate limit script shows the 429.`,
      };
    },
    async order() {
      const list = await call('GET', '/api/v1/products?page=0&size=20');
      const candidates = list.ok ? list.data.content.map((p) => p.id) : [1, 2, 3];
      const rows = [];
      for (const id of candidates) {
        const r = await call('POST', '/api/v1/orders', { auth: true, body: { items: [{ productId: id, quantity: 1 }] } });
        rows.push(row(r.status, `POST /api/v1/orders — product ${id}${r.status === 409 ? ' (out of stock, refused before payment)' : ''}`, r.ms));
        if (r.status === 201) {
          state.orderId = r.data.orderId; state.productId = id;
          rows.push(el('li', {}, el('span', { className: 'code c2', textContent: r.data.status }), el('span', { className: 'what', textContent: `orderId ${r.data.orderId}` })));
          return { node: el('ul', { className: 'calls' }, ...rows), say: `Order created in ${r.ms} milliseconds, with status ${r.data.status}.` };
        }
        if (r.status !== 409) return { node: el('ul', { className: 'calls' }, ...rows), say: `The order service answered ${r.status}.` };
      }
      return { node: el('ul', { className: 'calls' }, ...rows), say: 'Every product on the first page is out of stock. An admin can add stock in the storefront.' };
    },
    async saga() {
      if (!state.orderId) await LIVE.order();
      if (!state.orderId) return { node: el('p', { className: 'bad', textContent: 'No order to follow.' }) };
      const track = el('ol', { className: 'track' }, ...['Order saved (PENDING)', 'Stock reserved', 'Payment charged once', 'CONFIRMED + notice'].map((t) => el('li', { textContent: t })));
      const body = $('#live-body');
      if (body) body.replaceWith(el('div', { id: 'live-body' }, track));
      const steps = [...track.children];
      steps[0].className = 'on';
      const t0 = Date.now();
      for (let i = 0; i < 40; i += 1) {
        await sleep(i < 10 ? 300 : 1000);
        const r = await call('GET', `/api/v1/orders/${state.orderId}`, { auth: true });
        const st = r.ok && r.data.status;
        if (i === 1) steps[1].className = 'on';
        if (i === 2) steps[2].className = 'now';
        if (st === 'CONFIRMED') {
          steps.forEach((x) => { x.className = 'on'; });
          return { node: track, say: `The order is confirmed, ${((Date.now() - t0) / 1000).toFixed(1)} seconds after it was placed.` };
        }
        if (st === 'CANCELLED') {
          steps[2].className = 'fail'; steps[3].className = 'fail'; steps[3].textContent = 'CANCELLED, stock released';
          return { node: track, say: 'The order was cancelled and the stock was released. That is compensation.' };
        }
      }
      return { node: track, say: 'The order is still pending. If payment is stopped, it waits safely in Kafka.' };
    },
    async zipkin() {
      const r = await call('GET', '/tour-api/zipkin/api/v2/traces?serviceName=order-service&lookback=600000&limit=25');
      if (!r.ok || !Array.isArray(r.data) || !r.data.length) return { node: el('p', { className: 'muted', textContent: 'No recent order trace yet. Place an order first.' }) };
      const services = (t) => new Set(t.map((sp) => sp.localEndpoint && sp.localEndpoint.serviceName).filter(Boolean));
      const best = r.data.slice().sort((a, b) => services(b).size - services(a).size || b.length - a.length)[0];
      const start = Math.min(...best.map((sp) => sp.timestamp || Infinity));
      const end = Math.max(...best.map((sp) => (sp.timestamp || 0) + (sp.duration || 0)));
      const names = [...services(best)].sort();
      const url = `http://localhost:9411/zipkin/traces/${best[0].traceId}`;
      return {
        node: el('div', {}, kv([['trace id', best[0].traceId], ['spans', String(best.length)], ['services', names.join(', ')], ['total time', `${Math.round((end - start) / 1000)} ms`]]),
          el('p', {}, el('a', { className: 'toollink', href: url, target: 't1tools', rel: 'noopener', textContent: 'Open this trace in Zipkin ↗' }))),
        say: `One trace with ${best.length} spans across ${names.length} services.`,
        url,
      };
    },
    async review() {
      const pid = state.productId || 1;
      const r = await call('POST', `/api/v1/products/${pid}/reviews`, { auth: true, body: { rating: 5, text: 'Great product. Posted from the live tour.' } });
      await sleep(1500);
      const p = await call('GET', `/api/v1/products/${pid}`);
      const rating = p.ok && p.data.reviewCount ? `${Number(p.data.averageRating).toFixed(1)} from ${p.data.reviewCount} review(s)` : 'no reviews yet';
      return {
        node: el('ul', { className: 'calls' }, row(r.status, `POST /api/v1/products/${pid}/reviews${r.status === 409 ? ' (this customer already reviewed it)' : ''}`, r.ms),
          row(p.status, `GET /api/v1/products/${pid} — rating ${rating}`, p.ms)),
        say: r.status === 201 ? `Review saved. The product now shows ${rating}.` : r.status === 409 ? `409: one review per customer. The product shows ${rating}.` : `The review service answered ${r.status}.`,
      };
    },
    async prometheus() {
      const r = await call('GET', '/tour-api/prometheus/api/v1/query?query=up');
      if (!r.ok) return { node: el('p', { className: 'bad', textContent: `Prometheus answered ${r.status}.` }) };
      const res = r.data.data.result.slice().sort((a, b) => a.metric.job.localeCompare(b.metric.job));
      const up = res.filter((x) => x.value[1] === '1').length;
      return {
        node: el('ul', { className: 'calls' }, ...res.map((x) => el('li', {}, el('span', { className: `code ${x.value[1] === '1' ? 'c2' : 'c5'}`, textContent: `up ${x.value[1]}` }),
          el('span', { className: 'what', textContent: x.metric.job }), el('span', { className: 'ms', textContent: x.metric.instance })))),
        say: `${up} of ${res.length} targets are up.`,
      };
    },
  };

  async function runLive(s, run) {
    const body = () => $('#live-body');
    if (body()) body().replaceChildren(el('span', { className: 'spinner', textContent: 'Calling the platform…' }));
    try {
      const out = await LIVE[s.live]();
      if (STEPS[state.idx] === s && body()) body().replaceChildren(out.node);
      if (out.url && state.toolWin && !state.toolWin.closed) { try { state.toolWin.location.href = out.url; } catch (e) { /* ignore */ } }
      return out;
    } catch (e) {
      if (body()) body().replaceChildren(el('p', { className: 'bad', textContent: e.message }));
      return { say: e.message };
    }
  }

  // ------------------------------------------------------------------ wiring
  async function detectLive() {
    if (!/^(localhost|127\.0\.0\.1)$/.test(location.hostname)) return false;
    try {
      const ctl = new AbortController();
      setTimeout(() => ctl.abort(), 2500);
      const r = await fetch('/api/v1/products?page=0&size=1', { signal: ctl.signal });
      return r.status < 500;
    } catch (e) { return false; }
  }

  $('#play').onclick = () => { if (state.playing) { hush(); setPlaying(false); } else { setPlaying(true); perform(state.run); } };
  $('#prev').onclick = () => go(state.idx - 1, state.playing);
  $('#next').onclick = () => go(state.idx + 1, state.playing);
  $('#jump').onchange = (e) => go(Number(e.target.value), state.playing);
  $('#auto').onchange = (e) => { state.auto = e.target.checked; };
  $('#rate').onchange = (e) => { state.rate = Number(e.target.value); };
  $('#voice').onchange = (e) => {
    state.voice = voices().find((v) => v.name === e.target.value) || null;
    try { localStorage.setItem('tour-voice', e.target.value); } catch (err) { /* ignore */ }
  };
  $('#pw').oninput = (e) => { state.password = e.target.value; state.token = null; $('#pw-row').classList.remove('ask'); };
  $('#toolwin').onclick = () => {
    state.toolWin = window.open('about:blank', 't1tools');
    const s = STEPS[state.idx];
    if (state.toolWin && s.tool) state.toolWin.location.href = s.tool.url;
    $('#toolwin').textContent = state.toolWin ? 'Tool window open' : 'Pop-ups blocked';
  };
  document.querySelectorAll('[data-chapter]').forEach((b) => {
    b.onclick = () => go(STEPS.findIndex((s) => s.chapter === b.dataset.chapter), false);
  });
  document.addEventListener('keydown', (e) => {
    if (e.target.matches('input, select, textarea')) return;
    if (e.key === ' ') { e.preventDefault(); $('#play').click(); }
    if (e.key === 'ArrowRight') $('#next').click();
    if (e.key === 'ArrowLeft') $('#prev').click();
  });

  renderNav();
  render();
  detectLive().then((on) => {
    state.live = on;
    $('#mode').textContent = on ? 'Live: connected to the platform' : 'Study mode: live steps need the platform on localhost:8070';
    $('#mode').className = on ? 'mode on' : 'mode';
    $('#pw-row').hidden = !on;
    $('#toolwin').hidden = !on;
    render();
  });
})();
