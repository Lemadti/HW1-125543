-- Staging data: locker stations in the Aveiro zone (Portugal) with real coordinates.
INSERT INTO station (code, name, address, postal_code, city, latitude, longitude, access_notes) VALUES
  ('ORB-AVR-001', 'Fórum Aveiro',            'Rua do Batalhão de Caçadores 10',      '3810-064', 'Aveiro', 40.64120, -8.65270,
   'Ground floor, next to the entrance facing the Ria canal. Step-free access.'),
  ('ORB-AVR-002', 'Estação de Aveiro',       'Largo da Estação',                     '3800-178', 'Aveiro', 40.64400, -8.64000,
   'Inside the railway station hall, by the ticket machines. Step-free access.'),
  ('ORB-AVR-003', 'Universidade de Aveiro',  'Campus Universitário de Santiago',     '3810-193', 'Aveiro', 40.63050, -8.65750,
   'Next to the main library (Biblioteca), under the covered walkway.'),
  ('ORB-AVR-004', 'Glicínias Plaza',         'Rua Dom Manuel Barbuda e Vasconcelos', '3810-498', 'Aveiro', 40.62670, -8.64070,
   'Level 0, near the supermarket entrance. Step-free access.'),
  ('ORB-AVR-005', 'Ílhavo Centro',           'Avenida 25 de Abril',                  '3830-044', 'Ílhavo', 40.60070, -8.66700,
   'Beside the municipal market, under the arcade.');

-- Bank layout (front view): 4 rows per column; rows 1-2 small, row 3 medium, row 4 large.
-- Position n -> column (n-1)/4, row (n-1)%4. Code = <size>-<position>, e.g. M-07.
INSERT INTO compartment (station_id, code, position, size)
SELECT s.id,
       (CASE WHEN (p - 1) % 4 < 2 THEN 'S' WHEN (p - 1) % 4 = 2 THEN 'M' ELSE 'L' END) || '-' || lpad(p::text, 2, '0'),
       p,
       CASE WHEN (p - 1) % 4 < 2 THEN 'S' WHEN (p - 1) % 4 = 2 THEN 'M' ELSE 'L' END
FROM station s
CROSS JOIN generate_series(1, 16) AS p
WHERE s.code <> 'ORB-AVR-004' OR p <= 12;

-- A couple of compartments start out of service (as in the mockups).
UPDATE compartment SET status = 'OUT_OF_SERVICE', status_reason = 'Door sensor maintenance'
WHERE code IN ('S-06', 'L-12') AND station_id = (SELECT id FROM station WHERE code = 'ORB-AVR-001');
