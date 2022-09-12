package com.duncpro.festivalgarden.queue

import kotlinx.serialization.Serializable

@Serializable
sealed class QueueMessage()

@Serializable
data class InitializeLibraryProcessor(
    val userId: String
): QueueMessage()

@Serializable
data class ProcessLibraryPage(
    val userId: String,
    val pageId: String
): QueueMessage()

@Serializable
data class ProcessPlaylistPage(
    val userId: String,
    val playlistUrl: String,
    val pageNo: Int
): QueueMessage()
