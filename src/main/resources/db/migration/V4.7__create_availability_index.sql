-- Partial index tuned for the availability-overlap query:
-- SELECT 1 FROM bookings WHERE property_id = ? AND check_in < ? AND check_out > ?
--                          AND status NOT IN ('CANCELLED','DECLINED')
-- Excluding CANCELLED/DECLINED rows from the index keeps it small and makes lookups fast.
CREATE INDEX idx_bookings_availability
    ON bookings (property_id, check_in, check_out)
    WHERE status NOT IN ('CANCELLED', 'DECLINED');
