-- Add FOURTEENTH to the valid_amendment check constraint.
-- The Fourteenth Amendment (due process, equal protection) is commonly cited
-- in civil rights encounters alongside First, Fourth, and Fifth.

ALTER TABLE videos.video_amendments
    DROP CONSTRAINT valid_amendment,
    ADD CONSTRAINT valid_amendment CHECK (amendment IN ('FIRST', 'SECOND', 'FOURTH', 'FIFTH', 'FOURTEENTH'));
