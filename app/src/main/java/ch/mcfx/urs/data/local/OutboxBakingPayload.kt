package ch.mcfx.urs.data.local

import kotlinx.serialization.Serializable

@Serializable
data class OutboxBakePlanStepPayload(
    val index: Int,
    val label: String,
    val plannedAtMillis: Long,
)

/**
 * What gets JSON-encoded for a queued plan creation. Steps ride along in
 * this single payload rather than getting their own outbox rows — unlike
 * shopping-list items, a bake plan's steps are always created together with
 * their parent, never independently afterward, so there's no
 * still-syncing-parent case for a step create to resolve (see
 * SyncManager.replayCreateBakePlan).
 */
@Serializable
data class OutboxBakePlanPayload(
    val templateKey: String,
    val anchorAtMillis: Long,
    val steps: List<OutboxBakePlanStepPayload>,
)

// Identifies its target by the step's stable local row id, not its
// (possibly still-null) serverId — the user can mark a step done/snooze it
// before the plan's own create mutation has synced (no network at plan
// creation time), so at queue time there may be no serverId yet. Resolved
// at replay time instead (see SyncManager.replayUpdateBakePlanStep), same
// "not yet synced, retry later" shape as replayCreateListItem's
// still-pending-parent case.
@Serializable
data class OutboxBakePlanStepUpdatePayload(
    val localStepId: Long,
    val done: Boolean? = null,
    val snoozedAtMillis: Long? = null,
)

/** Same not-yet-synced-parent handling as [OutboxBakePlanStepUpdatePayload] — resolved by local row id at replay time. */
@Serializable
data class OutboxBakePlanCancelPayload(
    val localPlanId: Long,
)
