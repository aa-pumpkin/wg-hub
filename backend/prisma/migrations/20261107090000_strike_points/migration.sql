-- A strike costs points; how many is fixed when it is given.
ALTER TABLE "DishwasherStrike" ADD COLUMN "points" INTEGER NOT NULL DEFAULT 10;
