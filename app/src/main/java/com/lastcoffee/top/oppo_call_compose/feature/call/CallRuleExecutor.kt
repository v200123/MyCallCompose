package com.lastcoffee.top.oppo_call_compose.feature.call

import com.lastcoffee.top.oppo_call_compose.model.CallFlowConfiguration
import com.lastcoffee.top.oppo_call_compose.model.CallStage
import com.lastcoffee.top.oppo_call_compose.model.CallStatusRule
import com.lastcoffee.top.oppo_call_compose.model.defaultCallStatuses

/** One rule entry owns its deadline and callbacks. A transition invalidates the previous entry. */
internal class CallRuleExecutor(
    configuration: CallFlowConfiguration,
    initialStatusId: String,
    startedAt: Long,
    promptStatusId: String? = null,
) {
    data class Step(val rule: CallStatusRule, val revision: Long)

    val statuses = configuration.statuses.ifEmpty { defaultCallStatuses() }
    private val rules = statuses.associateBy { it.id }
    private val promptRule = promptStatusId?.let(rules::get)?.takeIf { it.stage == CallStage.OUTCOME }
    val timing = CallSessionTiming(startedAt)
    var step = Step(rules[initialStatusId] ?: rules[configuration.initialStatusId] ?: rules.values.first(), 0)
        private set
    private var completionClaimed = false

    init { timing.enterStage(step.rule.stage, startedAt) }

    fun isCurrent(revision: Long): Boolean = !completionClaimed && step.revision == revision

    fun moveTo(id: String, now: Long, expectedRevision: Long = step.revision): Boolean {
        if (!isCurrent(expectedRevision)) return false
        return enter(rules[id] ?: return false, now)
    }

    private fun enter(rule: CallStatusRule, now: Long): Boolean {
        // Ending a call cannot be undone by an old button/media callback.
        if (step.rule.stage == CallStage.DISCONNECTED ||
            (step.rule.stage == CallStage.DISCONNECTING && rule.stage != CallStage.DISCONNECTED)) return false
        timing.enterStage(rule.stage, now)
        step = Step(rule, step.revision + 1)
        return true
    }

    fun hangup(now: Long, expectedRevision: Long = step.revision): Boolean {
        if (!isCurrent(expectedRevision) || step.rule.stage in setOf(CallStage.DISCONNECTING, CallStage.DISCONNECTED)) return false
        val rule = rules.values.firstOrNull { it.stage == CallStage.DISCONNECTING }
            ?: CallStatusRule("__ending", "正在挂断", CallStage.DISCONNECTING, durationSeconds = 1)
        return enter(rule, now)
    }

    private fun nextRule(): CallStatusRule? {
        if (step.rule.stage == CallStage.WAITING && promptRule != null) return promptRule
        val configured = step.rule.timeoutTargetId?.let(rules::get)
        return if (step.rule.stage == CallStage.DISCONNECTING) {
                configured?.takeIf { it.stage == CallStage.DISCONNECTED }
                    ?: rules.values.firstOrNull { it.stage == CallStage.DISCONNECTED }
                    ?: CallStatusRule("__ended", "已挂断", CallStage.DISCONNECTED, durationSeconds = 1)
            } else configured
    }

    /** A zero duration means no timer, except terminal cleanup which always completes. */
    fun remainingMillis(now: Long): Long? {
        val rule = step.rule
        val terminal = rule.stage in setOf(CallStage.DISCONNECTING, CallStage.DISCONNECTED)
        if (!terminal && (rule.durationSeconds <= 0 || nextRule() == null)) return null
        return timing.remainingStatusMillis(rule.durationSeconds, now)
    }

    fun advanceIfDue(revision: Long, now: Long): Boolean {
        if (!isCurrent(revision) || step.rule.stage == CallStage.DISCONNECTED) return false
        if (remainingMillis(now) != 0L) return false
        return enter(nextRule() ?: return false, now)
    }

    /** Audio may advance an untimed step, but never override an explicit timed rule. */
    fun audioFinished(revision: Long, now: Long): Boolean {
        if (!isCurrent(revision) || step.rule.durationSeconds > 0) return false
        if (step.rule.stage == CallStage.ACTIVE || step.rule.stage == CallStage.HOLD) return false
        val target = nextRule() ?: return false
        return enter(target, now)
    }

    fun claimCompletion(revision: Long, now: Long): Boolean {
        if (!isCurrent(revision) || step.rule.stage != CallStage.DISCONNECTED || remainingMillis(now) != 0L) return false
        completionClaimed = true
        return true
    }
}
