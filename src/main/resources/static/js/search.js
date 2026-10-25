/* Station search used by Find stations, Register (step 1) and Operator pages (UC1). */
(function () {
  'use strict';
  const O = window.Orbit;

  /**
   * opts: { root, map, getSize(), cardOptions(station, index) -> {href, selected}, onResults(stations), autoSearch }
   * root must contain [data-search-form], [data-search-input], [data-locate], [data-results], [data-count], [data-alert]
   */
  function mount(opts) {
    const root = opts.root;
    const form = root.querySelector('[data-search-form]');
    const input = root.querySelector('[data-search-input]');
    const results = root.querySelector('[data-results]');
    const count = root.querySelector('[data-count]');
    const alertBox = root.querySelector('[data-alert]');
    const note = root.querySelector('[data-note]');
    let last = null;

    function setAlert(kind, html) {
      if (!html) { alertBox.innerHTML = ''; alertBox.classList.add('hidden'); return; }
      alertBox.className = `alert alert-${kind}`;
      alertBox.innerHTML = `${O.icon(kind === 'error' ? 'alert' : 'info')}<div>${html}</div>`;
      alertBox.setAttribute('role', kind === 'error' ? 'alert' : 'status');
    }

    function render(stations, origin, radius, label) {
      const size = opts.getSize ? opts.getSize() : null;
      count.textContent = `${stations.length} ${size ? 'matching stations' : (stations.length === 1 ? 'station nearby' : 'stations nearby')}`;
      results.innerHTML = stations.length
        ? stations.map((s, i) => O.stationCard(s, i, Object.assign({ size }, opts.cardOptions ? opts.cardOptions(s, i) : {}))).join('')
        : `<div class="card muted" data-testid="no-results">No stations ${radius ? `within ${radius} km` : ''} with free compartments${size ? ' of this size' : ''}.</div>`;
      if (note) note.textContent = label ? `Distances measured from ${label}.` : '';
      O.showStations(opts.map, stations, origin, radius);
      if (opts.onResults) opts.onResults(stations);
      results.querySelectorAll('button.station-card').forEach(b => b.addEventListener('click', () => {
        if (opts.onSelect) opts.onSelect(stations.find(s => s.code === b.dataset.code));
      }));
    }

    async function fallbackAll(message) {
      const size = opts.getSize ? opts.getSize() : null;
      const res = await O.api('GET', '/api/v1/stations' + (size ? `?size=${size}` : ''));
      setAlert('warn', O.esc(message));
      if (res.ok) render(res.data, null, null, null);
    }

    async function search(params, label) {
      setAlert(null);
      results.setAttribute('aria-busy', 'true');
      const size = opts.getSize ? opts.getSize() : null;
      if (size) params.set('size', size);
      const res = await O.api('GET', '/api/v1/stations/nearby?' + params.toString());
      results.removeAttribute('aria-busy');
      last = { params, label };
      if (res.ok) {
        render(res.data.stations, [res.data.originLatitude, res.data.originLongitude], res.data.radiusKm, label);
      } else if (res.status === 503 || res.status === 0) {
        await fallbackAll(O.problemMessage(res, 'Address search is temporarily unavailable. Showing all stations instead.'));
      } else {
        render([], null, null, null);
        setAlert('error', O.esc(O.problemMessage(res)));
      }
    }

    form.addEventListener('submit', e => {
      e.preventDefault();
      const q = input.value.trim();
      if (!q) { setAlert('error', 'Please enter an address.'); input.focus(); return; }
      O.storeSet('orbit.lastAddress', q);
      search(new URLSearchParams({ address: q }), q);
    });

    const locate = root.querySelector('[data-locate]');
    if (locate) locate.addEventListener('click', () => {
      if (!navigator.geolocation) { setAlert('error', 'Location is not available in this browser.'); return; }
      navigator.geolocation.getCurrentPosition(
        p => search(new URLSearchParams({ lat: p.coords.latitude, lon: p.coords.longitude }), 'your location'),
        () => setAlert('error', 'We could not get your location. Please type an address.'),
        { timeout: 8000 });
    });

    const radiusEl = root.querySelector('[data-radius]');
    if (radiusEl) O.api('GET', '/api/v1/config').then(r => { if (r.ok) radiusEl.textContent = `Within ${r.data.searchRadiusKm} km`; });

    const initial = opts.initialAddress || O.qs('address') || O.storeGet('orbit.lastAddress', '');
    if (initial) input.value = initial;
    if (opts.autoSearch !== false) {
      if (input.value.trim()) form.requestSubmit(); else fallbackAll('Enter an address to sort stations by distance.').then(() => setAlert(null));
    }
    return { refresh: () => last ? search(last.params, last.label) : form.requestSubmit() };
  }

  window.OrbitSearch = { mount };
})();
