-- Items are for the whole WG or for one person; buying one with a price creates an expense.
ALTER TABLE "ShoppingItem" ADD COLUMN "personal" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "ShoppingItem" ADD COLUMN "expenseId" TEXT;
CREATE UNIQUE INDEX "ShoppingItem_expenseId_key" ON "ShoppingItem"("expenseId");
ALTER TABLE "ShoppingItem" ADD CONSTRAINT "ShoppingItem_expenseId_fkey" FOREIGN KEY ("expenseId") REFERENCES "Expense"("id") ON DELETE SET NULL ON UPDATE CASCADE;
