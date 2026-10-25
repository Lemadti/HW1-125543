/* Operator parcel detail: registration, contacts, lifecycle diagram, history and operator transitions. */
(function () {
  'use strict';
  const O = window.Orbit;
  const code = document.getElementById('main').dataset.code;
  const $ = id => document.getElementById(id);
  const map = O.createMap($('map'), null, 15);
  $('now').textContent = O.fmtDateTime(new Date().toISOString()) + ' (Lisbon time)';

  // Diagram layout: row 1 is the happy path, row 2 the alternative branches.
  const NODES = [
    ['CREATED', 'Created', 1, 1, true], ['ON_ITS_WAY', 'On its way', 2, 1, true], ['READY_FOR_PICKUP', 'Ready for pickup', 3, 1, true], ['PICKED_UP', 'Picked up', 4, 1, false],
    ['CANCELLED', 'Cancelled', 1, 2, false], ['EXPIRED', 'Expired', 2, 2, true], ['TO_RETURN', 'To return', 3, 2, true], ['RETURNED', 'Returned', 4, 2, false]
  ];
  const SUBS = {
    CREATED: 'Compartment reserved', ON_ITS_WAY: 'Collected by courier', READY_FOR_PICKUP: 'Awaiting recipient',
    PICKED_UP: 'Successful completion', CANCELLED: 'Alternative terminal state', EXPIRED: 'Pickup window ended',
    TO_RETURN: 'Return collection pending', RETURNED: 'Return completed'
  };
  const EVENT_TARGET = {
    CANCEL: 'CANCELLED', DROP_OFF_DEADLINE_PASSED: 'CANCELLED', COLLECT: 'ON_ITS_WAY', DEPOSIT: 'READY_FOR_PICKUP',
    PICK_UP: 'PICKED_UP', PICKUP_DEADLINE_PASSED: 'EXPIRED', MARK_TO_RETURN: 'TO_RETURN', REMOVE: 'RETURNED'
  };
  const EVENT_HINT = {
    CANCEL: 'Cancellation by the partner or recipient', DROP_OFF_DEADLINE_PASSED: 'automatic when the lease ends',
    COLLECT: 'Courier scans the QR code', DEPOSIT: 'Courier drop-off with door confirmed closed',
    PICK_UP: 'Recipient enters the pickup code', PICKUP_DEADLINE_PASSED: 'automatic when the pickup window ends',
    MARK_TO_RETURN: 'Operator, or automatic when the compartment is re-allocated', REMOVE: 'Courier on next drop-off, or operator removal'
  };

  function opAlert(kind, msg) {
    const a = $('op-alert');
    a.className = `alert alert-${kind}`;
    a.innerHTML = `${O.icon(kind === 'error' ? 'alert' : 'check')}<div>${O.esc(msg)}</div>`;
  }

  function lifecycle(p) {
    const passed = new Set(p.history.map(h => h.to));
    const next = new Set(p.allowedEvents.map(e => EVENT_TARGET[e]));
    $('lifecycle').innerHTML = NODES.map(([s, label, col, row, arrow]) => {
      let cls = '';
      let sub = SUBS[s];
      const at = p.history.slice().reverse().find(h => h.to === s);
      if (s === p.state) { cls = 'current'; sub = `Current · ${O.fmtDateTime(at && at.at, false)}`; }
      else if (passed.has(s)) { cls = s === 'CANCELLED' ? 'alt-red' : s === 'EXPIRED' ? 'alt-amber' : 'passed'; sub = O.fmtDateTime(at.at, false); }
      else if (next.has(s)) cls = 'next';
      else if (s === 'CANCELLED') cls = 'alt-red';
      else if (s === 'EXPIRED') cls = 'alt-amber';
      return `<div class="lc-node ${cls} ${arrow && !(row === 2 && col === 1) ? 'lc-arrow' : ''}" style="grid-column:${col};grid-row:${row}"
                data-testid="lc-${s}" ${s === p.state ? 'aria-current="step"' : ''}>
                <div class="lc-name">${label}</div><div class="lc-sub">${O.esc(sub)}</div></div>`;
    }).join('');
    $('lc-current').textContent = 'Current: ' + O.STATE_LABELS[p.state];
    $('next-info').innerHTML = `${O.icon('info')}<span>${p.allowedEvents.length
      ? 'Next allowed: ' + p.allowedEvents.map(e => `<strong>${O.STATE_LABELS[EVENT_TARGET[e]]}</strong> (${O.esc(EVENT_HINT[e])})`).join('; ')
      : 'Final state: no further transitions are allowed.'}</span>`;
    const actions = [];
    if (['EXPIRED', 'TO_RETURN'].includes(p.state)) actions.push(`<button class="btn btn-primary" data-act="removal" data-testid="register-removal">${O.icon('check')}Register removal</button>`);
    if (p.state === 'EXPIRED') actions.push(`<button class="btn btn-outline" data-act="mark-to-return" data-testid="mark-to-return">Mark to return</button>`);
    if (p.state === 'CREATED') actions.push(`<button class="btn btn-outline" data-act="cancel" data-testid="cancel">Cancel reservation</button>`);
    $('op-actions').innerHTML = actions.join('');
    $('op-actions').querySelectorAll('[data-act]').forEach(b => b.addEventListener('click', async () => {
      const act = b.dataset.act;
      const url = act === 'cancel' ? `/api/v1/parcels/${encodeURIComponent(code)}/cancel` : `/api/v1/operator/parcels/${encodeURIComponent(code)}/${act}`;
      const res = await O.api('POST', url, act === 'cancel' ? { actor: 'PARTNER', reason: 'Cancelled via operator console on behalf of partner' } : undefined);
      if (res.ok) opAlert('success', 'Transition recorded.'); else opAlert('error', O.problemMessage(res));
      load();
    }));
  }

  async function stationParcels(p) {
    const res = await O.api('GET', `/api/v1/operator/stations/${encodeURIComponent(p.stationCode)}/parcels`);
    if (!res.ok) return;
    const render = () => {
      const q = $('sp-search').value.trim().toUpperCase();
      const list = res.data.filter(x => !q || x.trackingCode.includes(q));
      $('sp-count').textContent = `${res.data.filter(x => !['PICKED_UP', 'RETURNED', 'CANCELLED'].includes(x.state)).length} active`;
      $('sp-list').innerHTML = list.slice(0, 6).map(x => `
        <a class="station-card ${x.trackingCode === code ? 'selected' : ''}" href="/operator/parcels/${encodeURIComponent(x.trackingCode)}" style="padding:12px">
          <div class="strong small" style="color:var(--accent)">${O.esc(x.trackingCode)}</div>
          <div class="muted tiny">${O.esc(x.compartmentCode)} · ${O.esc(x.senderName)} → ${O.esc(x.recipientName)}</div>
          <div style="margin-top:6px">${O.stateBadge(x.state)}</div></a>`).join('');
    };
    $('sp-search').oninput = render;
    render();
  }

  async function load() {
    const res = await O.api('GET', '/api/v1/operator/parcels/' + encodeURIComponent(code));
    if (!res.ok) {
      $('content').classList.add('hidden');
      $('not-found').classList.remove('hidden');
      $('not-found').innerHTML = `${O.icon('alert')}<div>${O.esc(O.problemMessage(res))}</div>`;
      return;
    }
    const p = res.data;
    $('crumb-station').textContent = p.stationName;
    $('crumb-station').href = '/operator/stations/' + encodeURIComponent(p.stationCode);
    $('state').innerHTML = O.stateBadge(p.state);
    const initials = p.sender.name.split(/\s+/).map(w => w[0]).slice(0, 2).join('').toUpperCase();
    $('summary').innerHTML = `
      <div class="row"><span class="avatar" aria-hidden="true">${O.esc(initials)}</span><div><div class="muted tiny">Registered to</div><div class="strong small">${O.esc(p.sender.name)}</div></div></div>
      <div><div class="muted tiny">Created</div><div class="strong small">${O.fmtDateTime(p.createdAt)}</div></div>
      <div><div class="muted tiny">Compartment</div><div class="strong small">${O.esc(p.stationName)} · ${O.esc(p.compartmentCode)}</div></div>`;
    const contact = (t, c) => `<div><div class="muted tiny">${t}</div><div class="strong">${O.esc(c.name)}</div><div class="muted small">${O.esc(c.email)}<br>${O.esc(c.phone)}</div></div>`;
    $('contacts').innerHTML = contact('SENDER', p.sender) + contact('RECIPIENT', p.recipient);
    $('lease').innerHTML = `
      <div><div class="muted tiny">Parcel size (H × W × D)</div><div class="strong small">${p.size} · ${O.SIZE_DIMS[p.size]}</div></div>
      <div><div class="muted tiny">Lease starts</div><div class="strong small">${O.fmtDateTime(p.leaseStart)}</div></div>
      <div><div class="muted tiny">${p.pickupDeadline ? 'Pickup deadline' : `Drop-off deadline (${p.leaseHours} h lease)`}</div><div class="strong small">${O.fmtDateTime(p.pickupDeadline || p.dropOffDeadline)}</div></div>`;
    $('location').innerHTML = p.lastLocation
      ? `${O.icon('truck')}<div><div class="strong">${O.esc(p.lastLocation.label)}</div><div class="tiny">Last scan: ${O.fmtDateTime(p.lastLocation.at, false)}</div></div>`
      : `${O.icon('info')}<div>No location scans yet.</div>`;
    $('history').innerHTML = p.history.map(h => `<tr><td class="small">${O.fmtDateTime(h.at)}</td><td class="small">${h.from ? O.STATE_LABELS[h.from] : '—'} → <strong>${O.STATE_LABELS[h.to]}</strong></td>
      <td class="small mono">${O.esc(h.event || 'REGISTER')}</td><td class="small">${O.esc(h.actor)}</td><td class="small muted">${O.esc(h.note || '')}</td></tr>`).join('');
    lifecycle(p);
    const st = await O.api('GET', '/api/v1/stations/' + encodeURIComponent(p.stationCode));
    if (st.ok) {
      const s = st.data.station;
      $('station-card').href = '/operator/stations/' + encodeURIComponent(s.code);
      $('station-card').innerHTML = `<div class="row-between"><span class="station-name"><span class="num">1</span>${O.esc(s.name)}</span><span class="muted small">${O.esc(s.code)}</span></div>
        <div class="station-meta">${O.esc(s.address)} · ${O.esc(s.city)}</div><div class="row-between">${O.availabilityBadge(s)}<span class="muted tiny">Selected ✓</span></div>`;
      O.showStations(map, [s]);
    }
    stationParcels(p);
  }
  load();
})();
