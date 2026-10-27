// Orbit Lockers - k6 load test for the performance NFRs of the report (section 2.4):
//   NFR1: 95th percentile of the response time below 500 ms with 50 concurrent users (every request)
//   NFR5: 95th percentile of the response time below 300 ms with 50 concurrent users (read requests)
//
// 50 virtual users run at the same time for one minute (after a 15 s ramp-up). Each user is a recipient
// (finds a locker, looks at a station, tracks a parcel) or, one user in five, a partner (registers a parcel,
// tracks it and cancels it so the stations do not fill up).
//
// The application must run with the "loadtest" profile: the geocoder is then the in-process stub, so the real
// OpenStreetMap Nominatim server is NEVER called by the load test (NFR4).
//   docker compose up -d db
//   SPRING_PROFILES_ACTIVE=loadtest ./mvnw spring-boot:run
//   k6 run load-tests/nfr-load.js                     (BASE_URL defaults to http://localhost:8080)
import http from 'k6/http';
import { check, group, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const USERS = 50;
const STATIONS = ['ORB-AVR-001', 'ORB-AVR-002', 'ORB-AVR-003', 'ORB-AVR-004', 'ORB-AVR-005'];
const ADDRESSES = ['Rua de Coimbra 10, Aveiro', 'Avenida Dr. Lourenço Peixinho 50, Aveiro',
  'Universidade de Aveiro', 'Rua da Pega, Aveiro', 'Ílhavo'];
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  scenarios: {
    fifty_concurrent_users: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: USERS },   // ramp-up
        { duration: '60s', target: USERS },   // 50 concurrent users: the measured window
        { duration: '5s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<500'],              // NFR1: every request
    'http_req_duration{type:read}': ['p(95)<300'],   // NFR5: read requests
    'http_req_failed': ['rate<0.01'],                // the response times only count if the requests work
    'checks': ['rate>0.99'],
    'vus_max': [`value>=${USERS}`],                  // the test really reached 50 concurrent users
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const pick = (a) => a[Math.floor(Math.random() * a.length)];

function parcelBody(station, size) {
  return JSON.stringify({
    stationCode: station, size,
    sender: { name: 'Load Test', email: 'load@example.com', phone: '+351 910 000 000' },
    recipient: { name: 'Load Recipient', email: 'recipient@example.com', phone: '+351 920 000 000' },
  });
}

/** Registers one parcel that every recipient tracks; fails the run early if the application is not reachable. */
export function setup() {
  const config = http.get(`${BASE}/api/v1/config`);
  if (config.status !== 200) {
    throw new Error(`The application is not reachable at ${BASE} (HTTP ${config.status})`);
  }
  const r = http.post(`${BASE}/api/v1/parcels`, parcelBody('ORB-AVR-004', 'S'), { headers: JSON_HEADERS });
  return { trackingCode: r.status === 201 ? r.json('trackingCode') : null };
}

/** UC1 + UC5: read-only journey. */
function recipient(data) {
  group('UC1 find a locker', () => {
    const all = http.get(`${BASE}/api/v1/stations`, { tags: { type: 'read', name: 'GET /stations' } });
    check(all, { 'all stations 200': (r) => r.status === 200 });

    const radius = pick(['', '&radiusKm=2', '&radiusKm=5', '&radiusKm=20']);
    const nearby = http.get(`${BASE}/api/v1/stations/nearby?address=${encodeURIComponent(pick(ADDRESSES))}${radius}`,
      { tags: { type: 'read', name: 'GET /stations/nearby' } });
    check(nearby, { 'nearby 200': (r) => r.status === 200 });

    const station = http.get(`${BASE}/api/v1/stations/${pick(STATIONS)}`,
      { tags: { type: 'read', name: 'GET /stations/{code}' } });
    check(station, { 'station 200': (r) => r.status === 200 });
  });

  if (data.trackingCode) {
    group('UC5 track a parcel', () => {
      const t = http.get(`${BASE}/api/v1/parcels/${data.trackingCode}`,
        { tags: { type: 'read', name: 'GET /parcels/{trackingCode}' } });
      check(t, { 'track 200': (r) => r.status === 200 });
    });
  }
}

/** UC2: register, track and cancel (writes). A full station answers 409 STATION_FULL, which is a valid answer. */
function partner() {
  group('UC2 register a parcel', () => {
    const reg = http.post(`${BASE}/api/v1/parcels`, parcelBody(pick(STATIONS), pick(['S', 'M', 'L'])), {
      headers: JSON_HEADERS,
      tags: { type: 'write', name: 'POST /parcels' },
      responseCallback: http.expectedStatuses(201, 409),
    });
    check(reg, { 'register 201 or 409 (station full)': (r) => r.status === 201 || r.status === 409 });
    if (reg.status !== 201) {
      return;
    }
    const tc = reg.json('trackingCode');
    const t = http.get(`${BASE}/api/v1/parcels/${tc}`, { tags: { type: 'read', name: 'GET /parcels/{trackingCode}' } });
    check(t, { 'new parcel is CREATED': (r) => r.status === 200 && r.json('state') === 'CREATED' });

    const c = http.post(`${BASE}/api/v1/parcels/${tc}/cancel`, null,
      { tags: { type: 'write', name: 'POST /parcels/{trackingCode}/cancel' } });
    check(c, { 'cancel 200': (r) => r.status === 200 });
  });
}

export default function (data) {
  if (__VU % 5 === 0) {
    partner();
  } else {
    recipient(data);
  }
  sleep(1);   // think time between journeys
}
