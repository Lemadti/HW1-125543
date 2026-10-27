/* Register step 3: contacts -> POST /api/v1/parcels (UC2), then the label (tracking code, QR, drop-off PIN). */
(function () {
  'use strict';
  const O = window.Orbit;
  const main = document.getElementById('main');
  const stationCode = main.dataset.station;
  const compartmentCode = main.dataset.compartment;
  const size = (main.dataset.size || 'M').toUpperCase();
  let cfg = { dropOffWorkingDays: 10, price: 3, currency: 'EUR', pickupWindowDays: 15 };
  let station = null;

  const $ = id => document.getElementById(id);
  const fields = ['s-name', 's-email', 's-phone', 'r-name', 'r-email', 'r-phone'];
  const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  const PHONE = /^\+?[0-9 ()-]{7,20}$/;

  // Preview of the drop-off deadline: N working days (Mon-Fri) from now, same time of day. The server computes the
  // authoritative value at registration (in Lisbon time); this preview uses the browser's calendar.
  function dropOffDeadline() {
    const d = new Date();
    let counted = 0;
    while (counted < cfg.dropOffWorkingDays) {
      d.setDate(d.getDate() + 1);
      if (d.getDay() !== 0 && d.getDay() !== 6) counted++;
    }
    return d;
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
    return Object.keys(errors).length === 0 && $('terms').checked;
  }

  function update() {
    const end = dropOffDeadline().toISOString();
    $('sum-duration').textContent = `${cfg.dropOffWorkingDays} working days`;
    $('sum-expires').textContent = O.fmtDateTime(end);
    $('lease-info').innerHTML = `${O.icon('clock')}<span>The courier has ${cfg.dropOffWorkingDays} working days from now to drop off the parcel (until about ${O.fmtDateTime(end)}, Lisbon time). The recipient then has ${cfg.pickupWindowDays || 15} days to collect it.</span>`;
    $('price-line').textContent = 'Flat price · VAT included';
    $('price').textContent = `${Number(cfg.price).toFixed(2)} ${cfg.currency}`;
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
    if (r.ok) cfg = r.data;
    update();
  });
  loadStation();
})();
