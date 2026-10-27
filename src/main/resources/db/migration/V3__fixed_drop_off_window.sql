-- The drop-off window is no longer chosen by the partner: it is always a fixed number of working days after the
-- allocation (orbit.rules.drop-off-working-days), stored in drop_off_deadline. The lease columns are not used anymore.
ALTER TABLE parcel DROP COLUMN lease_start;
ALTER TABLE parcel DROP COLUMN lease_hours;
