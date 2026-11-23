package de.wghub.app.data

import kotlinx.serialization.Serializable

// Mirrors frontend/src/types/index.ts. Field names match the API's JSON.

@Serializable
data class UserSummary(val id: String, val displayName: String, val avatarColor: String)

@Serializable
data class User(
    val id: String,
    val displayName: String,
    val avatarColor: String,
    val username: String,
    val locale: String,
) {
    val summary get() = UserSummary(id, displayName, avatarColor)
}

@Serializable
data class HouseholdListItem(
    val id: String,
    val name: String,
    val icon: String,
    val role: String,
    val memberCount: Int,
)

@Serializable
data class Household(
    val id: String,
    val name: String,
    val address: String? = null,
    val icon: String,
    val timezone: String,
    val beerPriceCents: Int = 0,
    val dishwasherTarget: Int? = null,
)

@Serializable
data class Member(
    val id: String,
    val displayName: String,
    val avatarColor: String,
    val username: String,
    val role: String,
    val joinedAt: String,
    val frozen: Boolean,
)

@Serializable
data class HouseholdDetail(val household: Household, val role: String, val members: List<Member>) {
    val isAdmin get() = role == "ADMIN"
}

@Serializable
data class Chore(
    val id: String,
    val title: String,
    val description: String? = null,
    val kind: String,
    val points: Int,
    val position: Int = 0,
    val recurrence: String,
    val startDate: String,
    val createdById: String,
    val rotation: List<UserSummary>,
)

@Serializable
data class Occurrence(
    val id: String,
    val choreId: String,
    val dueDate: String,
    val status: String,
    val title: String,
    val description: String? = null,
    val recurrence: String,
    val kind: String,
    val points: Int,
    val assignee: UserSummary? = null,
    val completedAt: String? = null,
    val completedBy: UserSummary? = null,
    val awardedPoints: Int? = null,
)

@Serializable
data class CompletionResult(val occurrence: Occurrence, val earned: Int, val level: Int, val leveledUp: Boolean)

@Serializable
data class Seat(val user: UserSummary, val frozen: Boolean)

@Serializable
data class PlanWeek(val dueDate: String, val items: List<Occurrence>)

@Serializable
data class ChorePlan(val circle: List<Seat>, val weeks: List<PlanWeek>)

@Serializable
data class ChoreInput(
    val title: String,
    val description: String,
    val recurrence: String,
    val startDate: String,
    val rotation: List<String>,
    val kind: String? = null,
    val points: Int? = null,
)

@Serializable internal data class UserResponse(val user: User)
@Serializable internal data class HouseholdsResponse(val households: List<HouseholdListItem>)
@Serializable internal data class ChoresResponse(val chores: List<Chore>)
@Serializable internal data class OccurrencesResponse(val occurrences: List<Occurrence>)
@Serializable internal data class OccurrenceResponse(val occurrence: Occurrence)

@Serializable
data class MemberProgress(
    val user: UserSummary,
    val points: Int,
    val monthPoints: Int,
    val weekPoints: Int,
    val completed: Int,
    val level: Int,
    val levelFloor: Int,
    val levelNext: Int,
    val streak: Int,
    val badges: List<String>,
)

@Serializable
data class ExpenseRef(val id: String, val amountCents: Int)

@Serializable
data class ShoppingItem(
    val id: String,
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val note: String? = null,
    val personal: Boolean = false,
    val expense: ExpenseRef? = null,
    val status: String,
    val createdBy: UserSummary,
    val boughtBy: UserSummary? = null,
    val boughtAt: String? = null,
    val createdAt: String,
)

@Serializable
data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String? = null,
    val category: String,
    val startsAt: String,
    val endsAt: String? = null,
    val allDay: Boolean,
    val createdBy: UserSummary,
    val participants: List<UserSummary>,
)

@Serializable
data class Note(
    val id: String,
    val content: String,
    val color: String,
    val pinned: Boolean,
    val archivedAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val author: UserSummary,
)

@Serializable
data class ExpenseShare(val user: UserSummary, val shareCents: Int)

@Serializable
data class Expense(
    val id: String,
    val description: String,
    val amountCents: Int,
    val date: String,
    val splitMode: String,
    val paidBy: UserSummary,
    val createdById: String,
    val participants: List<ExpenseShare>,
)

@Serializable
data class Settlement(
    val id: String,
    val amountCents: Int,
    val date: String,
    val note: String? = null,
    val kind: String,
    val beers: Int? = null,
    val fromUser: UserSummary,
    val toUser: UserSummary,
    val createdById: String,
)

@Serializable
data class BeerRequest(val id: String, val fromUserId: String, val toUserId: String, val amountCents: Int, val beers: Int)

@Serializable
data class Balance(val user: UserSummary, val cents: Int)

@Serializable
data class SuggestedPayment(val fromUserId: String, val toUserId: String, val amountCents: Int)

@Serializable
data class Balances(
    val balances: List<Balance>,
    val suggestions: List<SuggestedPayment>,
    val beerRequests: List<BeerRequest> = emptyList(),
)

@Serializable
data class DishwasherMember(val user: UserSummary, val frozen: Boolean, val count: Int, val crowns: Int, val strikes: Int)

@Serializable
data class DishwasherCycle(val winner: UserSummary, val struck: List<UserSummary>)

@Serializable
data class DishwasherState(
    val target: Int? = null,
    val total: Int,
    val strikePenalty: Int = 0,
    val cycle: Int,
    val crownHolderId: String? = null,
    val lastCycle: DishwasherCycle? = null,
    val members: List<DishwasherMember>,
)

@Serializable
data class ShoppingSummary(val items: List<ShoppingItem>, val pendingCount: Int)

@Serializable
data class Dashboard(
    val today: String,
    val tasks: List<Occurrence>,
    val upcomingTasks: List<Occurrence> = emptyList(),
    val shopping: ShoppingSummary,
    val events: List<CalendarEvent>,
    val notes: List<Note>,
    val myBalanceCents: Int,
    val myProgress: MemberProgress? = null,
    val myRank: Int = 0,
)

@Serializable
data class Invitation(val id: String, val code: String, val expiresAt: String? = null, val useCount: Int, val createdAt: String)

@Serializable
data class InvitationPreview(val householdName: String, val householdIcon: String, val memberCount: Int)

@Serializable
data class MemberStats(val open: Map<String, Int> = emptyMap(), val completed: Map<String, Int> = emptyMap())

@Serializable
data class CalendarRange(val events: List<CalendarEvent>, val occurrences: List<Occurrence>)

@Serializable internal data class ShoppingResponse(val items: List<ShoppingItem>)
@Serializable internal data class ShoppingItemResponse(val item: ShoppingItem)
@Serializable internal data class NotesResponse(val notes: List<Note>)
@Serializable internal data class ExpensesResponse(val expenses: List<Expense>)
@Serializable internal data class SettlementsResponse(val settlements: List<Settlement>)
@Serializable internal data class LeaderboardResponse(val members: List<MemberProgress>)
@Serializable internal data class InvitationsResponse(val invitations: List<Invitation>)
