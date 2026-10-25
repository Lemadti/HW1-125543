/* Station search used by Find stations, Register (steps 1-2) and Operator pages (UC1). */
(function () {
  'use strict';
  const O = window.Orbit;

  /**
   * opts: { root, map, getSize(), cardOptions(station, index) -> {href, selected}, onResults(stations), onSelect(station) }
   * root contains [data-search-form], [data-search-input], [data-radius-select], [data-locate], [data-results],
   * [data-count], [data-alert] and optionally [data-note], [data-show-all].
   * On load every station is listed; a search ranks them by distance within the radius the user chose.
   */
  function mount(opts) {
    const root = opts.root;
    const form = root.querySelector('[data-search-form]');
    const input = root.querySelector('[data-search-input]');
    const radiusSelect = root.querySelector('[data-radius-select]');
    const results = root.querySelector('[data-results]');
    const count = root.querySelector('[data-count]');
    const alertBox = root.querySelector('[data-alert]');
    const note = root.querySelector('[data-note]');
    const showAll = root.querySelector('[data-show-all]');
    let last = null;   // last search: { params, label }, or null while all stations are listed

    const sizeFilter = () => (opts.getSize ? opts.getSize() : null);
    const radius = () => (radiusSelect && radiusSelect.value ? Number(radiusSelect.value) : null);
    const plural = (n, word) => `${n} ${word}${n === 1 ? '' : 's'}`;

    function setAlert(kind, html) {
      if (!html) { alertBox.innerHTML = ''; alertBox.classList.add('hidden'); return; }
      alertBox.className = `alert alert-${kind}`;
      alertBox.innerHTML = `${O.icon(kind === 'error' ? 'alert' : 'info')}<div>${html}</div>`;
      alertBox.setAttribute('role', kind === 'error' ? 'alert' : 'status');
    }

    function render(stations, origin, radiusKm, label) {
      const size = sizeFilter();
      const noun = size ? 'matching station' : 'station';
      count.textContent = label === null
        ? plural(stations.length, noun)
        : `${plural(stations.length, noun)} ${radiusKm ? `within ${radiusKm} km` : 'nearby'}`;
      results.innerHTML = stations.length
        ? stations.map((s, i) => O.stationCard(s, i, Object.assign({ size }, opts.cardOptions ? opts.cardOptions(s, i) : {}))).join('')
        : `<div class="card muted" data-testid="no-results">No stations ${radiusKm ? `within ${radiusKm} km` : ''} with free compartments${size ? ' of this size' : ''}.${radiusKm ? ' Try a larger radius.' : ''}</div>`;
      if (note) {
        note.textContent = label === null
          ? 'Showing all stations. Search an address to sort them by distance.'
          : `Distances measured from ${label}.`;
      }
      if (showAll) showAll.classList.toggle('hidden', label === null);
      O.showStations(opts.map, stations, origin, radiusKm);
      if (opts.onResults) opts.onResults(stations);
      results.querySelectorAll('button.station-card').forEach(b => b.addEventListener('click', () => {
        if (opts.onSelect) opts.onSelect(stations.find(s => s.code === b.dataset.code));
      }));
    }

    /** Lists every station (alphabetically, without distance). Also the fallback when geocoding fails. */
    async function listAll(message) {
      const size = sizeFilter();
      const res = await O.api('GET', '/api/v1/stations' + (size ? `?size=${size}` : ''));
      setAlert(message ? 'warn' : null, message ? O.esc(message) : null);
      if (res.ok) render(res.data, null, null, null);
    }

    async function search(params, label) {
      setAlert(null);
      results.setAttribute('aria-busy', 'true');
      const size = sizeFilter();
      params.delete('size');
      params.delete('radiusKm');
      if (size) params.set('size', size);
      if (radius()) params.set('radiusKm', radius());
      const res = await O.api('GET', '/api/v1/stations/nearby?' + params.toString());
      results.removeAttribute('aria-busy');
      last = { params, label };
      if (res.ok) {
        render(res.data.stations, [res.data.originLatitude, res.data.originLongitude], res.data.radiusKm, label);
      } else if (res.status === 503 || res.status === 0) {
        await listAll(O.problemMessage(res, 'Address search is temporarily unavailable. Showing all stations instead.'));
      } else {
        render([], null, null, label);
        setAlert('error', O.esc(O.problemMessage(res)));
      }
    }

    function reset() {
      last = null;
      input.value = '';
      listAll();
    }

    form.addEventListener('submit', e => {
      e.preventDefault();
      const q = input.value.trim();
      if (!q) { setAlert('error', 'Please enter an address.'); input.focus(); return; }
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

    if (showAll) showAll.addEventListener('click', reset);

    // Radius choices come from the server configuration; the user's choice re-runs the current search.
    const ready = radiusSelect
      ? O.api('GET', '/api/v1/config').then(r => {
          if (r.ok) {
            radiusSelect.innerHTML = '<option value="">Any distance</option>'
              + r.data.radiusOptionsKm.map(km => `<option value="${km}">Within ${km} km</option>`).join('');
          }
          const wanted = O.qs('radius');
          if (wanted && [...radiusSelect.options].some(o => o.value === wanted)) radiusSelect.value = wanted;
          radiusSelect.addEventListener('change', () => { if (last) search(last.params, last.label); });
        })
      : Promise.resolve();

    const initial = opts.initialAddress || O.qs('address');
    if (initial) input.value = initial;
    ready.then(() => { if (input.value.trim()) form.requestSubmit(); else listAll(); });

    return {
      refresh: () => (last ? search(last.params, last.label) : listAll()),
      radius
    };
  }

  window.OrbitSearch = { mount };
})();
