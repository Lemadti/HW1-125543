// Orbit Lockers - load test (k6). Verifies NFR1 (p95 < 500 ms) and NFR5 (p95 < 300 ms for reads) with 50 VUs.
// The application must run with the "loadtest" profile so the geocoder is the in-process stub
// (the real Nominatim provider is NEVER called by load tests):
//   SPRING_PROFILES_ACTIVE=loadtest ./mvnw spring-boot:run      (or docker compose, see README)
//   k6 run load-tests/orbit-load.js                             (BASE_URL defaults to http://localhost:8080)
import http from 'k6/http';
import { check, group, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const STATIONS = ['ORB-AVR-001', 'ORB-AVR-002', 'ORB-AVR-003', 'ORB-AVR-004', 'ORB-AVR-005'];
const ADDRESSES = ['Rua de Coimbra 10, Aveiro', 'Avenida Dr. Lourenço Peixinho 50, Aveiro', 'Universidade de Aveiro', 'Ílhavo'];
const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

export const options = {
  scenarios: {
    recipients: {               // UC1 + UC5: browse and track (read-heavy)
      executor: 'ramping-vus',
      exec: 'recipient',
      startVUs: 0,
      stages: [{ duration: '15s', target: 40 }, { duration: '45s', target: 40 }, { duration: '10s', target: 0 }],
    },
    partners: {                 // UC2: register (and cancel, to keep capacity) through the REST API
      executor: 'constant-vus',
      exec: 'partner',
      vus: 10,
      duration: '70s',
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<500'],                       // NFR1: all requests, 50 concurrent users
    'http_req_duration{type:read}': ['p(95)<300'],            // NFR5: read endpoints
    'http_req_failed': ['rate<0.01'],
    'checks': ['rate>0.99'],
  },
};

const pick = (a) => a[Math.floor(Math.random() * a.length)];

export function setup() {
  // one parcel to track
  const r = http.post(`${BASE}/api/v1/parcels`, JSON.stringify(body('ORB-AVR-004', 'S')), JSON_HEADERS);
  return { trackingCode: r.status === 201 ? r.json('trackingCode') : null };
}

function body(station, size) {
  return {
    stationCode: station, size,
    sender: { name: 'Load Test', email: 'load@example.com', phone: '+351 910 000 000' },
    recipient: { name: 'Load Recipient', email: 'recipient@example.com', phone: '+351 920 000 000' },
  };
}

export function recipient(data) {
  group('find a locker', () => {
    const r = http.get(`${BASE}/api/v1/stations/nearby?address=${encodeURIComponent(pick(ADDRESSES))}`, { tags: { type: 'read', name: 'nearby' } });
    check(r, { 'nearby 200': (x) => x.status === 200 });
    const s = http.get(`${BASE}/api/v1/stations/${pick(STATIONS)}`, { tags: { type: 'read', name: 'station' } });
    check(s, { 'station 200': (x) => x.status === 200 });
  });
  if (data.trackingCode) {
    group('track', () => {
      const t = http.get(`${BASE}/api/v1/parcels/${data.trackingCode}`, { tags: { type: 'read', name: 'track' } });
      check(t, { 'track 200': (x) => x.status === 200 });
    });
  }
  sleep(1);
}

export function partner() {
  const r = http.post(`${BASE}/api/v1/parcels`, JSON.stringify(body(pick(STATIONS), pick(['S', 'M', 'L']))),
    Object.assign({ tags: { type: 'write', name: 'register' }, responseCallback: http.expectedStatuses(201, 409) }, JSON_HEADERS));
  check(r, { 'register 201 or 409 (full)': (x) => x.status === 201 || x.status === 409 });
  if (r.status === 201) {
    const c = http.post(`${BASE}/api/v1/parcels/${r.json('trackingCode')}/cancel`, null, { tags: { type: 'write', name: 'cancel' } });
    check(c, { 'cancel 200': (x) => x.status === 200 });
  }
  sleep(1);
}
