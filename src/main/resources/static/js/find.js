/* UC1 - Find a locker: search, ranked list, map, and the free compartments of the selected station. */
(function () {
  'use strict';
  const O = window.Orbit;
  const map = O.createMap(document.getElementById('map'));
  const detail = document.getElementById('station-detail');
  let selected = null;
  let search;

  async function showDetail(station) {
    selected = station.code;
    document.querySelectorAll('.station-card').forEach(c => {
      const on = c.dataset.code === selected;
      c.classList.toggle('selected', on);
      if (on) c.setAttribute('aria-current', 'true'); else c.removeAttribute('aria-current');
    });
    const res = await O.api('GET', `/api/v1/stations/${encodeURIComponent(station.code)}`);
    if (!res.ok) return;
    const st = res.data.station;
    detail.classList.remove('hidden');
    detail.innerHTML = `
      <div class="row-between">
        <div><span class="badge badge-outline">${O.esc(st.code)}</span> <span class="badge badge-dot badge-green">Open 24/7</span></div>
        <a class="btn btn-primary" href="/register/${encodeURIComponent(st.code)}?size=M" data-testid="register-here">${O.icon('box')}Register a locker here</a>
      </div>
      <h2 class="card-title" style="margin-top:12px;font-size:20px">${O.esc(st.name)}</h2>
      <p class="muted small">${O.esc(st.address)}, ${O.esc(st.postalCode)} ${O.esc(st.city)} · ${O.esc(st.accessNotes || '')}</p>
      <div class="row" style="gap:28px;margin:12px 0">
        <div><div class="stat" data-testid="free-s">${st.free.s}</div><div class="muted tiny">small free</div></div>
        <div><div class="stat" data-testid="free-m">${st.free.m}</div><div class="muted tiny">medium free</div></div>
        <div><div class="stat" data-testid="free-l">${st.free.l}</div><div class="muted tiny">large free</div></div>
        <div><div class="stat stat-dark">${st.free.s + st.free.m + st.free.l} / ${st.totalCompartments}</div><div class="muted tiny">available compartments</div></div>
      </div>
      <div id="bank"></div>`;
    O.renderBank(document.getElementById('bank'), res.data.compartments, { stationCode: st.code, selectable: () => false });
  }

  search = OrbitSearch.mount({
    root: document.getElementById('search-root'),
    map,
    onSelect: showDetail,
    cardOptions: s => ({ selected: s.code === selected })
  });

  // NFR7: availability on the client refreshes at least every 2 minutes.
  setInterval(() => { search.refresh(); if (selected) showDetail({ code: selected }); }, 60000);
})();
