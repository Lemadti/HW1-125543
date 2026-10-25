/* UC5 - Track a parcel: current state, timestamped history and last known location. */
(function () {
  'use strict';
  const O = window.Orbit;
  const code = document.getElementById('main').dataset.code;
  const $ = id => document.getElementById(id);
  const map = O.createMap($('map'), null, 14);

  const TITLES = {
    CREATED: 'Your parcel is registered.', ON_ITS_WAY: 'Your parcel is on its way.',
    READY_FOR_PICKUP: 'Your parcel is ready for pickup.', PICKED_UP: 'Your parcel was picked up.',
    CANCELLED: 'This parcel was cancelled.', EXPIRED: 'The pickup window has ended.',
    TO_RETURN: 'Your parcel will be returned soon.', RETURNED: 'Your parcel was returned to the sender.'
  };
  const STEP_TITLES = {
    CREATED: 'Registration created', ON_ITS_WAY: 'Collected by courier', READY_FOR_PICKUP: 'Ready for pickup',
    PICKED_UP: 'Picked up', CANCELLED: 'Cancelled', EXPIRED: 'Pickup window ended', TO_RETURN: 'Marked to return',
    RETURNED: 'Returned to sender'
  };
  const MAIN_PATH = ['CREATED', 'ON_ITS_WAY', 'READY_FOR_PICKUP', 'PICKED_UP'];

  function eta(p) {
    switch (p.state) {
      case 'CREATED':
      case 'ON_ITS_WAY':
        return `<div class="eyebrow">Delivery to the locker by</div><div class="eta-big">${O.fmtDateTime(p.dropOffDeadline, false)}</div><div class="muted small">Lease of ${p.leaseHours} hours</div>`;
      case 'READY_FOR_PICKUP':
        return `<div class="eyebrow">Pick up before</div><div class="eta-big">${O.fmtDateTime(p.pickupDeadline, false)}</div><div class="muted small">Use the pickup code sent by SMS and email</div>`;
      case 'EXPIRED':
      case 'TO_RETURN':
        return `<div class="eyebrow">Pickup window ended</div><div class="eta-big">${O.fmtDateTime(p.pickupDeadline, false)}</div><div class="muted small">You can still collect it with your code until it is removed</div>`;
      default: {
        const last = p.history[p.history.length - 1];
        return `<div class="eyebrow">${O.esc(O.STATE_LABELS[p.state])}</div><div class="eta-big">${O.fmtDateTime(last.at, false)}</div>`;
      }
    }
  }

  function timeline(p) {
    const items = [];
    p.history.forEach((t, i) => {
      const current = i === p.history.length - 1;
      let sub = t.note || '';
      if (t.to === 'CREATED') sub = `Locker ${p.compartmentCode} reserved at ${p.stationName}`;
      items.push({ at: t.at, title: STEP_TITLES[t.to], sub, done: !current || p.state === 'PICKED_UP', current: current && p.state !== 'PICKED_UP' && p.state !== 'RETURNED' && p.state !== 'CANCELLED' });
    });
    p.locations.slice(1).forEach(l => items.push({ at: l.at, title: 'On its way', sub: l.label, done: p.state !== 'ON_ITS_WAY', current: false }));
    items.sort((a, b) => new Date(a.at) - new Date(b.at));
    if (p.state === 'ON_ITS_WAY' && items.length) {
      items.forEach(i => i.current = false);
      items[items.length - 1].current = true;
    }
    const idx = MAIN_PATH.indexOf(p.state);
    if (idx >= 0 && idx < MAIN_PATH.length - 1) {
      MAIN_PATH.slice(idx + 1).forEach(s => items.push({ title: STEP_TITLES[s], pending: true,
        sub: s === 'READY_FOR_PICKUP' ? `${p.stationName} · ${p.stationCode}` : '' }));
    }
    return items.map(i => `
      <li class="${i.pending ? 'pending-text' : ''}">
        <span class="tl-dot ${i.current ? 'current' : i.pending ? 'pending' : ''}" aria-hidden="true">${i.pending ? '•' : i.current ? '●' : '✓'}</span>
        <div class="tl-title">${O.esc(i.title)}${i.current ? ' <span class="sr-only">(current)</span>' : ''}</div>
        <div class="muted small">${O.esc(i.sub || '')}</div>
        <div class="muted tiny">${i.at ? O.fmtDateTime(i.at, false) : 'Pending'}</div>
      </li>`).join('');
  }

  function drawMap(p) {
    if (!map) return;
    map._orbitLayer.clearLayers();
    const station = [p.stationLatitude, p.stationLongitude];
    const m = L.marker(station, { icon: L.divIcon({ className: '', html: '<div class="pin">1</div>', iconSize: [26, 26], iconAnchor: [13, 13] }) });
    m.bindTooltip(`<strong>${O.esc(p.stationName)} · ${O.esc(p.stationCode)}</strong>`, { permanent: true, direction: 'bottom', offset: [0, 12] });
    map._orbitLayer.addLayer(m);
    const pts = p.locations.filter(l => l.latitude != null).map(l => [l.latitude, l.longitude]);
    if (pts.length && p.state === 'ON_ITS_WAY') {
      const cur = pts[pts.length - 1];
      map._orbitLayer.addLayer(L.polyline(pts, { color: '#6236d9', weight: 4 }));
      map._orbitLayer.addLayer(L.polyline([cur, station], { color: '#a993f0', weight: 3, dashArray: '4 6' }));
      const c = L.marker(cur, { icon: L.divIcon({ className: '', html: '<div class="pin-courier"></div>', iconSize: [30, 30], iconAnchor: [15, 15] }) });
      c.bindTooltip(`<strong>${O.esc(p.lastLocation.label)}</strong><br>Current location · ${O.fmtDateTime(p.lastLocation.at, false)}`, { permanent: true, direction: 'top', offset: [0, -14] });
      map._orbitLayer.addLayer(c);
      map.fitBounds([cur, station], { padding: [70, 70] });
    } else {
      map.setView(station, 16);
    }
  }

  async function load() {
    const res = await O.api('GET', '/api/v1/parcels/' + encodeURIComponent(code));
    if (!res.ok) {
      $('content').classList.add('hidden');
      $('title').textContent = 'Parcel not found';
      $('not-found').classList.remove('hidden');
      $('not-found').innerHTML = `${O.icon('alert')}<div>${O.esc(O.problemMessage(res))} <a href="/tracking">Try another code</a></div>`;
      return;
    }
    const p = res.data;
    $('title').textContent = TITLES[p.state];
    $('state-badge').innerHTML = O.stateBadge(p.state);
    $('eta').innerHTML = eta(p);
    $('timeline').innerHTML = timeline(p);
    $('updated').innerHTML = `${O.icon('refresh')} Last update ${O.fmtDateTime(new Date().toISOString())} (Warsaw time)`;
    $('station-info').innerHTML = `
      <div><div class="strong">${O.esc(p.stationName)} · ${O.esc(p.stationCode)}</div><div class="muted small">${O.esc(p.stationAddress)}</div></div>
      <div><div class="muted tiny">Compartment</div><div class="strong" data-testid="compartment">${O.esc(p.compartmentCode)} · ${O.SIZE_LABELS[p.size]}</div></div>
      <div><div class="muted tiny">${p.pickupDeadline ? 'Pickup until' : 'Drop-off by'}</div><div class="strong">${O.fmtDateTime(p.pickupDeadline || p.dropOffDeadline)}</div></div>`;
    $('notice').innerHTML = `${O.icon('bell')} ${O.esc(p.recipientName)} will receive the pickup code by SMS and email when the parcel is ready.`;
    $('actions').innerHTML = p.allowedEvents.includes('CANCEL')
      ? `<button class="btn btn-outline btn-block" id="cancel" data-testid="cancel-parcel" type="button">Cancel this parcel</button>` : '';
    const cancel = $('cancel');
    if (cancel) cancel.addEventListener('click', async () => {
      if (!confirm('Cancel this parcel? The compartment will be released.')) return;
      const r = await O.api('POST', `/api/v1/parcels/${encodeURIComponent(code)}/cancel`, { actor: 'RECIPIENT', reason: 'Cancelled by recipient on the web' });
      if (!r.ok) alert(O.problemMessage(r));
      load();
    });
    drawMap(p);
    const recent = O.storeGet('orbit.recent', []).filter(x => x.code !== p.trackingCode);
    recent.unshift({ code: p.trackingCode, station: p.stationName });
    O.storeSet('orbit.recent', recent.slice(0, 5));
  }

  $('copy').addEventListener('click', () => { try { navigator.clipboard.writeText(code); } catch (e) { /* ignore */ } });
  load();
  setInterval(load, 60000); // NFR7
})();
