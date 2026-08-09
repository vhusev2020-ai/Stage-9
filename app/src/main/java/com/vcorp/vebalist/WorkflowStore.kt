package com.vcorp.vebalist

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class WorkflowStage {
    IDLE, IMPORTING, DETAILS_REQUIRED, READY_FOR_RESEARCH, WAITING_FOR_CHATGPT,
    REVIEW_REQUIRED, READY_TO_PUBLISH, PUBLISHING, COMPLETE, ERROR
}

data class WorkflowSnapshot(
    val version: String = WorkflowStore.CURRENT_WORKFLOW_VERSION,
    val batchId: String = "",
    val stage: WorkflowStage = WorkflowStage.IDLE,
    val updatedAt: Long = System.currentTimeMillis(),
    val message: String = ""
)

data class WorkflowImprovement(
    val createdAt: Long,
    val stage: String,
    val issue: String,
    val resolution: String = "",
    val approvedForFuture: Boolean = false
)

object WorkflowStore {
    const val CURRENT_WORKFLOW_VERSION = "1.0"
    private const val PREFS = "vebalist_workflow"
    private const val SNAPSHOT = "snapshot"
    private const val IMPROVEMENTS = "improvements"

    fun load(context: Context): WorkflowSnapshot {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(SNAPSHOT, null) ?: return WorkflowSnapshot()
        return runCatching {
            val j = JSONObject(raw)
            WorkflowSnapshot(
                version = j.optString("version", CURRENT_WORKFLOW_VERSION),
                batchId = j.optString("batch_id"),
                stage = runCatching { WorkflowStage.valueOf(j.optString("stage")) }
                    .getOrDefault(WorkflowStage.IDLE),
                updatedAt = j.optLong("updated_at", System.currentTimeMillis()),
                message = j.optString("message")
            )
        }.getOrDefault(WorkflowSnapshot())
    }

    fun save(context: Context, snapshot: WorkflowSnapshot) {
        val json = JSONObject()
            .put("version", snapshot.version)
            .put("batch_id", snapshot.batchId)
            .put("stage", snapshot.stage.name)
            .put("updated_at", snapshot.updatedAt)
            .put("message", snapshot.message)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(SNAPSHOT, json.toString()).apply()
    }

    fun transition(context: Context, stage: WorkflowStage, batchId: String, message: String = "") =
        save(context, WorkflowSnapshot(batchId = batchId, stage = stage, message = message))

    fun recordIssue(context: Context, stage: WorkflowStage, issue: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val items = runCatching { JSONArray(prefs.getString(IMPROVEMENTS, "[]")) }.getOrDefault(JSONArray())
        items.put(JSONObject()
            .put("created_at", System.currentTimeMillis())
            .put("stage", stage.name)
            .put("issue", issue.take(1500))
            .put("resolution", "")
            .put("approved_for_future", false))
        prefs.edit().putString(IMPROVEMENTS, items.toString()).apply()
    }

    fun improvements(context: Context): List<WorkflowImprovement> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(IMPROVEMENTS, "[]") ?: "[]"
        val a = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until a.length()).map { index ->
            val j = a.getJSONObject(index)
            WorkflowImprovement(
                createdAt = j.optLong("created_at"),
                stage = j.optString("stage"),
                issue = j.optString("issue"),
                resolution = j.optString("resolution"),
                approvedForFuture = j.optBoolean("approved_for_future")
            )
        }
    }
}
