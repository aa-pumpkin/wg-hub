-- Freezing replaces sitting out of the circle: whoever sat out is now frozen, and keeps a
-- seat to come back to.
ALTER TABLE "HouseholdMember" ADD COLUMN "frozenAt" TIMESTAMP(3);

UPDATE "HouseholdMember" m
SET "frozenAt" = CURRENT_TIMESTAMP,
    "rotationPosition" = seats.position
FROM (
  SELECT id,
         (SELECT COALESCE(MAX(o."rotationPosition"), -1) FROM "HouseholdMember" o WHERE o."householdId" = x."householdId")
           + row_number() OVER (PARTITION BY "householdId" ORDER BY "joinedAt", id) AS position
  FROM "HouseholdMember" x
  WHERE "rotationPosition" IS NULL
) seats
WHERE seats.id = m.id;
