package com.myAllVideoBrowser.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myAllVideoBrowser.data.local.room.entity.RemoteVideoInfo
import io.reactivex.rxjava3.core.Flowable

@Dao
interface RemoteVideoDao {

    @Query("SELECT * FROM RemoteVideoInfo ORDER BY createdAt DESC")
    fun observeAll(): Flowable<List<RemoteVideoInfo>>

    @Query("SELECT * FROM RemoteVideoInfo ORDER BY createdAt DESC")
    fun listAll(): List<RemoteVideoInfo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(item: RemoteVideoInfo)

    @Delete
    fun delete(item: RemoteVideoInfo)

    @Query("DELETE FROM RemoteVideoInfo WHERE id = :id")
    fun deleteById(id: String)
}
