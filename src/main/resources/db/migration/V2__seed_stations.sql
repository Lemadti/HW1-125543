-- Staging data: locker stations in Warsaw (Śródmieście) with real coordinates.
INSERT INTO station (code, name, address, postal_code, city, latitude, longitude, access_notes) VALUES
  ('ORB-WAW-014', 'Centrum',        'Marszałkowska 104/122', '00-017', 'Warsaw', 52.23165, 21.00917,
   'Outside the east entrance, facing Marszałkowska. Step-free pavement access.'),
  ('ORB-WAW-021', 'Świętokrzyska',  'Świętokrzyska 18',      '00-052', 'Warsaw', 52.23590, 21.01020,
   'Next to the metro exit, under the arcade. Step-free access.'),
  ('ORB-WAW-007', 'Złote Tarasy',   'Złota 59',              '00-120', 'Warsaw', 52.22995, 21.00255,
   'Level 0, near the main entrance from Złota street.'),
  ('ORB-WAW-033', 'Nowy Świat',     'Nowy Świat 32',         '00-373', 'Warsaw', 52.23370, 21.01900,
   'In the passage between Nowy Świat and Foksal. Step-free access.'),
  ('ORB-WAW-040', 'Plac Unii',      'Puławska 2',            '02-566', 'Warsaw', 52.21280, 21.02210,
   'Ground floor of the shopping centre, by the parking entrance.');

-- Bank layout (front view): 4 rows per column; rows 1-2 small, row 3 medium, row 4 large.
-- Position n -> column (n-1)/4, row (n-1)%4. Code = <size>-<position>, e.g. M-07.
INSERT INTO compartment (station_id, code, position, size)
SELECT s.id,
       (CASE WHEN (p - 1) % 4 < 2 THEN 'S' WHEN (p - 1) % 4 = 2 THEN 'M' ELSE 'L' END) || '-' || lpad(p::text, 2, '0'),
       p,
       CASE WHEN (p - 1) % 4 < 2 THEN 'S' WHEN (p - 1) % 4 = 2 THEN 'M' ELSE 'L' END
FROM station s
CROSS JOIN generate_series(1, 16) AS p
WHERE s.code <> 'ORB-WAW-033' OR p <= 12;

-- A couple of compartments start out of service (as in the mockups).
UPDATE compartment SET status = 'OUT_OF_SERVICE', status_reason = 'Door sensor maintenance'
WHERE code IN ('S-06', 'L-12') AND station_id = (SELECT id FROM station WHERE code = 'ORB-WAW-014');
