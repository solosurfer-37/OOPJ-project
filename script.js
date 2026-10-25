// BorrowBuddy frontend demo. All data lives in localStorage; no backend is involved.
const KEY = 'borrowbuddy-demo-v1';
const MAX_ACTIVE = 3;                                  // same limit as the Java project
const PENALTY = { late: 5, damaged: 15, lost: 30 };    // same formula as the Java project
const $ = (s) => document.querySelector(s);
document.documentElement.classList.add('js');

// ---------- date helpers ----------
const midnight = (d) => { d.setHours(0, 0, 0, 0); return d; };
const addDays = (n) => { const d = midnight(new Date()); d.setDate(d.getDate() + n); return d; };
const toIso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const daysLeft = (iso) => Math.round((new Date(iso + 'T00:00:00') - midnight(new Date())) / 864e5);
const nice = (iso) => new Date(iso + 'T00:00:00').toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

// ---------- state ----------
function seed() {
  const item = (id, name, cat, owner, note, max) =>
    ({ id, name, cat, owner, note, max, status: 'available', borrower: '', requester: '', days: 0, due: '' });
  const items = [
    item(1, 'Data Structures', 'Books', 'Meera', 'Weiss, 4th edition. Light pencil notes in the margins.', 14),
    item(2, 'Engineering Physics', 'Books', 'Kabir', 'Semester 2 textbook with solved problems at the back.', 14),
    item(3, 'Casio fx-991EX', 'Electronics', 'Ishaan', 'Scientific calculator. Allowed in most exams.', 7),
    item(4, '65W USB-C Charger', 'Electronics', 'Neha', 'Fast charger that works for laptops and phones.', 7),
    item(5, 'Arduino Uno Kit', 'Electronics', 'Ishaan', 'Board, breadboard, jumper wires and sensors.', 7),
    item(6, 'Cricket Bat', 'Sports', 'Rohan', 'Kashmir willow, size 6. Grip recently replaced.', 7),
    item(7, 'Badminton Racquets', 'Sports', 'Neha', 'Pair of racquets with three shuttlecocks.', 7),
    item(8, 'Digital Multimeter', 'Electronics', 'Kabir', 'Basic lab multimeter with probes.', 7),
  ];
  Object.assign(items[2], { status: 'borrowed', borrower: 'Rohan', due: toIso(addDays(-2)) });
  Object.assign(items[3], { status: 'borrowed', borrower: 'Aarav', due: toIso(addDays(3)) });
  Object.assign(items[5], { status: 'borrowed', borrower: 'Meera', due: toIso(addDays(5)) });
  const log = (name, owner, borrower, result, ago) => ({ name, owner, borrower, result, date: toIso(addDays(-ago)) });
  return {
    user: 'Aarav',
    items,
    log: [
      log('Data Structures', 'Meera', 'Aarav', 'ontime', 40),
      log('Badminton Racquets', 'Neha', 'Aarav', 'ontime', 28),
      log('Digital Multimeter', 'Kabir', 'Aarav', 'late', 17),
      log('Engineering Physics', 'Kabir', 'Aarav', 'ontime', 9),
    ],
  };
}

function load() {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY));
    if (saved && Array.isArray(saved.items) && Array.isArray(saved.log)) return saved;
  } catch (e) { /* corrupted or blocked storage: fall back to sample data */ }
  return seed();
}
function save() {
  try { localStorage.setItem(KEY, JSON.stringify(state)); }
  catch (e) { toast('Storage is blocked, so changes will not persist.'); }
}

let state = load();
let category = 'All';
let query = '';
let selectedId = state.items[0].id;
const find = (id) => state.items.find((i) => i.id === id);
const activeLoans = (name) => state.items.filter((i) => i.status === 'borrowed' && i.borrower === name).length;

// ---------- small UI helpers ----------
let toastTimer;
function toast(msg) {
  const t = $('#toast');
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove('show'), 3200);
}
const art = (cat) => `<div class="art art-${cat}" aria-hidden="true"><i></i><i></i><i></i></div>`;
const statusLabel = { available: 'Available', requested: 'Request pending', borrowed: 'Borrowed', lost: 'Reported lost', damaged: 'Reported damaged' };
const pill = (s) => `<span class="pill s-${s}">${statusLabel[s]}</span>`;

// ---------- loan actions ----------
function sendRequest(id, days) {
  const it = find(id);
  if (!it || it.status !== 'available') return toast('That item is not available right now.');
  if (it.owner === state.user) return toast('You cannot borrow your own item.');
  if (activeLoans(state.user) >= MAX_ACTIVE) return toast(`You already have ${MAX_ACTIVE} active loans. Return one first.`);
  Object.assign(it, { status: 'requested', requester: state.user, days });
  save(); selectedId = id; renderAll();
  toast(`Request sent to ${it.owner}.`);
}
function approve(id) {
  const it = find(id);
  if (!it || it.status !== 'requested') return;
  Object.assign(it, { status: 'borrowed', borrower: it.requester, due: toIso(addDays(it.days)), requester: '' });
  save(); renderAll(); toast(`Approved. Due on ${nice(it.due)}.`);
}
function reject(id) {
  const it = find(id);
  if (!it || it.status !== 'requested') return;
  Object.assign(it, { status: 'available', requester: '', days: 0 });
  save(); renderAll(); toast('Request rejected.');
}
// Closing a loan: always writes one entry to the history log, which the score is built from.
function closeLoan(id, result) {
  const it = find(id);
  if (!it || it.status !== 'borrowed') return;
  if (result === 'ontime' && daysLeft(it.due) < 0) result = 'late';   // overdue is decided by the date, not by the user
  state.log.push({ name: it.name, owner: it.owner, borrower: it.borrower, result, date: toIso(midnight(new Date())) });
  const wasOwnerReport = result === 'lost' || result === 'damaged';
  Object.assign(it, { status: wasOwnerReport ? result : 'available', borrower: '', due: '', days: 0 });
  save(); renderAll();
  toast(result === 'late' ? 'Returned late. This is recorded in the score.' : wasOwnerReport ? `Reported ${result}.` : 'Returned on time.');
}

// ---------- rendering ----------
function renderGrid() {
  const q = query.trim().toLowerCase();
  const list = state.items.filter((i) =>
    (category === 'All' || i.cat === category) && (!q || (i.name + ' ' + i.owner + ' ' + i.cat).toLowerCase().includes(q)));
  $('#count').textContent = `${list.length} item${list.length === 1 ? '' : 's'} shown`;
  $('#grid').innerHTML = list.length ? list.map((i) => `
    <button class="card" data-id="${i.id}" aria-label="Open details for ${esc(i.name)}">
      ${art(i.cat)}
      <div class="card-in"><h3>${esc(i.name)}</h3><p>${esc(i.cat)}, owned by ${esc(i.owner)}</p>${pill(i.status)}</div>
    </button>`).join('') : '<p class="empty">No items match that search. Try another word or switch the category.</p>';
}

function renderFlow() {
  const sel = $('#flowSelect');
  sel.innerHTML = state.items.map((i) => `<option value="${i.id}">${esc(i.name)}</option>`).join('');
  sel.value = selectedId;
  const it = find(selectedId);
  const who = it.status === 'requested' ? it.requester : it.borrower;
  const dueText = it.status === 'borrowed' ? `${nice(it.due)} (${daysLeft(it.due) < 0 ? Math.abs(daysLeft(it.due)) + ' days overdue' : daysLeft(it.due) + ' days left'})`
    : it.status === 'requested' ? `${it.days} days after approval` : 'Not set';
  $('#facts').innerHTML = [
    ['Availability', pill(it.status)], ['Owner', esc(it.owner)], ['Borrower', who ? esc(who) : 'None'], ['Due date', dueText],
  ].map(([k, v]) => `<div><dt>${k}</dt><dd>${v}</dd></div>`).join('');

  // progress track: Available > Requested > Borrowed > Returned
  const stage = { available: 0, requested: 1, borrowed: 2, lost: 3, damaged: 3 }[it.status];
  const names = ['Available', 'Request sent', 'Approved and borrowed', it.status === 'lost' || it.status === 'damaged' ? 'Closed as ' + it.status : 'Returned'];
  $('#track').innerHTML = names.map((n, i) => `<li class="${i < stage ? 'done' : i === stage ? 'now' : ''}">${n}</li>`).join('');

  const btn = (label, fn, cls = 'primary') => `<button class="btn ${cls} small" data-do="${fn}">${label}</button>`;
  let html = '', note = '';
  if (it.status === 'requested') html = btn('Approve as owner', 'approve') + btn('Reject', 'reject', 'ghost-l');
  else if (it.status === 'borrowed') html = btn('Mark returned', 'ontime') + btn('Report damaged', 'damaged', 'ghost-l') + btn('Report lost', 'lost', 'ghost-l');
  else if (it.status === 'available') { note = 'Open this item from the gallery to send a request.'; html = btn('Go to gallery', 'gallery'); }
  else note = 'This item is closed and cannot be borrowed again in the demo.';
  $('#actions').innerHTML = html;
  const old = $('#flowNote'); if (old) old.remove();
  if (note) $('#actions').insertAdjacentHTML('afterend', `<p class="note" id="flowNote">${note}</p>`);
}

function scoreFor(name) {
  const mine = state.log.filter((e) => e.borrower === name && e.result !== 'ontime');
  const total = mine.reduce((sum, e) => sum + PENALTY[e.result], 0);
  return { score: Math.max(0, Math.min(100, 100 - total)), events: mine };
}
function renderTrust() {
  const { score, events } = scoreFor(state.user);
  $('#scoreWho').textContent = `${state.user}'s reliability score`;
  $('#scoreNum').textContent = score;
  $('#scoreFill').style.width = score + '%';
  $('#scoreBar').setAttribute('aria-label', `Score ${score} out of 100`);
  const wording = { late: 'returned late', damaged: 'reported damaged', lost: 'reported lost' };
  $('#why').innerHTML = events.length
    ? events.map((e) => `<li><span>${esc(e.name)} ${wording[e.result]}, ${nice(e.date)}</span><b>-${PENALTY[e.result]}</b></li>`).join('')
    : '<li><span>No penalties yet. Every return has been on time.</span><b>0</b></li>';
}

function renderActivity() {
  const row = (i, extra) => `<li><span>${esc(i.name)}<small>${esc(i.borrower)} from ${esc(i.owner)}, ${extra}</small></span>
    <button class="btn ghost-l small" data-return="${i.id}">Mark returned</button></li>`;
  const upcoming = state.items.filter((i) => i.status === 'borrowed' && daysLeft(i.due) >= 0).sort((a, b) => a.due.localeCompare(b.due));
  const overdue = state.items.filter((i) => i.status === 'borrowed' && daysLeft(i.due) < 0);
  $('#upcoming').innerHTML = upcoming.map((i) => row(i, `due ${nice(i.due)}`)).join('') || '<li class="none">Nothing due soon.</li>';
  $('#overdue').innerHTML = overdue.map((i) => row(i, `<span class="late">${Math.abs(daysLeft(i.due))} days overdue</span>`)).join('') || '<li class="none">No overdue items. Nice.</li>';
  const labels = { ontime: 'Returned on time', late: 'Returned late', lost: 'Reported lost', damaged: 'Reported damaged' };
  $('#history').innerHTML = state.log.slice(-5).reverse().map((e) =>
    `<li><span>${esc(e.name)}<small>${esc(e.borrower)} from ${esc(e.owner)}, ${nice(e.date)}</small></span><span>${labels[e.result]}</span></li>`).join('') || '<li class="none">No history yet.</li>';
}

function renderAll() {
  renderGrid(); renderFlow(); renderTrust(); renderActivity();
  $('#signin').textContent = state.user === 'Aarav' ? 'Sign in' : `Hi, ${state.user}`;
}

// ---------- events ----------
$('#filters').addEventListener('click', (e) => {
  const b = e.target.closest('button'); if (!b) return;
  category = b.dataset.cat;
  document.querySelectorAll('#filters button').forEach((x) => x.setAttribute('aria-pressed', x === b));
  renderGrid();
});
$('#search').addEventListener('input', (e) => { query = e.target.value; renderGrid(); });

// item details dialog
function openItem(id) {
  const it = find(id);
  const own = it.owner === state.user;
  const can = it.status === 'available' && !own;
  const opts = Array.from({ length: it.max }, (_, n) => `<option value="${n + 1}">${n + 1} day${n ? 's' : ''}</option>`).join('');
  $('#dlgBody').innerHTML = `${art(it.cat)}
    <h3 id="dlgTitle">${esc(it.name)}</h3>${pill(it.status)}
    <p>${esc(it.note)}<br>Owner: ${esc(it.owner)}. Maximum loan: ${it.max} days.</p>
    ${can ? `<div class="row"><label for="dlgDays">Borrow for</label><select id="dlgDays">${opts}</select></div>
      <div class="actions"><button class="btn primary" data-request="${it.id}">Send borrow request</button></div>`
      : `<p>${own ? 'This is your own item, so you cannot borrow it.' : 'You can request this item once it is available again.'}</p>
      <div class="actions"><button class="btn ghost-l small" data-track="${it.id}">See loan status</button></div>`}`;
  $('#itemDlg').showModal();
}
$('#grid').addEventListener('click', (e) => { const c = e.target.closest('.card'); if (c) openItem(+c.dataset.id); });
$('#dlgBody').addEventListener('click', (e) => {
  const r = e.target.closest('[data-request]'), t = e.target.closest('[data-track]');
  if (r) { sendRequest(+r.dataset.request, +$('#dlgDays').value); $('#itemDlg').close(); $('#flow').scrollIntoView(); }
  if (t) { selectedId = +t.dataset.track; renderFlow(); $('#itemDlg').close(); $('#flow').scrollIntoView(); }
});

// workflow panel
$('#flowSelect').addEventListener('change', (e) => { selectedId = +e.target.value; renderFlow(); });
$('#actions').addEventListener('click', (e) => {
  const b = e.target.closest('[data-do]'); if (!b) return;
  const d = b.dataset.do;
  if (d === 'approve') approve(selectedId);
  else if (d === 'reject') reject(selectedId);
  else if (d === 'gallery') $('#explore').scrollIntoView();
  else closeLoan(selectedId, d);
});
document.addEventListener('click', (e) => {
  const r = e.target.closest('[data-return]');
  if (r) closeLoan(+r.dataset.return, 'ontime');
});

// sign in (demo only: just sets a display name)
$('#signin').addEventListener('click', () => { $('#nameInput').value = state.user; $('#signDlg').showModal(); });
$('#signCancel').addEventListener('click', () => $('#signDlg').close());
$('#signForm').addEventListener('submit', (e) => {
  e.preventDefault();
  const name = $('#nameInput').value.trim();
  if (!name) return;
  state.user = name; save(); $('#signDlg').close(); renderAll(); toast(`Signed in as ${name} (demo).`);
  closeMenu();
});
$('#reset').addEventListener('click', () => {
  if (!confirm('Reset all demo data to the original sample data?')) return;
  state = seed(); selectedId = state.items[0].id; save(); renderAll(); toast('Demo data reset.');
});

// mobile menu
const burger = $('#burger'), menu = $('#menu');
function closeMenu() { menu.classList.remove('open'); burger.setAttribute('aria-expanded', 'false'); burger.setAttribute('aria-label', 'Open menu'); }
burger.addEventListener('click', () => {
  const open = menu.classList.toggle('open');
  burger.setAttribute('aria-expanded', open); burger.setAttribute('aria-label', open ? 'Close menu' : 'Open menu');
});
menu.addEventListener('click', (e) => { if (e.target.closest('a')) closeMenu(); });
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeMenu(); });

// fade-up reveal for headings, once each
const reveals = document.querySelectorAll('.reveal');
if ('IntersectionObserver' in window) {
  const io = new IntersectionObserver((entries) => entries.forEach((en) => {
    if (en.isIntersecting) { en.target.classList.add('in'); io.unobserve(en.target); }
  }), { threshold: 0.2 });
  reveals.forEach((r) => io.observe(r));
} else reveals.forEach((r) => r.classList.add('in'));

renderAll();
