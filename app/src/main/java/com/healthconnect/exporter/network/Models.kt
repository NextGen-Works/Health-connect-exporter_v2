package com.healthconnect.exporter.network

import com.google.gson.annotations.SerializedName

data class BatchIngestRequest(
    @SerializedName("records")
    val records: List<IngestRecord>,
    @SerializedName("deviceId")
    val deviceId: String,
    @SerializedName("batchId")
    val batchId: String
)

data class IngestRecord(
    @SerializedName("id")
    val id: String,
    @SerializedName("type")
    val type: String,
    @SerializedName("payload")
    val payload: Map<String, Any>,
    @SerializedName("capturedAt")
    val capturedAt: Long,
    @SerializedName("deduplicationKey")
    val deduplicationKey: String
)

data class BatchIngestResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("batchId")
    val batchId: String,
    @SerializedName("acknowledged")
    val acknowledged: List<AcknowledgedRecord>,
    @SerializedName("rejected")
    val rejected: List<RejectedRecord>,
    @SerializedName("serverTimestamp")
    val serverTimestamp: Long
)

data class AcknowledgedRecord(
    @SerializedName("deduplicationKey")
    val deduplicationKey: String,
    @SerializedName("serverRecordId")
    val serverRecordId: String
)

data class RejectedRecord(
    @SerializedName("deduplicationKey")
    val deduplicationKey: String,
    @SerializedName("reason")
    val reason: String,
    @SerializedName("errorCode")
    val errorCode: String
)

data class HealthResponse(
    @SerializedName("status")
    val status: String,
    @SerializedName("version")
    val version: String,
    @SerializedName("uptime")
    val uptime: Long
)
