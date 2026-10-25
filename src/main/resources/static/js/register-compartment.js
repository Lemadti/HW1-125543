/* Register step 2: live front view of the station; pick a free compartment of the parcel size. */
(function () {
  'use strict';
  const O = window.Orbit;
  const main = document.getElementById('main');
  const stationCode = main.dataset.station;
  const size = (main.dataset.size || 'M').toUpperCase();
  const sizeWord = O.SIZE_LABELS[size].toLowerCase();
  let selected = null;
  let compartments = [];

  document.getElementById('size-word').textContent = sizeWord;
  document.getElementById('size-chip').textContent = `${O.SIZE_LABELS[size]} · ${O.SIZE_DIMS[size]}`;
  document.getElementById('st-size-label').textContent = `${sizeWord} available`;

  const map = O.createMap(document.getElementById('map'), null, 15);
  let rank = 0;

  OrbitSearch.mount({
    root: document.getElementById('search-root'),
    map,
    getSize: () => size,
    onResults: list => {
      const i = list.findIndex(s => s.code === stationCode);
      rank = i >= 0 ? i + 1 : 0;
      const me = list[i];
      document.getElementById('st-index').textContent = rank ? `STATION ${rank}` : 'STATION';
      document.getElementById('st-dist').textContent = me && me.distanceKm != null
        ? `${me.distanceKm.toFixed(1)} km away · ${me.walkingMinutes} min walk` : '';
    },
    cardOptions: s => ({
      selected: s.code === stationCode,
      href: `/register/${encodeURIComponent(s.code)}?size=${size}&address=${encodeURIComponent(document.getElementById('address').value)}`
    })
  });

  function renderSelection() {
    const box = document.getElementById('selection');
    const c = compartments.find(x => x.code === selected);
    if (!c) {
      box.innerHTML = `<div class="callout callout-violet">${O.icon('info')}<span>Select a ${sizeWord} compartment marked as available.</span></div>`;
      return;
    }
    const dims = `${O.SIZE_LABELS[c.size]} · ${c.heightCm} × ${c.widthCm} × ${c.depthCm} cm`;
    if (c.availability === 'AVAILABLE' && c.size === size) {
      box.innerHTML = `<div class="callout callout-green">${O.icon('box')}
          <div style="flex:1"><div class="callout-title">${O.esc(c.code)} · Available to register</div><div class="small muted">${dims} · Maximum parcel weight 25 kg</div></div>
          <a class="btn btn-green" data-testid="continue" href="/register/${encodeURIComponent(stationCode)}/${encodeURIComponent(c.code)}?size=${size}">${O.icon('arrow-right')}Register</a></div>`;
    } else if (c.availability === 'AVAILABLE') {
      box.innerHTML = `<div class="callout callout-amber">${O.icon('info')}<div><div class="callout-title">${O.esc(c.code)} · ${O.SIZE_LABELS[c.size]}</div><div class="small">Your parcel is ${sizeWord}; choose a ${sizeWord} compartment.</div></div></div>`;
    } else if (c.availability === 'RESERVED') {
      box.innerHTML = `<div class="callout callout-red">${O.icon('clock')}<div style="flex:1"><div class="callout-title">${O.esc(c.code)} · Reserved</div>
          <div class="small muted">Reserved until ${O.fmtDateTime(c.reservedUntil)}</div></div><span class="small strong" style="color:var(--red)">Not available</span></div>`;
    } else {
      box.innerHTML = `<div class="callout callout-amber">${O.icon('alert')}<div style="flex:1"><div class="callout-title">${O.esc(c.code)} · Out of service</div>
          <div class="small">${O.esc(c.statusReason || 'Under maintenance')}</div></div><span class="small strong">Not available</span></div>`;
    }
  }

  async function load() {
    const res = await O.api('GET', `/api/v1/stations/${encodeURIComponent(stationCode)}`);
    if (!res.ok) return;
    const st = res.data.station;
    compartments = res.data.compartments;
    document.getElementById('st-name').textContent = st.name;
    document.getElementById('st-address').innerHTML = `${O.esc(st.address)}<br>${O.esc(st.postalCode)} ${O.esc(st.city)} · ${O.esc(st.code)}`;
    document.getElementById('st-notes').textContent = st.accessNotes || '';
    document.getElementById('st-free').textContent = `${st.free.s + st.free.m + st.free.l} / ${st.totalCompartments}`;
    document.getElementById('st-size-free').textContent = O.freeOf(st, size);
    if (!selected) {
      const first = compartments.find(c => c.size === size && c.availability === 'AVAILABLE');
      selected = first ? first.code : null;
    }
    O.renderBank(document.getElementById('bank'), compartments, {
      stationCode, selected,
      onSelect: c => { selected = c.code; load(); }
    });
    renderSelection();
    if (map && !map._stationShown) {
      map._stationShown = true;
      map.setView([st.latitude, st.longitude], 16);
      L.marker([st.latitude, st.longitude], { icon: L.divIcon({ className: '', html: '<div class="pin">•</div>', iconSize: [26, 26] }) })
        .addTo(map).bindTooltip(`${st.name} · ${st.code} · 24/7`, { permanent: true, direction: 'right' });
    }
  }

  load();
  setInterval(load, 60000); // NFR7: live availability
})();
