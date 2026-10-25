/* Register step 3: lease + contacts -> POST /api/v1/parcels (UC2), then the label (tracking code, QR, drop-off PIN). */
(function () {
  'use strict';
  const O = window.Orbit;
  const main = document.getElementById('main');
  const stationCode = main.dataset.station;
  const compartmentCode = main.dataset.compartment;
  const size = (main.dataset.size || 'M').toUpperCase();
  let cfg = { leaseHoursOptions: [24, 48, 72], defaultLeaseHours: 48, pricePerDay: 3, currency: 'EUR' };
  let hours = 48;
  let station = null;

  const $ = id => document.getElementById(id);
  const fields = ['s-name', 's-email', 's-phone', 'r-name', 'r-email', 'r-phone'];
  const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  const PHONE = /^\+?[0-9 ()-]{7,20}$/;

  // Default lease start: now, rounded up to the next 10 minutes, as a Lisbon local time.
  function lisbonLocal(date) {
    const parts = new Intl.DateTimeFormat('en-CA', { timeZone: O.TZ, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
      .formatToParts(date).reduce((a, p) => (a[p.type] = p.value, a), {});
    return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
  }
  // Convert a Lisbon wall-clock "YYYY-MM-DDTHH:mm" to an Instant (handles DST by probing the offset).
  function lisbonToInstant(local) {
    if (!local) return null;
    const guess = new Date(local + ':00Z');
    const asLisbon = new Date(lisbonLocal(guess) + ':00Z');
    return new Date(guess.getTime() - (asLisbon.getTime() - guess.getTime()));
  }
  const start = new Date(Math.ceil(Date.now() / 600000) * 600000);
  $('lease-start').value = lisbonLocal(start);

  function leaseEnd() {
    const s = lisbonToInstant($('lease-start').value);
    return s ? new Date(s.getTime() + hours * 3600000) : null;
  }

  function renderDurations() {
    $('durations').innerHTML = cfg.leaseHoursOptions.map(h =>
      `<button type="button" role="radio" aria-checked="${h === hours}" data-hours="${h}" data-testid="duration-${h}">${h} hours</button>`).join('');
    $('durations').querySelectorAll('button').forEach(b => b.addEventListener('click', () => {
      hours = Number(b.dataset.hours); renderDurations(); update();
    }));
  }

  const touched = new Set();

  /** showErrors: 'touched' shows errors only for fields the user already left; 'all' (on submit) for every field. */
  function validate(showErrors) {
    const v = id => $(id).value.trim();
    const errors = {};
    ['s', 'r'].forEach(p => {
      if (!v(`${p}-name`)) errors[`${p}-name`] = 'Please enter the full name.';
      if (!EMAIL.test(v(`${p}-email`))) errors[`${p}-email`] = 'Please enter a valid email address.';
      if (!PHONE.test(v(`${p}-phone`))) errors[`${p}-phone`] = 'Please enter a valid phone number.';
    });
    if (showErrors) {
      fields.forEach(f => {
        const show = errors[f] && (showErrors === 'all' || touched.has(f));
        $(`${f}-err`).textContent = show ? errors[f] : '';
        if (show) $(f).setAttribute('aria-invalid', 'true'); else $(f).removeAttribute('aria-invalid');
      });
    }
    return Object.keys(errors).length === 0 && $('terms').checked && !!$('lease-start').value;
  }

  function update() {
    const end = leaseEnd();
    const days = Math.ceil(hours / 24);
    const total = (days * Number(cfg.pricePerDay)).toFixed(2);
    $('sum-duration').textContent = `${hours} hours`;
    $('sum-expires').textContent = end ? O.fmtDateTime(end.toISOString()) : '–';
    $('lease-info').innerHTML = `${O.icon('clock')}<span>${hours}-hour lease · The courier must drop off the parcel by ${end ? O.fmtDateTime(end.toISOString()) : '–'} (Lisbon time). The recipient then has ${cfg.pickupWindowDays || 15} days to collect it.</span>`;
    $('price-line').textContent = `${days} day${days > 1 ? 's' : ''} × ${Number(cfg.pricePerDay).toFixed(2)} ${cfg.currency} · VAT included`;
    $('price').textContent = `${total} ${cfg.currency}`;
    const ok = validate(false);
    $('submit').disabled = !ok;
    $('complete-hint').textContent = ok ? 'All required details are complete.' : 'Add recipient details and accept the terms to continue.';
    $('complete-hint').style.color = ok ? 'var(--green)' : 'var(--text-2)';
  }

  async function loadStation() {
    const res = await O.api('GET', `/api/v1/stations/${encodeURIComponent(stationCode)}`);
    if (!res.ok) return;
    station = res.data.station;
    $('loc-title').textContent = `${station.name} · ${station.code}`;
    $('loc-address').textContent = `${station.address}, ${station.city}`;
    const c = res.data.compartments.find(x => x.code === compartmentCode);
    if (c) {
      $('d-h').textContent = `${c.heightCm} cm`; $('d-w').textContent = `${c.widthCm} cm`; $('d-d').textContent = `${c.depthCm} cm`;
      $('size-badge').textContent = O.SIZE_LABELS[c.size];
    }
    O.renderBank($('bank'), res.data.compartments, { stationCode, selected: compartmentCode, selectable: () => false });
  }

  function showConfirmation(r) {
    $('form-view').classList.add('hidden');
    $('head').querySelector('.page-title').textContent = 'You are all set.';
    $('head').querySelector('.page-sub').textContent = 'Print these codes on the parcel label. The drop-off PIN is shown only once.';
    const box = $('confirmation');
    box.classList.remove('hidden');
    box.innerHTML = `
      <div class="grid-2-wide">
        <div class="card card-lg stack">
          <div class="alert alert-success">${O.icon('check')}<div><strong>Compartment ${O.esc(r.compartmentCode)} reserved at ${O.esc(r.stationName)}.</strong><br>
            The courier must drop off the parcel by ${O.fmtDateTime(r.dropOffDeadline)}.</div></div>
          <div><div class="muted tiny">TRACKING CODE</div><div class="code-big" data-testid="tracking-code">${O.esc(r.trackingCode)}</div></div>
          <div class="form-grid-2">
            <div class="code-box"><div class="muted tiny">DROP-OFF PIN (courier, at the locker)</div><div class="code-big" data-testid="drop-off-code">${O.esc(r.dropOffCode)}</div></div>
            <div class="code-box"><div class="muted tiny">DELIVERY CODE (QR, scanned at collection)</div><div class="strong mono" data-testid="delivery-code" style="margin-top:6px">${O.esc(r.deliveryCode)}</div></div>
          </div>
          <div class="row-between">
            <div><div class="muted tiny">Total paid</div><div class="strong">${Number(r.price).toFixed(2)} ${O.esc(r.currency)}</div></div>
            <div class="row"><a class="btn btn-outline" href="/register">Register another</a>
              <a class="btn btn-primary" data-testid="track-link" href="/tracking/${encodeURIComponent(r.trackingCode)}">${O.icon('route')}Track parcel</a></div>
          </div>
        </div>
        <div class="card card-lg center">
          <div class="card-title">Parcel label QR</div>
          <div class="qr" id="qr" style="margin:16px auto" role="img" aria-label="QR code with delivery code ${O.esc(r.deliveryCode)}"></div>
          <p class="muted small">The courier scans this QR when collecting the parcel. The recipient gets a separate pickup code when it arrives.</p>
        </div>
      </div>`;
    if (typeof QRCode !== 'undefined') new QRCode($('qr'), { text: r.deliveryCode, width: 128, height: 128 });
    const recent = O.storeGet('orbit.recent', []).filter(x => x.code !== r.trackingCode);
    recent.unshift({ code: r.trackingCode, station: r.stationName });
    O.storeSet('orbit.recent', recent.slice(0, 5));
    box.querySelector('.code-big').focus && window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  $('reg-form').addEventListener('input', update);
  $('reg-form').addEventListener('change', update);
  fields.forEach(f => $(f).addEventListener('blur', () => { touched.add(f); validate('touched'); }));

  $('reg-form').addEventListener('submit', async e => {
    e.preventDefault();
    if (!validate('all')) { update(); return; }
    $('submit').disabled = true;
    const v = id => $(id).value.trim();
    const body = {
      stationCode, size, compartmentCode,
      leaseStart: lisbonToInstant(v('lease-start')).toISOString(),
      leaseHours: hours,
      sender: { name: v('s-name'), email: v('s-email'), phone: v('s-phone') },
      recipient: { name: v('r-name'), email: v('r-email'), phone: v('r-phone') }
    };
    const res = await O.api('POST', '/api/v1/parcels', body);
    if (res.ok) { showConfirmation(res.data); return; }
    const alertBox = $('form-alert');
    alertBox.className = 'alert alert-error';
    alertBox.innerHTML = `${O.icon('alert')}<div>${O.esc(O.problemMessage(res))}${res.status === 409
      ? ` <a href="/register/${encodeURIComponent(stationCode)}?size=${size}">Choose another compartment</a>` : ''}</div>`;
    update();
  });

  O.api('GET', '/api/v1/config').then(r => {
    if (r.ok) { cfg = r.data; hours = cfg.defaultLeaseHours; }
    renderDurations(); update();
  });
  loadStation();
})();
