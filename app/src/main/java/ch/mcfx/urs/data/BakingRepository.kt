package ch.mcfx.urs.data

import android.content.Context
import ch.mcfx.urs.data.local.BakePlanDao
import ch.mcfx.urs.data.local.BakePlanEntity
import ch.mcfx.urs.data.local.BakePlanStepDao
import ch.mcfx.urs.data.local.BakePlanStepEntity
import ch.mcfx.urs.data.local.OutboxBakePlanCancelPayload
import ch.mcfx.urs.data.local.OutboxBakePlanPayload
import ch.mcfx.urs.data.local.OutboxBakePlanStepPayload
import ch.mcfx.urs.data.local.OutboxBakePlanStepUpdatePayload
import ch.mcfx.urs.data.local.OutboxDao
import ch.mcfx.urs.data.local.OutboxMutationEntity
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.data.local.localBakePlanId
import ch.mcfx.urs.data.local.localIdStandIn
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.data.sync.SyncManager
import ch.mcfx.urs.notifications.BakingStepAlarmScheduler
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Offline-first write path for baking plans () — same shape as
 * [ShoppingListRepository], extended with the device-alarm side effect this
 * feature additionally has: one exact alarm scheduled per step, right after
 * the local rows are written (see [BakingStepAlarmScheduler]). [context] is
 * needed only for that alarm side effect — no other repository in this app
 * has one.
 */
class BakingRepository(
    private val context: Context,
    private val bakePlanDao: BakePlanDao,
    private val bakePlanStepDao: BakePlanStepDao,
    private val outboxDao: OutboxDao,
    private val syncManager: SyncManager,
    private val applicationScope: CoroutineScope,
    private val json: Json,
) {

    fun observePlans(statuses: List<String>): Flow<List<BakePlanEntity>> = bakePlanDao.observeByStatuses(statuses)

    /** Every plan regardless of status — used to resolve a single plan by [ch.mcfx.urs.data.local.publicId] (see [ShoppingListRepository.observeLists]'s identical use in `ListDetailViewModel`). */
    fun observeAllPlans(): Flow<List<BakePlanEntity>> = bakePlanDao.observeAll()

    fun observeSteps(planId: String): Flow<List<BakePlanStepEntity>> = bakePlanStepDao.observeByPlanId(planId)

    suspend fun getPlan(localId: Long): BakePlanEntity? = bakePlanDao.getById(localId)

    /**
     * Computes every step's instant from [template] backward off [anchor]
     * (the design's single fixed anchor, see [RecipeStepTemplate.plannedAtMillis]),
     * writes the plan + step rows locally, assigns and arms one exact alarm
     * per step, then queues a single [OutboxMutationEntity.TYPE_CREATE_BAKE_PLAN]
     * mutation carrying every step — mirrors [ShoppingListRepository.createList]'s
     * offline-first shape.
     */
    suspend fun createPlan(template: RecipeTemplate, anchor: LocalDateTime) {
        val anchorMillis = anchor.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val payload = OutboxBakePlanPayload(
            templateKey = template.key,
            anchorAtMillis = anchorMillis,
            steps = template.steps.map { step ->
                OutboxBakePlanStepPayload(index = step.index, label = step.label, plannedAtMillis = step.plannedAtMillis(anchor))
            },
        )
        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_BAKE_PLAN,
                payloadJson = json.encodeToString(payload),
                createdAt = System.currentTimeMillis(),
            ),
        )
        val localPlanId = bakePlanDao.upsert(
            BakePlanEntity(
                outboxId = outboxId,
                templateKey = template.key,
                anchorAtMillis = anchorMillis,
                status = STATUS_ACTIVE,
                syncStatus = SyncStatus.PENDING,
            ),
        )
        val planPublicId = localIdStandIn(localPlanId)

        template.steps.forEach { step ->
            val plannedAtMillis = step.plannedAtMillis(anchor)
            // alarmId is derived from this row's own Room id, so it's only
            // known after the initial insert — a small follow-up update
            // stamps it in, see BakePlanStepDao.updateAlarmId.
            val localStepId = bakePlanStepDao.upsert(
                BakePlanStepEntity(
                    planId = planPublicId,
                    stepIndex = step.index,
                    label = step.label,
                    plannedAtMillis = plannedAtMillis,
                    alarmId = 0,
                ),
            )
            val alarmId = BAKING_STEP_ALARM_ID_BASE + (localStepId.toInt() and 0xFFFF)
            bakePlanStepDao.updateAlarmId(localStepId, alarmId)
            BakingStepAlarmScheduler.scheduleStepAlarm(context, alarmId, plannedAtMillis, planPublicId, step.label)
        }

        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Marks (or un-marks) a single step done — cancels/re-arms its alarm
     * accordingly, and locally completes/reopens the parent plan as a side
     * effect, mirroring the backend's own auto-complete
     * (`data.SetBakePlanStepDone` in urs-backend) so the UI reflects it
     * immediately even offline.
     */
    suspend fun markStepDone(stepLocalId: Long, done: Boolean = true) {
        val step = bakePlanStepDao.getById(stepLocalId) ?: return
        bakePlanStepDao.updateDone(stepLocalId, if (done) System.currentTimeMillis() else null, SyncStatus.PENDING)
        if (done) {
            BakingStepAlarmScheduler.cancel(context, step.alarmId)
        } else {
            BakingStepAlarmScheduler.scheduleStepAlarm(
                context, step.alarmId, step.snoozedAtMillis ?: step.plannedAtMillis, step.planId, step.label,
            )
        }

        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_UPDATE_BAKE_PLAN_STEP,
                payloadJson = json.encodeToString(OutboxBakePlanStepUpdatePayload(localStepId = stepLocalId, done = done)),
                createdAt = System.currentTimeMillis(),
            ),
        )

        if (done) maybeCompletePlanLocally(step.planId) else reopenPlanLocally(step.planId)
        applicationScope.launch { syncManager.syncNow() }
    }

    /** Independent per step, no cascade to any other step's own time — the design's explicit choice. */
    suspend fun snoozeStep(stepLocalId: Long, newTimeMillis: Long) {
        val step = bakePlanStepDao.getById(stepLocalId) ?: return
        bakePlanStepDao.updateSnoozed(stepLocalId, newTimeMillis, SyncStatus.PENDING)
        BakingStepAlarmScheduler.scheduleStepAlarm(context, step.alarmId, newTimeMillis, step.planId, step.label)

        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_UPDATE_BAKE_PLAN_STEP,
                payloadJson = json.encodeToString(
                    OutboxBakePlanStepUpdatePayload(localStepId = stepLocalId, snoozedAtMillis = newTimeMillis),
                ),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    /** Aborts an active plan — cancels every not-yet-done step's alarm, never archived as history the way a completed plan is. */
    suspend fun cancelPlan(planLocalId: Long) {
        val plan = bakePlanDao.getById(planLocalId) ?: return
        bakePlanStepDao.getByPlanId(plan.publicId)
            .filter { it.doneAtMillis == null }
            .forEach { BakingStepAlarmScheduler.cancel(context, it.alarmId) }

        bakePlanDao.updateStatus(planLocalId, STATUS_CANCELLED, null)

        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CANCEL_BAKE_PLAN,
                payloadJson = json.encodeToString(OutboxBakePlanCancelPayload(localPlanId = planLocalId)),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Re-arms every not-yet-done step alarm of every active plan — called
     * after a reboot (exact alarms don't survive it) from
     * [ch.mcfx.urs.notifications.BootCompletedReceiver]. A trigger time
     * already in the past (the device was off past a step's planned time)
     * still gets handed to AlarmManager rather than special-cased:
     * setExactAndAllowWhileIdle fires an already-past trigger almost
     * immediately, so the step's reminder still shows up, just late.
     */
    suspend fun rearmPendingStepAlarms() {
        bakePlanDao.getActive().forEach { plan ->
            bakePlanStepDao.getByPlanId(plan.publicId)
                .filter { it.doneAtMillis == null }
                .forEach { step ->
                    val triggerAtMillis = step.snoozedAtMillis ?: step.plannedAtMillis
                    BakingStepAlarmScheduler.scheduleStepAlarm(context, step.alarmId, triggerAtMillis, step.planId, step.label)
                }
        }
    }

    private suspend fun resolveLocalPlanId(planId: String): Long? =
        localBakePlanId(planId) ?: bakePlanDao.findLocalIdByServerId(planId)

    private suspend fun maybeCompletePlanLocally(planId: String) {
        val steps = bakePlanStepDao.getByPlanId(planId)
        if (steps.isNotEmpty() && steps.all { it.doneAtMillis != null }) {
            resolveLocalPlanId(planId)?.let { bakePlanDao.updateStatus(it, STATUS_COMPLETED, System.currentTimeMillis()) }
        }
    }

    private suspend fun reopenPlanLocally(planId: String) {
        val localId = resolveLocalPlanId(planId) ?: return
        val plan = bakePlanDao.getById(localId) ?: return
        if (plan.status == STATUS_COMPLETED) {
            bakePlanDao.updateStatus(localId, STATUS_ACTIVE, null)
        }
    }

    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_CANCELLED = "cancelled"

        // Reserved id range, same convention as
        // ProductsViewModel.INVENTORY_REMINDER_ID_BASE (100_000) and
        // LocationCaptureScheduler's fixed 9010 — guarantees no collision
        // with either.
        private const val BAKING_STEP_ALARM_ID_BASE = 200_000
    }
}
