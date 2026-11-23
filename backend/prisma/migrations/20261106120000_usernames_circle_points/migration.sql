-- Accounts move from email to a login name. Existing users keep access: their login
-- becomes the part of the email before the @ (with a number if that is already taken).
ALTER TABLE "User" ADD COLUMN "username" TEXT;

WITH base AS (
  SELECT id, regexp_replace(lower(split_part(email, '@', 1)), '[^a-z0-9._-]', '', 'g') AS name, "createdAt"
  FROM "User"
), padded AS (
  SELECT id, CASE WHEN length(name) >= 3 THEN left(name, 26) ELSE 'user' || name END AS name, "createdAt"
  FROM base
), numbered AS (
  SELECT id, name, row_number() OVER (PARTITION BY name ORDER BY "createdAt", id) AS n FROM padded
)
UPDATE "User" u
SET "username" = CASE WHEN numbered.n = 1 THEN numbered.name ELSE numbered.name || numbered.n END
FROM numbered WHERE numbered.id = u.id;

ALTER TABLE "User" ALTER COLUMN "username" SET NOT NULL;
DROP INDEX "User_email_key";
ALTER TABLE "User" DROP COLUMN "email";
CREATE UNIQUE INDEX "User_username_key" ON "User"("username");

-- CreateEnum
CREATE TYPE "ChoreKind" AS ENUM ('MAIN', 'EXTRA');

-- AlterTable
ALTER TABLE "Chore" ADD COLUMN     "kind" "ChoreKind" NOT NULL DEFAULT 'EXTRA',
ADD COLUMN     "points" INTEGER NOT NULL DEFAULT 0,
ADD COLUMN     "position" INTEGER NOT NULL DEFAULT 0;

-- AlterTable
ALTER TABLE "ChoreCompletion" ADD COLUMN     "points" INTEGER NOT NULL DEFAULT 0;

-- AlterTable
ALTER TABLE "Household" ADD COLUMN     "rotationAnchor" DATE;

-- AlterTable
ALTER TABLE "HouseholdMember" ADD COLUMN     "rotationPosition" INTEGER;

-- Everyone joins the circle in the order they joined the WG.
UPDATE "HouseholdMember" m
SET "rotationPosition" = ordered.position
FROM (
  SELECT id, row_number() OVER (PARTITION BY "householdId" ORDER BY "joinedAt", id) - 1 AS position
  FROM "HouseholdMember"
) ordered
WHERE ordered.id = m.id;

-- Existing weekly chores become main tasks: due on the Sunday of their week, assigned
-- by the circle. Their open dates are regenerated on the next read.
UPDATE "Chore" c
SET "kind" = 'MAIN',
    "points" = 10,
    "position" = ordered.position,
    "startDate" = (date_trunc('week', CURRENT_DATE) + INTERVAL '6 days')::date,
    "generatedUntil" = NULL
FROM (
  SELECT id, row_number() OVER (PARTITION BY "householdId" ORDER BY "createdAt", id) - 1 AS position
  FROM "Chore"
  WHERE "recurrence" = 'WEEKLY' AND "archivedAt" IS NULL
) ordered
WHERE ordered.id = c.id;

DELETE FROM "ChoreAssignment" a
USING "Chore" c
WHERE a."choreId" = c.id AND c."kind" = 'MAIN' AND a."status" = 'PENDING' AND a."dueDate" >= CURRENT_DATE;

UPDATE "Chore" SET "points" = 20 WHERE "kind" = 'EXTRA';

-- Work already done counts.
UPDATE "ChoreCompletion" SET "points" = 10;

-- CreateTable
CREATE TABLE "PasswordResetToken" (
    "id" TEXT NOT NULL,
    "tokenHash" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "createdById" TEXT NOT NULL,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "usedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "PasswordResetToken_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "PasswordResetToken_tokenHash_key" ON "PasswordResetToken"("tokenHash");

-- CreateIndex
CREATE INDEX "PasswordResetToken_userId_idx" ON "PasswordResetToken"("userId");

-- AddForeignKey
ALTER TABLE "PasswordResetToken" ADD CONSTRAINT "PasswordResetToken_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PasswordResetToken" ADD CONSTRAINT "PasswordResetToken_createdById_fkey" FOREIGN KEY ("createdById") REFERENCES "User"("id") ON DELETE CASCADE ON UPDATE CASCADE;
