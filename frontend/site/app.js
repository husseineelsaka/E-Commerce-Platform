// Team 1 Market — a thin browser client for the platform. Every call goes to the same origin:
// /api/* is proxied to the API Gateway, /auth/* to Keycloak (see nginx.conf). No framework, no build step.
'use strict';

const REALM = '/auth/realms/ecommerce-platform/protocol/openid-connect/token';
const $ = (sel) => document.querySelector(sel);
const el = (tag, props = {}, ...kids) => {
  const node = Object.assign(document.createElement(tag), props);
  for (const kid of kids) node.append(kid);
  return node;
};
const money = (v) => Number(v).toFixed(2);

// ---------------------------------------------------------------- session
const session = { access: null, refresh: null, expiresAt: 0, user: null, roles: [] };

function decode(jwt) {
  const part = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  return JSON.parse(decodeURIComponent(escape(atob(part))));
}

function keep(tokens) {
  session.access = tokens.access_token;
  session.refresh = tokens.refresh_token;
  session.expiresAt = Date.now() + (tokens.expires_in - 30) * 1000;
  const claims = decode(tokens.access_token);
  session.user = claims.preferred_username;
  session.roles = (claims.realm_access && claims.realm_access.roles) || [];
  try { sessionStorage.setItem('t1-refresh', session.refresh); } catch (e) { /* storage blocked: stay in memory */ }
  renderWho();
}

async function tokenRequest(form) {
  const started = performance.now();
  const res = await fetch(REALM, { method: 'POST', body: new URLSearchParams(form) });
  log('POST', '/realms/…/token (Keycloak)', res.status, performance.now() - started);
  if (!res.ok) throw new Error(res.status === 401 ? 'Wrong username or password.' : `Keycloak answered ${res.status}.`);
  return res.json();
}

async function signIn(username, password) {
  keep(await tokenRequest({ grant_type: 'password', client_id: 'user-sign-in', username, password }));
}

async function freshToken() {
  if (!session.access) return null;
  if (Date.now() < session.expiresAt) return session.access;
  try {
    keep(await tokenRequest({ grant_type: 'refresh_token', client_id: 'user-sign-in', refresh_token: session.refresh }));
    return session.access;
  } catch (e) {
    signOut();
    toast('Your session ended. Sign in again.', true);
    return null;
  }
}

function signOut() {
  Object.assign(session, { access: null, refresh: null, expiresAt: 0, user: null, roles: [] });
  try { sessionStorage.removeItem('t1-refresh'); } catch (e) { /* ignore */ }
  renderWho();
  show('shop');
}

const has = (role) => session.roles.includes(role);

// ---------------------------------------------------------------- API + live request log
async function api(method, path, body, { auth = true } = {}) {
  const headers = {};
  if (auth) {
    const token = await freshToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const started = performance.now();
  const res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  log(method, path, res.status, performance.now() - started);
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch (e) { data = text; }
  return { status: res.status, ok: res.ok, data };
}

function log(method, path, status, ms) {
  const list = $('#log');
  list.querySelector('.empty')?.remove();
  const row = el('li', { className: 'fresh' },
    el('span', { className: `code c${String(status)[0]}`, textContent: status }),
    el('span', { className: 'path', textContent: `${method} ${path}`, title: `${method} ${path}` }),
    el('span', { className: 'ms', textContent: `${Math.round(ms)} ms` }));
  list.prepend(row);
  setTimeout(() => row.classList.remove('fresh'), 1200);
  while (list.children.length > 60) list.lastChild.remove();
}

function problem(res, fallback) {
  const known = { 401: 'Sign in first.', 403: 'Your role is not allowed to do this.', 409: null, 429: 'Too many requests: the Gateway rate limit kicked in. Wait a second.', 503: 'A service is unavailable right now (circuit breaker). Try again shortly.' };
  if (known[res.status]) return known[res.status];
  if (res.data && res.data.message) return res.data.message;
  return fallback || `Request failed (${res.status}).`;
}

let toastTimer;
function toast(message, bad = false) {
  const t = $('#toast');
  t.textContent = message;
  t.className = bad ? 'toast bad' : 'toast';
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.hidden = true; }, 3800);
}

// ---------------------------------------------------------------- header and navigation
function renderWho() {
  const who = $('#who');
  who.replaceChildren();
  if (session.user) {
    for (const r of session.roles.filter((x) => x === 'ADMIN' || x === 'CUSTOMER')) who.append(el('span', { className: 'role', textContent: r.toLowerCase() }));
    who.append(el('span', { textContent: session.user }));
    who.append(el('button', { className: 'btn ghost small', textContent: 'Sign out', onclick: signOut }));
  } else {
    who.append(el('button', { className: 'btn', textContent: 'Sign in', onclick: () => $('#signin').showModal() }));
  }
  for (const tab of document.querySelectorAll('.tab[data-need]')) tab.hidden = !has(tab.dataset.need);
}

function show(view) {
  for (const tab of document.querySelectorAll('.tab')) {
    if (tab.dataset.view === view) tab.setAttribute('aria-current', 'page'); else tab.removeAttribute('aria-current');
  }
  for (const v of document.querySelectorAll('.view')) v.hidden = v.id !== `view-${view}`;
  if (view === 'shop') loadProducts();
  if (view === 'orders') loadOrders();
  if (view === 'admin') loadAdmin();
}

// ---------------------------------------------------------------- shop
const shop = { page: 0, size: 12, totalPages: 1 };

function stars(product) {
  if (!product.reviewCount) return el('span', { className: 'stars', textContent: 'No reviews yet' });
  return el('span', { className: 'stars' },
    el('b', { textContent: `${Number(product.averageRating).toFixed(1)} / 5` }),
    ` from ${product.reviewCount} review${product.reviewCount === 1 ? '' : 's'}`);
}

async function loadProducts() {
  const res = await api('GET', `/api/v1/products?page=${shop.page}&size=${shop.size}`, undefined, { auth: false });
  const grid = $('#products');
  if (!res.ok) { grid.replaceChildren(el('p', { className: 'error', textContent: problem(res, 'Could not load the catalogue.') })); return; }
  shop.totalPages = res.data.page.totalPages || 1;
  $('#pageinfo').textContent = `Page ${shop.page + 1} of ${shop.totalPages}`;
  $('#prev').disabled = shop.page === 0;
  $('#next').disabled = shop.page + 1 >= shop.totalPages;
  grid.replaceChildren(...res.data.content.map((p) => el('button', { className: 'item', onclick: () => openProduct(p.id) },
    el('span', { className: 'cat', textContent: p.categoryName }),
    el('span', { className: 'name', textContent: p.name }),
    stars(p),
    el('span', { className: 'price', textContent: money(p.price) }))));
}

async function openProduct(id) {
  const [prod, reviews] = await Promise.all([
    api('GET', `/api/v1/products/${id}`, undefined, { auth: false }),
    api('GET', `/api/v1/products/${id}/reviews?page=0&size=20`, undefined, { auth: false })]);
  if (!prod.ok) { toast(problem(prod, 'Could not open the product.'), true); return; }
  const p = prod.data;
  const body = el('div', { className: 'detail' },
    el('span', { className: 'hint', textContent: p.categoryName }),
    el('h2', { textContent: p.name }),
    el('span', { className: 'price', textContent: money(p.price) }),
    stars(p));

  if (has('CUSTOMER')) {
    const qty = el('input', { type: 'number', min: 1, value: 1, id: 'qty' });
    const buy = el('button', { className: 'btn', textContent: 'Place order' });
    buy.onclick = async () => {
      buy.disabled = true;
      await placeOrder(p.id, Number(qty.value));
      buy.disabled = false;
    };
    body.append(el('div', { className: 'buy' }, el('label', {}, 'Quantity', qty), buy));
  } else {
    body.append(el('p', { className: 'hint', textContent: 'Sign in as a customer to order or review this product.' }));
  }

  body.append(el('h3', { textContent: 'Reviews' }));
  const list = el('ul', { className: 'reviews' });
  const items = (reviews.ok && reviews.data.content) || [];
  if (!items.length) list.append(el('li', { className: 'hint', textContent: 'Nobody has reviewed this product yet.' }));
  for (const r of items) {
    list.append(el('li', {}, el('span', { className: 'r', textContent: `${r.rating} / 5` }), ' ',
      el('span', { textContent: r.text }), el('br'),
      el('small', { className: 'hint', textContent: new Date(r.createdAt).toLocaleString() })));
  }
  body.append(list);

  if (has('CUSTOMER')) {
    const rating = el('select', {}, ...[5, 4, 3, 2, 1].map((n) => el('option', { value: n, textContent: `${n} / 5` })));
    const text = el('textarea', { rows: 2, maxLength: 2000, required: true, placeholder: 'What did you think?' });
    const form = el('form', { className: 'panel' }, el('h3', { textContent: 'Write a review' }),
      el('label', {}, 'Rating', rating), el('label', {}, 'Review', text),
      el('div', { className: 'row' }, el('button', { className: 'btn', type: 'submit', textContent: 'Post review' })));
    form.onsubmit = async (ev) => {
      ev.preventDefault();
      const res = await api('POST', `/api/v1/products/${p.id}/reviews`, { rating: Number(rating.value), text: text.value });
      if (res.status === 201) {
        toast('Review posted. The average updates in a moment (ReviewSubmitted event).');
        setTimeout(() => { openProduct(p.id); loadProducts(); }, 1500);
      } else if (res.status === 409) {
        toast('You already reviewed this product. One review per customer.', true);
      } else {
        toast(problem(res, 'Could not post the review.'), true);
      }
    };
    body.append(form);
  }
  $('#detail-body').replaceChildren(body);
  if (!$('#detail').open) $('#detail').showModal();
}

// ---------------------------------------------------------------- order + live Saga track
async function placeOrder(productId, quantity) {
  const res = await api('POST', '/api/v1/orders', { items: [{ productId, quantity }] });
  if (res.status === 409) { toast('Not enough stock. The order was refused before any payment.', true); return; }
  if (!res.ok) { toast(problem(res, 'Could not place the order.'), true); return; }
  $('#detail').close();
  toast('Order placed. Watch it move through the services on the right.');
  trackOrder(res.data.orderId);
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function trackOrder(orderId) {
  const saga = $('#saga');
  saga.hidden = false;
  $('#saga-id').textContent = `Order ${orderId}`;
  const steps = [...document.querySelectorAll('#track li')];
  const mark = (states) => steps.forEach((li, i) => { li.className = states[i] || ''; });
  steps[3].querySelector('span').textContent = 'Confirmed';
  mark(['on', 'now']);
  $('#saga-note').textContent = 'Saved as PENDING. The Saga now runs over Kafka; the page polls the order.';
  const started = Date.now();
  for (let i = 0; i < 60; i++) {
    await sleep(i < 10 ? 400 : 1000);
    const res = await api('GET', `/api/v1/orders/${orderId}`);
    const status = res.ok && res.data.status;
    if (status === 'CONFIRMED') {
      mark(['on', 'on', 'on', 'on']);
      $('#saga-note').textContent = `Confirmed in ${((Date.now() - started) / 1000).toFixed(1)} s. Stock reserved, paid once, customer notified.`;
      return;
    }
    if (status === 'CANCELLED') {
      steps[3].querySelector('span').textContent = 'Cancelled';
      mark(['on', 'on', 'fail', 'fail']);
      $('#saga-note').textContent = 'Cancelled: payment was declined or stock ran out. Any reserved stock was released (compensation).';
      return;
    }
    if (i === 6) mark(['on', 'on', 'now']);
  }
  $('#saga-note').textContent = 'Still PENDING. If payment-service is stopped the order waits safely in Kafka and finishes when it is back.';
}

// ---------------------------------------------------------------- my orders
async function loadOrders() {
  const res = await api('GET', '/api/v1/orders?page=0&size=50');
  const tbody = $('#orders-table tbody');
  if (!res.ok) { tbody.replaceChildren(el('tr', {}, el('td', { colSpan: 5, className: 'error', textContent: problem(res) }))); return; }
  if (!res.data.content.length) {
    tbody.replaceChildren(el('tr', {}, el('td', { colSpan: 5, className: 'hint', textContent: 'No orders yet. Open a product in the shop and place one.' })));
    return;
  }
  tbody.replaceChildren(...res.data.content.map((o) => el('tr', {},
    el('td', { textContent: o.orderId.slice(0, 8), title: o.orderId }),
    el('td', { textContent: new Date(o.createdAt).toLocaleString() }),
    el('td', { textContent: o.items.map((it) => `#${it.productId} × ${it.quantity}`).join(', ') }),
    el('td', { textContent: money(o.totalAmount) }),
    el('td', {}, el('span', { className: `status ${o.status}`, textContent: o.status.toLowerCase() })))));
}

// ---------------------------------------------------------------- admin
async function loadAdmin() {
  const res = await api('GET', '/api/v1/products?page=0&size=100', undefined, { auth: false });
  const tbody = $('#stock-table tbody');
  if (!res.ok) { tbody.replaceChildren(el('tr', {}, el('td', { colSpan: 6, className: 'error', textContent: problem(res) }))); return; }
  const rows = await Promise.all(res.data.content.map(async (p) => {
    const s = await api('GET', `/api/v1/inventory/${p.id}`);
    const stock = s.ok ? s.data : { available: '—', reserved: '—' };
    const input = el('input', { type: 'number', min: 0, value: s.ok ? stock.available : 0 });
    input.setAttribute('aria-label', `Available units for ${p.name}`);
    const save = el('button', { className: 'btn small', textContent: 'Save stock' });
    save.onclick = async () => {
      const r = await api('PUT', `/api/v1/inventory/${p.id}`, { available: Number(input.value) });
      toast(r.ok ? `Stock for ${p.name} set to ${input.value}.` : problem(r), !r.ok);
      if (r.ok) loadAdmin();
    };
    const edit = el('button', { className: 'btn ghost small', textContent: 'Edit' });
    edit.onclick = () => editProduct(p);
    const del = el('button', { className: 'btn ghost small', textContent: 'Delete' });
    del.onclick = async () => {
      const r = await api('DELETE', `/api/v1/products/${p.id}`);
      toast(r.status === 204 ? `Deleted ${p.name}.` : problem(r), r.status !== 204);
      loadAdmin();
    };
    return el('tr', {}, el('td', { textContent: `#${p.id} ${p.name}` }), el('td', { textContent: money(p.price) }),
      el('td', { textContent: stock.available }), el('td', { textContent: stock.reserved }),
      el('td', {}, el('div', { className: 'row' }, input, save)), el('td', {}, el('div', { className: 'row' }, edit, del)));
  }));
  tbody.replaceChildren(...rows);
  loadLowStock();
}

async function loadLowStock() {
  const threshold = Number($('#ls-threshold').value || 10);
  const res = await api('GET', `/api/v1/inventory/low-stock?threshold=${threshold}`);
  const list = $('#lowstock');
  if (!res.ok) { list.replaceChildren(el('li', { className: 'error', textContent: problem(res) })); return; }
  list.replaceChildren(...(res.data.length ? res.data.map((s) => el('li', { textContent: `Product #${s.productId}: ${s.available} available, ${s.reserved} reserved` }))
    : [el('li', { className: 'hint', textContent: `Every product has at least ${threshold} units.` })]));
}

function editProduct(p) {
  $('#pf-title').textContent = `Edit product #${p.id}`;
  $('#pf-id').value = p.id; $('#pf-name').value = p.name; $('#pf-price').value = p.price; $('#pf-cat').value = p.categoryId;
  $('#pf-save').textContent = 'Save changes';
  $('#pf-cancel').hidden = false;
  $('#product-form').scrollIntoView({ behavior: 'smooth' });
}

function resetProductForm() {
  $('#product-form').reset();
  $('#pf-id').value = '';
  $('#pf-title').textContent = 'Add a product';
  $('#pf-save').textContent = 'Add product';
  $('#pf-cancel').hidden = true;
}

$('#product-form').onsubmit = async (ev) => {
  ev.preventDefault();
  const id = $('#pf-id').value;
  const body = { name: $('#pf-name').value, price: Number($('#pf-price').value), categoryId: Number($('#pf-cat').value) };
  const res = id ? await api('PUT', `/api/v1/products/${id}`, body) : await api('POST', '/api/v1/products', body);
  if (res.ok) {
    toast(id ? `Saved ${body.name}. The cache was evicted, so the shop shows it at once.` : `Added ${body.name}.`);
    resetProductForm();
    loadAdmin();
  } else {
    toast(problem(res, 'Could not save the product.'), true);
  }
};
$('#pf-cancel').onclick = resetProductForm;
$('#ls-load').onclick = loadLowStock;

// ---------------------------------------------------------------- rate-limit test
$('#burst').onclick = async () => {
  const btn = $('#burst');
  btn.disabled = true;
  const results = await Promise.all(Array.from({ length: 250 }, () =>
    fetch('/api/v1/products/1').then((r) => r.status).catch(() => 0)));
  const ok = results.filter((s) => s === 200).length;
  const limited = results.filter((s) => s === 429).length;
  log('GET', `/api/v1/products/1 × 250 → ${ok} served, ${limited} limited`, limited ? 429 : 200, 0);
  toast(limited ? `${ok} served, ${limited} answered 429: the Redis token bucket ran dry.` : `All ${ok} served: this browser could not send faster than the limit.`);
  btn.disabled = false;
};

// ---------------------------------------------------------------- wiring
document.querySelectorAll('.tab').forEach((tab) => { tab.onclick = () => show(tab.dataset.view); });
$('#prev').onclick = () => { shop.page = Math.max(0, shop.page - 1); loadProducts(); };
$('#next').onclick = () => { shop.page += 1; loadProducts(); };
document.querySelectorAll('[data-user]').forEach((b) => { b.onclick = () => { $('#si-user').value = b.dataset.user; $('#si-pass').focus(); }; });
$('#si-cancel').onclick = () => $('#signin').close();
$('#signin-form').onsubmit = async (ev) => {
  ev.preventDefault();
  const err = $('#si-error');
  err.hidden = true;
  try {
    await signIn($('#si-user').value, $('#si-pass').value);
    $('#si-pass').value = '';
    $('#signin').close();
    toast(`Signed in as ${session.user}.`);
    show(has('ADMIN') ? 'admin' : 'shop');
  } catch (e) {
    err.textContent = e.message;
    err.hidden = false;
  }
};

(async function start() {
  let saved = null;
  try { saved = sessionStorage.getItem('t1-refresh'); } catch (e) { /* ignore */ }
  if (saved) {
    try { keep(await tokenRequest({ grant_type: 'refresh_token', client_id: 'user-sign-in', refresh_token: saved })); } catch (e) { /* stay signed out */ }
  }
  renderWho();
  show('shop');
})();
