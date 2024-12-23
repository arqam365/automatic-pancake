package com.nextlevelprogrammers.surakshakawach.api

import kotlinx.serialization.Serializable

@Serializable
data class ClipData(
    val url: String,
    val timestamp: Long,
    val gsBucketUrl: String
)

@Serializable
data class AddAudioRequest(
    val ticketId: String,
    val firebaseUID: String,
    val clips_data: List<ClipData>
)
