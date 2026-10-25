/* Orbit Lockers - shared helpers for the pages (vanilla JS, no build step). */
(function () {
  'use strict';

  const TZ = 'Europe/Lisbon';
  const AVEIRO = [40.6405, -8.6538];

  const STATE_LABELS = {
    CREATED: 'Created', CANCELLED: 'Cancelled', ON_ITS_WAY: 'On its way', READY_FOR_PICKUP: 'Ready for pickup',
    PICKED_UP: 'Picked up', EXPIRED: 'Expired', TO_RETURN: 'To return', RETURNED: 'Returned'
  };
  const STATE_BADGE = {
    CREATED: 'badge-green', CANCELLED: 'badge-red', ON_ITS_WAY: 'badge-violet', READY_FOR_PICKUP: 'badge-green',
    PICKED_UP: 'badge-grey', EXPIRED: 'badge-amber', TO_RETURN: 'badge-amber', RETURNED: 'badge-grey'
  };
  const SIZE_LABELS = { S: 'Small', M: 'Medium', L: 'Large' };
  const SIZE_DIMS = { S: '8 × 38 × 64 cm', M: '19 × 38 × 64 cm', L: '41 × 38 × 64 cm' };

  function esc(v) {
    return String(v == null ? '' : v).replace(/[&<>"']/g, c => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
  }

  function icon(name, cls) {
    return `<svg class="${cls || ''}" width="16" height="16" aria-hidden="true" focusable="false"><use href="/img/icons.svg#${name}"/></svg>`;
  }

  /** fetch wrapper: resolves to {ok, status, data}; network errors resolve to {ok:false, status:0}. */
  async function api(method, url, body) {
    const opts = { method, headers: { 'Accept': 'application/json' } };
    if (body !== undefined) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
    try {
      const res = await fetch(url, opts);
      let data = null;
      const text = await res.text();
      if (text) {
        try { data = JSON.parse(text); } catch (e) { data = { detail: text }; }
      }
      return { ok: res.ok, status: res.status, data };
    } catch (e) {
      return { ok: false, status: 0, data: { code: 'NETWORK_ERROR', detail: 'The service cannot be reached. Please try again.' } };
    }
  }

  function fmtDateTime(iso, withYear) {
    if (!iso) return '—';
    const d = new Date(iso);
    return d.toLocaleString('en-GB', {
      timeZone: TZ, day: 'numeric', month: 'short', year: withYear === false ? undefined : 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }

  function fmtTime(iso) {
    if (!iso) return '—';
    return new Date(iso).toLocaleTimeString('en-GB', { timeZone: TZ, hour: '2-digit', minute: '2-digit' });
  }

  function stateBadge(state, testid) {
    return `<span class="badge badge-dot ${STATE_BADGE[state] || 'badge-grey'}" ${testid ? `data-testid="${testid}"` : ''}>${esc(STATE_LABELS[state] || state)}</span>`;
  }

  function freeTotal(s) { return s.free.s + s.free.m + s.free.l; }
  function freeOf(s, size) { return size ? s.free[size.toLowerCase()] : freeTotal(s); }

  function availabilityBadge(s, size) {
    const n = freeOf(s, size);
    const what = size ? `${SIZE_LABELS[size].toLowerCase()} available` : (n === 1 ? 'locker available' : 'lockers available');
    return n > 0
      ? `<span class="badge badge-dot badge-green">${n} ${what}</span>`
      : `<span class="badge badge-dot badge-red">No ${size ? SIZE_LABELS[size].toLowerCase() : 'lockers'} available</span>`;
  }

  /** Station card used in the lists (button or link). */
  function stationCard(s, index, opts) {
    opts = opts || {};
    const dist = s.distanceKm != null ? `<span class="station-dist">${s.distanceKm.toFixed(1)} km</span>` : '';
    const tag = opts.href ? 'a' : 'button';
    const attrs = opts.href ? `href="${esc(opts.href)}"` : 'type="button"';
    return `<${tag} ${attrs} class="station-card ${opts.selected ? 'selected' : ''}" data-code="${esc(s.code)}"
              data-testid="station-card" ${opts.selected ? 'aria-current="true"' : ''}>
        <div class="row-between">
          <span class="station-name"><span class="num" aria-hidden="true">${index + 1}</span><span data-testid="station-name">${esc(s.name)}</span></span>
          ${dist}
        </div>
        <div class="station-meta">${esc(s.address)} · ${esc(s.city)}</div>
        <div class="row-between">
          <span data-testid="station-availability">${availabilityBadge(s, opts.size)}</span>
          <span class="muted tiny">${opts.selected ? 'Selected ✓' : '24/7 access'}</span>
        </div>
      </${tag}>`;
  }

  /* ---------------- map (Leaflet + OpenStreetMap tiles; optional, pages work without it) ---------------- */
  function createMap(el, center, zoom) {
    if (!el || typeof L === 'undefined') return null;
    const map = L.map(el, { zoomControl: true, attributionControl: true, scrollWheelZoom: false })
      .setView(center || AVEIRO, zoom || 15);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19, attribution: '&copy; OpenStreetMap contributors'
    }).addTo(map);
    map.zoomControl.setPosition('topright');
    map._orbitLayer = L.layerGroup().addTo(map);
    return map;
  }

  function numberedIcon(n) {
    return L.divIcon({ className: '', html: `<div class="pin">${n}</div>`, iconSize: [26, 26], iconAnchor: [13, 13] });
  }

  function showStations(map, stations, origin, radiusKm) {
    if (!map) return;
    const layer = map._orbitLayer;
    layer.clearLayers();
    const bounds = [];
    stations.forEach((s, i) => {
      const m = L.marker([s.latitude, s.longitude], { icon: numberedIcon(i + 1), title: s.name, keyboard: false });
      m.bindTooltip(`<strong>${esc(s.name)}</strong><br>${esc(s.code)} · 24/7`);
      layer.addLayer(m);
      bounds.push([s.latitude, s.longitude]);
    });
    if (origin) {
      layer.addLayer(L.marker(origin, {
        icon: L.divIcon({ className: '', html: '<div class="pin-origin"></div>', iconSize: [14, 14], iconAnchor: [7, 7] }),
        keyboard: false
      }));
      if (radiusKm) {
        layer.addLayer(L.circle(origin, { radius: Math.min(radiusKm, 1.2) * 1000, color: '#9aa0b8', weight: 1, dashArray: '4 4', fillColor: '#8b8fa8', fillOpacity: .08 }));
      }
      bounds.push(origin);
    }
    if (bounds.length > 1) map.fitBounds(bounds, { padding: [50, 50], maxZoom: 16 });
    else if (bounds.length === 1) map.setView(bounds[0], 16);
  }

  /* ---------------- compartment bank (front view) ---------------- */
  function renderBank(container, compartments, opts) {
    opts = opts || {};
    const cols = Math.max(...compartments.map(c => c.column)) + 1;
    const cells = compartments
      .slice()
      .sort((a, b) => a.row - b.row || a.column - b.column)
      .map(c => {
        const cls = c.availability.toLowerCase();
        const selectable = opts.selectable ? opts.selectable(c) : true;
        const label = `${c.code}, ${SIZE_LABELS[c.size]}, ${c.availability.replace(/_/g, ' ').toLowerCase()}`;
        return `<button type="button" class="cell size-${c.size} ${cls} ${opts.selected === c.code ? 'selected' : ''}"
                  style="grid-column:${c.column + 1};grid-row:${c.row + 1}" data-code="${esc(c.code)}"
                  data-testid="cell-${esc(c.code)}" aria-label="${esc(label)}"
                  aria-pressed="${opts.selected === c.code}" ${selectable ? '' : 'disabled'}>
                  ${esc(c.code)}${opts.selected === c.code ? ' ✓' : ''}</button>`;
      }).join('');
    container.innerHTML = `
      <div class="bank" role="group" aria-label="Compartments, front view">
        <div class="bank-side" aria-hidden="true">
          <svg width="20" height="20" style="color:#c8c8d8"><use href="/img/icons.svg#logo"/></svg>
          <div class="bank-screen"><i></i><i></i><b></b></div><div class="bank-slot"></div>
          <div class="bank-id">${esc((opts.stationCode || '').slice(-3))}</div>
        </div>
        <div class="bank-grid" style="grid-template-columns:repeat(${cols},1fr)">${cells}</div>
      </div>
      <div class="bank-legend">
        <span style="--c:#1f7a4d">Available</span><span style="--c:#c42a3e">Reserved</span><span style="--c:#9aa0b0">Out of service</span>
      </div>
      <div class="bank-legend"><span style="--c:transparent">S · small &nbsp; M · medium &nbsp; L · large</span><span style="--c:transparent">Selected locker has a white outline</span></div>`;
    if (opts.onSelect) {
      container.querySelectorAll('.cell').forEach(btn => btn.addEventListener('click', () => {
        opts.onSelect(compartments.find(c => c.code === btn.dataset.code));
      }));
    }
  }

  function problemMessage(res, fallback) {
    if (res && res.data && (res.data.detail || res.data.title)) {
      if (res.data.errors) {
        return res.data.detail + ': ' + Object.entries(res.data.errors).map(([k, v]) => `${k} ${v}`).join('; ');
      }
      return res.data.detail || res.data.title;
    }
    return fallback || 'Something went wrong. Please try again.';
  }

  function qs(name) { return new URLSearchParams(location.search).get(name); }

  function storeGet(key, def) {
    try { const v = localStorage.getItem(key); return v ? JSON.parse(v) : def; } catch (e) { return def; }
  }
  function storeSet(key, value) {
    try { localStorage.setItem(key, JSON.stringify(value)); } catch (e) { /* storage unavailable */ }
  }

  window.Orbit = {
    TZ, AVEIRO, STATE_LABELS, SIZE_LABELS, SIZE_DIMS, esc, icon, api, fmtDateTime, fmtTime, stateBadge,
    availabilityBadge, stationCard, createMap, showStations, renderBank, problemMessage, qs, freeOf,
    storeGet, storeSet
  };
})();
