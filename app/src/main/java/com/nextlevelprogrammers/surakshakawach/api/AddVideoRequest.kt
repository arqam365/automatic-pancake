package com.nextlevelprogrammers.surakshakawach.api

import kotlinx.serialization.Serializable

@Serializable
data class AddVideoRequest(
    val ticketId: String,
    val firebaseUID: String,
    val video_clips_data: List<VideoClipData>
)

@Serializable
data class VideoClipData(
    val url: String,
    val timestamp: Long,
    val gsBucketUrl: String
)