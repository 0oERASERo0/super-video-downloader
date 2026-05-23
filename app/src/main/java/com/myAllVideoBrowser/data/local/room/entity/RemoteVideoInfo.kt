package com.myAllVideoBrowser.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "RemoteVideoInfo")
data class RemoteVideoInfo(
    @PrimaryKey
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var remoteUri: String = "",
    var destinationType: String = "",
    var destinationLabel: String = "",
    var sizeBytes: Long = 0L,
    var createdAt: Long = System.currentTimeMillis(),
)
