# Nominatim fixtures

Response bodies of `GET /search?format=jsonv2&limit=1&countrycodes=pt&q=...`, used by `NominatimGeocoderIT` through
WireMock. They follow the shape of real Nominatim answers (lat/lon are strings); the real server is never called by the
tests (OSM usage policy, NFR4).

| File | Request | Meaning |
|---|---|---|
| `search-rua-de-coimbra.json` | `q=Rua de Coimbra 10, Aveiro` | one result: the first one is used |
| `search-empty.json` | an unknown address | no result: the address was not found |
| `search-malformed.json` | any | a body that is not a result array: a provider error |
