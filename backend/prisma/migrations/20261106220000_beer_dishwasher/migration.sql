-- CreateEnum
CREATE TYPE "SettlementKind" AS ENUM ('PAYMENT', 'FORGIVEN', 'BEER');

-- AlterTable
ALTER TABLE "ExpenseSettlement" ADD COLUMN     "beers" INTEGER,
ADD COLUMN     "kind" "SettlementKind" NOT NULL DEFAULT 'PAYMENT';

-- AlterTable
ALTER TABLE "Household" ADD COLUMN     "beerPriceCents" INTEGER NOT NULL DEFAULT 150,
ADD COLUMN     "dishwasherCycle" INTEGER NOT NULL DEFAULT 1,
ADD COLUMN     "dishwasherCycleStart" TIMESTAMP(3),
ADD COLUMN     "dishwasherTarget" INTEGER;

-- CreateTable
CREATE TABLE "BeerRequest" (
    "id" TEXT NOT NULL,
    "householdId" TEXT NOT NULL,
    "fromUserId" TEXT NOT NULL,
    "toUserId" TEXT NOT NULL,
    "amountCents" INTEGER NOT NULL,
    "beers" INTEGER NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "BeerRequest_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "DishwasherEntry" (
    "id" TEXT NOT NULL,
    "householdId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "cycle" INTEGER NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "DishwasherEntry_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "DishwasherCycle" (
    "id" TEXT NOT NULL,
    "householdId" TEXT NOT NULL,
    "number" INTEGER NOT NULL,
    "winnerId" TEXT NOT NULL,
    "finishedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "DishwasherCycle_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "DishwasherStrike" (
    "cycleId" TEXT NOT NULL,
    "userId" TEXT NOT NULL,

    CONSTRAINT "DishwasherStrike_pkey" PRIMARY KEY ("cycleId","userId")
);

-- CreateIndex
CREATE UNIQUE INDEX "BeerRequest_householdId_fromUserId_toUserId_key" ON "BeerRequest"("householdId", "fromUserId", "toUserId");

-- CreateIndex
CREATE INDEX "DishwasherEntry_householdId_cycle_idx" ON "DishwasherEntry"("householdId", "cycle");

-- CreateIndex
CREATE UNIQUE INDEX "DishwasherCycle_householdId_number_key" ON "DishwasherCycle"("householdId", "number");

-- AddForeignKey
ALTER TABLE "BeerRequest" ADD CONSTRAINT "BeerRequest_householdId_fkey" FOREIGN KEY ("householdId") REFERENCES "Household"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "BeerRequest" ADD CONSTRAINT "BeerRequest_fromUserId_fkey" FOREIGN KEY ("fromUserId") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "BeerRequest" ADD CONSTRAINT "BeerRequest_toUserId_fkey" FOREIGN KEY ("toUserId") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "DishwasherEntry" ADD CONSTRAINT "DishwasherEntry_householdId_fkey" FOREIGN KEY ("householdId") REFERENCES "Household"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "DishwasherEntry" ADD CONSTRAINT "DishwasherEntry_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "DishwasherCycle" ADD CONSTRAINT "DishwasherCycle_householdId_fkey" FOREIGN KEY ("householdId") REFERENCES "Household"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "DishwasherCycle" ADD CONSTRAINT "DishwasherCycle_winnerId_fkey" FOREIGN KEY ("winnerId") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "DishwasherStrike" ADD CONSTRAINT "DishwasherStrike_cycleId_fkey" FOREIGN KEY ("cycleId") REFERENCES "DishwasherCycle"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "DishwasherStrike" ADD CONSTRAINT "DishwasherStrike_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

