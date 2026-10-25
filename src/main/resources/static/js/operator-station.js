/* UC6 - Supervise a station: parcels by state, expired parcels and their removal, out-of-service compartments. */
(function () {
  'use strict';
  const O = window.Orbit;
  const stationCode = document.getElementById('main').dataset.station;
  const $ = id => document.getElementById(id);
  const FILTERS = [['', 'All'], ['CREATED', 'Created'], ['ON_ITS_WAY', 'On its way'], ['READY_FOR_PICKUP', 'Ready'],
    ['EXPIRED', 'Expired'], ['TO_RETURN', 'To return'], ['PICKED_UP', 'Picked up'], ['RETURNED', 'Returned'], ['CANCELLED', 'Cancelled']];
  let filter = '';
  let selected = null;
  let compartments = [];

  $('now').textContent = O.fmtDateTime(new Date().toISOString()) + ' (Lisbon time)';

  function opAlert(kind, msg) {
    const a = $('op-alert');
    if (!msg) { a.className = 'hidden'; return; }
    a.className = `alert alert-${kind}`;
    a.innerHTML = `${O.icon(kind === 'error' ? 'alert' : 'check')}<div>${O.esc(msg)}</div>`;
  }

  function renderFilters() {
    $('filters').innerHTML = FILTERS.map(([v, l]) =>
      `<button type="button" class="badge ${filter === v ? 'badge-violet' : 'badge-outline'}" style="cursor:pointer;border:1px solid var(--border)" aria-pressed="${filter === v}" data-filter="${v}" data-testid="filter-${v || 'ALL'}">${l}</button>`).join('');
    $('filters').querySelectorAll('button').forEach(b => b.addEventListener('click', () => { filter = b.dataset.filter; renderFilters(); loadParcels(); }));
  }

  function cellPanel() {
    const c = compartments.find(x => x.code === selected);
    if (!c) { $('cell-panel').innerHTML = '<p class="muted small">No compartment selected.</p>'; return; }
    const oos = c.availability === 'OUT_OF_SERVICE';
    $('cell-panel').innerHTML = `
      <div class="strong" style="font-size:16px">Compartment ${O.esc(c.code)}</div>
      <div class="muted small">${O.SIZE_LABELS[c.size]} · ${c.heightCm} × ${c.widthCm} × ${c.depthCm} cm</div>
      <div style="margin:10px 0">${oos ? '<span class="badge badge-grey">Out of service</span>' : c.availability === 'RESERVED'
        ? `<span class="badge badge-red">Reserved until ${O.fmtDateTime(c.reservedUntil, false)}</span>` : '<span class="badge badge-green">Available</span>'}</div>
      ${oos ? `<p class="small muted">${O.esc(c.statusReason || '')}</p>
        <button class="btn btn-green btn-block" data-testid="in-service" id="svc">${O.icon('check')}Put back in service</button>`
        : `<label for="reason">Reason</label><input class="input" id="reason" placeholder="e.g. Door sensor faulty" data-testid="oos-reason">
        <button class="btn btn-danger btn-block" style="margin-top:10px" data-testid="out-of-service" id="svc">${O.icon('alert')}Put out of service</button>`}`;
    $('svc').addEventListener('click', async () => {
      const body = oos ? { status: 'IN_SERVICE' } : { status: 'OUT_OF_SERVICE', reason: ($('reason').value || '').trim() || 'Faulty compartment' };
      const res = await O.api('PUT', `/api/v1/operator/stations/${encodeURIComponent(stationCode)}/compartments/${encodeURIComponent(c.code)}/status`, body);
      opAlert(res.ok ? 'success' : 'error', res.ok ? `Compartment ${c.code} is now ${oos ? 'in service' : 'out of service'}.` : O.problemMessage(res));
      refresh();
    });
  }

  async function loadStation() {
    const res = await O.api('GET', `/api/v1/stations/${encodeURIComponent(stationCode)}`);
    if (!res.ok) return;
    const st = res.data.station;
    compartments = res.data.compartments;
    $('crumb').textContent = st.name;
    const oos = compartments.filter(c => c.availability === 'OUT_OF_SERVICE').length;
    const reserved = compartments.filter(c => c.availability === 'RESERVED').length;
    $('station-card').innerHTML = `
      <div class="row-between"><span class="station-name"><span class="num" style="width:24px;height:24px;display:inline-grid;place-items:center;border-radius:6px;background:var(--red-soft);color:var(--pin);margin-right:10px">●</span>${O.esc(st.name)}</span><span class="badge badge-outline">${O.esc(st.code)}</span></div>
      <div class="station-meta">${O.esc(st.address)}, ${O.esc(st.postalCode)} ${O.esc(st.city)}</div>
      <div class="form-grid-2" style="grid-template-columns:repeat(3,1fr);gap:8px">
        <div><div class="stat" data-testid="count-free">${st.free.s + st.free.m + st.free.l}</div><div class="muted tiny">available</div></div>
        <div><div class="stat" style="color:var(--red)">${reserved}</div><div class="muted tiny">reserved</div></div>
        <div><div class="stat stat-dark" data-testid="count-oos">${oos}</div><div class="muted tiny">out of service</div></div>
      </div>
      <p class="muted tiny" style="margin:10px 0 0">Free: S ${st.free.s} · M ${st.free.m} · L ${st.free.l} of ${st.totalCompartments} compartments</p>
      <a class="small" href="/terminal/${encodeURIComponent(st.code)}">Open this station's terminal ↗</a>`;
    O.renderBank($('bank'), compartments, { stationCode, selected, onSelect: c => { selected = c.code; loadStation(); } });
    cellPanel();
  }

  function deadline(p) {
    if (p.state === 'CREATED' || p.state === 'ON_ITS_WAY') return `Drop-off ${O.fmtDateTime(p.dropOffDeadline, false)}`;
    if (p.pickupDeadline && ['READY_FOR_PICKUP', 'EXPIRED', 'TO_RETURN'].includes(p.state)) return `Pickup ${O.fmtDateTime(p.pickupDeadline, false)}`;
    return '—';
  }

  async function loadParcels() {
    const res = await O.api('GET', `/api/v1/operator/stations/${encodeURIComponent(stationCode)}/parcels${filter ? `?state=${filter}` : ''}`);
    if (!res.ok) return;
    $('p-count').textContent = `${res.data.length} parcels`;
    $('parcels').innerHTML = res.data.length ? res.data.map(p => `
      <tr data-testid="parcel-row" data-code="${O.esc(p.trackingCode)}">
        <td><a class="strong" href="/operator/parcels/${encodeURIComponent(p.trackingCode)}">${O.esc(p.trackingCode)}</a></td>
        <td>${O.esc(p.compartmentCode)}</td>
        <td class="small">${O.esc(p.senderName)} → ${O.esc(p.recipientName)}</td>
        <td>${O.stateBadge(p.state)}</td>
        <td class="small">${deadline(p)}</td>
        <td>${['EXPIRED', 'TO_RETURN'].includes(p.state)
          ? `<button class="btn btn-outline" style="height:32px" data-remove="${O.esc(p.trackingCode)}" data-testid="remove-${O.esc(p.trackingCode)}">Register removal</button>` : ''}</td>
      </tr>`).join('') : '<tr><td colspan="6" class="muted">No parcels in this state.</td></tr>';
    $('parcels').querySelectorAll('[data-remove]').forEach(b => b.addEventListener('click', async () => {
      const res2 = await O.api('POST', `/api/v1/operator/parcels/${encodeURIComponent(b.dataset.remove)}/removal`);
      opAlert(res2.ok ? 'success' : 'error', res2.ok ? `Removal of ${b.dataset.remove} registered (RETURNED).` : O.problemMessage(res2));
      refresh();
    }));
  }

  async function loadIncidents() {
    const res = await O.api('GET', `/api/v1/operator/stations/${encodeURIComponent(stationCode)}/incidents`);
    if (!res.ok) return;
    $('inc-count').textContent = res.data.length;
    $('incidents').innerHTML = res.data.length ? res.data.slice(0, 8).map(i => `
      <div class="panel-grey small"><div class="row-between"><span class="strong">${O.esc(i.compartmentCode)} · ${O.esc(i.type.replace(/_/g, ' ').toLowerCase())}</span><span class="muted tiny">${O.fmtDateTime(i.reportedAt, false)}</span></div>
      <div class="muted tiny">${O.esc(i.description || '')}${i.trackingCode ? ' · ' + O.esc(i.trackingCode) : ''} · by ${O.esc(i.reportedBy.toLowerCase())}</div></div>`).join('')
      : '<p class="muted small" style="margin:0">No malfunctions reported.</p>';
  }

  function refresh() { loadStation(); loadParcels(); loadIncidents(); }
  renderFilters();
  refresh();
  setInterval(refresh, 60000); // NFR7
})();
