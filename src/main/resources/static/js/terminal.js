/* Locker terminal simulator: UC3 drop-off (courier) and UC4 pickup (recipient), including hardware failures. */
(function () {
  'use strict';
  const O = window.Orbit;
  const main = document.getElementById('main');
  const station = main.dataset.station;
  const phone = main.dataset.phone;
  const screen = document.getElementById('screen');
  const base = `/api/v1/terminals/${encodeURIComponent(station)}`;
  let idleTimer;

  function tick() {
    document.getElementById('clock').textContent = new Date().toLocaleTimeString('en-GB', { timeZone: O.TZ, hour: '2-digit', minute: '2-digit' });
  }
  tick(); setInterval(tick, 15000);

  function resetIdle() { clearTimeout(idleTimer); idleTimer = setTimeout(home, 120000); }

  function render(html, focusSel) {
    screen.innerHTML = html;
    const f = screen.querySelector(focusSel || 'h1, h2');
    if (f) { if (!f.hasAttribute('tabindex') && !/INPUT|BUTTON/.test(f.tagName)) f.setAttribute('tabindex', '-1'); f.focus(); }
    resetIdle();
  }

  function home() {
    render(`
      <h1 class="kiosk-title" id="k-title">Welcome! What would you like to do?</h1>
      <p class="muted">Touch an option to start.</p>
      <div class="kiosk-choice">
        <button type="button" id="go-pickup" data-testid="start-pickup">
          <span class="icon-tile">${O.icon('box')}</span><span class="k-title">Collect a parcel</span>
          <span class="muted small">I have a pickup code</span></button>
        <button type="button" id="go-dropoff" data-testid="start-dropoff">
          <span class="icon-tile">${O.icon('truck')}</span><span class="k-title">Drop off a parcel</span>
          <span class="muted small">Courier · drop-off PIN from the label</span></button>
      </div>`);
    document.getElementById('go-pickup').onclick = () => pinScreen('pickup');
    document.getElementById('go-dropoff').onclick = () => pinScreen('dropoff');
  }

  function pinScreen(mode, error) {
    const title = mode === 'pickup' ? 'Enter your pickup code' : 'Enter the drop-off PIN';
    const sub = mode === 'pickup' ? 'You received a 6-digit code by SMS and email.' : 'The 6-digit PIN is printed on the parcel label.';
    render(`
      <h1 class="kiosk-title" id="k-title">${title}</h1>
      <p class="muted">${sub}</p>
      <form id="pin-form" autocomplete="off">
        <label for="pin" class="sr-only">${title}</label>
        <input id="pin" class="input center" inputmode="numeric" maxlength="6" pattern="[0-9]*" data-testid="pin-input"
               style="font-size:26px;letter-spacing:.4em;height:58px;max-width:320px;margin:18px auto;display:block" aria-describedby="pin-error">
        <div id="pin-error" ${error ? '' : 'class="hidden"'} role="alert" data-testid="pin-error" style="max-width:420px;margin:0 auto 14px">${error || ''}</div>
        <div class="keypad" aria-hidden="true">
          ${[1, 2, 3, 4, 5, 6, 7, 8, 9].map(n => `<button type="button" tabindex="-1" data-k="${n}">${n}</button>`).join('')}
          <button type="button" tabindex="-1" data-k="del">⌫</button><button type="button" tabindex="-1" data-k="0">0</button>
          <button type="submit" tabindex="-1" style="background:var(--accent);color:#fff">OK</button>
        </div>
        <div class="row" style="justify-content:center;margin-top:18px">
          <button type="button" class="btn btn-outline" id="back">Back</button>
          <button type="submit" class="btn btn-primary btn-lg" data-testid="pin-submit">${O.icon('lock')}Open compartment</button>
        </div>
      </form>`, '#pin');
    const input = document.getElementById('pin');
    screen.querySelectorAll('[data-k]').forEach(b => b.addEventListener('click', () => {
      input.value = b.dataset.k === 'del' ? input.value.slice(0, -1) : (input.value + b.dataset.k).slice(0, 6);
      resetIdle();
    }));
    document.getElementById('back').onclick = home;
    document.getElementById('pin-form').onsubmit = e => { e.preventDefault(); submitPin(mode, input.value.trim()); };
  }

  function errorHtml(msg) { return `<div class="alert alert-error">${O.icon('alert')}<div>${O.esc(msg)}</div></div>`; }

  async function submitPin(mode, pin) {
    if (!/^\d{6}$/.test(pin)) { pinScreen(mode, errorHtml('The code has 6 digits.')); return; }
    const res = await O.api('POST', `${base}/${mode === 'pickup' ? 'pickups' : 'drop-offs'}`, { code: pin });
    if (res.ok) { outcome(mode, res.data); return; }
    if (res.status === 429) {
      message('warn', 'alert', 'Code entry blocked', `Too many wrong codes. For your security, code entry is blocked for one hour. Need help? Call ${phone}.`);
    } else if (res.status === 503 || res.status === 0) {
      message('warn', 'alert', 'The locker is not responding', `Please call assistance at ${phone}.`);
    } else if (res.status === 409) {
      message('warn', 'alert', 'This parcel cannot be dropped off yet', O.problemMessage(res));
    } else {
      pinScreen(mode, errorHtml(O.problemMessage(res, 'This code is not valid.')));
    }
  }

  function message(kind, iconName, title, text, extra) {
    render(`
      <div class="center">
        <div class="big-icon ${kind}">${O.icon(iconName)}</div>
        <h1 class="kiosk-title" id="k-title" data-testid="screen-title">${O.esc(title)}</h1>
        <p class="muted" style="max-width:520px;margin:10px auto 22px" data-testid="screen-message">${O.esc(text)}</p>
        ${extra || ''}
        <button type="button" class="btn btn-outline btn-lg" id="done" data-testid="finish">Finish</button>
      </div>`);
    document.getElementById('done').onclick = home;
  }

  function outcome(mode, r) {
    switch (r.outcome) {
      case 'DOOR_OPENED':
        render(`
          <div class="center">
            <div class="big-icon info">${O.icon('door')}</div>
            <h1 class="kiosk-title" id="k-title" data-testid="screen-title">Compartment ${O.esc(r.compartmentCode)} is open</h1>
            <p class="muted" data-testid="screen-message">${O.esc(r.message)}</p>
            <button type="button" class="btn btn-primary btn-lg" id="closed" data-testid="door-closed" style="margin-top:18px">${O.icon('check')}I have closed the door</button>
          </div>`);
        document.getElementById('closed').onclick = async () => {
          const res = await O.api('POST', `${base}/${mode === 'pickup' ? 'pickups' : 'drop-offs'}/${encodeURIComponent(r.sessionToken)}/door-closed`);
          if (res.ok) outcome(mode, res.data); else message('warn', 'alert', 'Something went wrong', O.problemMessage(res));
        };
        break;
      case 'DEPOSITED':
        message('ok', 'check', 'Parcel stored', r.message + ` (${r.trackingCode})`);
        break;
      case 'COMPLETED':
        message('ok', 'check', 'Enjoy your parcel!', 'The compartment is closed and free for the next parcel.');
        break;
      case 'DOOR_DID_NOT_OPEN':
      case 'DOOR_DID_NOT_CLOSE': {
        const title = r.outcome === 'DOOR_DID_NOT_OPEN' ? 'The compartment did not open' : 'The door did not close';
        const canReplace = mode === 'dropoff' && r.sessionToken && r.replacementAvailable;
        message('warn', 'alert', title, r.message + ' The malfunction was reported to the operator.',
          canReplace ? `<button type="button" class="btn btn-primary btn-lg" id="replace" data-testid="request-replacement" style="margin:0 8px 12px 0">${O.icon('refresh')}Use another compartment</button>` : '');
        const btn = document.getElementById('replace');
        if (btn) btn.onclick = async () => {
          const res = await O.api('POST', `${base}/drop-offs/${encodeURIComponent(r.sessionToken)}/replacement`);
          if (res.ok) outcome(mode, res.data); else message('warn', 'alert', 'Something went wrong', O.problemMessage(res));
        };
        break;
      }
      case 'REPLACEMENT_ISSUED':
        message('info', 'info', `New compartment: ${r.compartmentCode}`, r.message);
        break;
      case 'NO_REPLACEMENT_AVAILABLE':
        message('warn', 'alert', 'No other compartment available', r.message);
        break;
      default:
        message('info', 'info', r.outcome, r.message);
    }
  }

  home();
})();
