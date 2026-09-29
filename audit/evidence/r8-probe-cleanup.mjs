// R8 probe test-data cleanup — deletes ALL disposable probe accounts (email LIKE r8probe%)
// by explicit related-row deletion first, then the users. Idempotent. DB-layer cleanup of
// synthetic accounts only; the production DELETE /api/me balance guard remains untouched.
import { PrismaClient } from "@prisma/client";
const db = new PrismaClient();
const users = await db.user.findMany({
  where: { email: { startsWith: "r8probe" } },
  select: { id: true, email: true, balance: true },
});
if (users.length === 0) { console.log("no r8probe accounts found — nothing to clean"); await db.$disconnect(); process.exit(0); }
const ids = users.map((u) => u.id);
const m1 = await db.message.deleteMany({ where: { OR: [{ senderId: { in: ids } }, { receiverId: { in: ids } }] } });
const m2 = await db.friendRequest.deleteMany({ where: { OR: [{ senderId: { in: ids } }, { receiverId: { in: ids } }] } });
const m3 = await db.friendship.deleteMany({ where: { OR: [{ userId: { in: ids } }, { friendId: { in: ids } }] } });
for (const u of users) await db.user.delete({ where: { id: u.id } });
console.log(`cleaned: ${users.length} users (${users.map((u) => u.email).join(", ")}), messages=${m1.count}, friendRequests=${m2.count}, friendships=${m3.count}`);
const leftover = await db.user.count({ where: { email: { startsWith: "r8probe" } } });
console.log(`leftover r8probe accounts: ${leftover} (expect 0)`);
await db.$disconnect();
