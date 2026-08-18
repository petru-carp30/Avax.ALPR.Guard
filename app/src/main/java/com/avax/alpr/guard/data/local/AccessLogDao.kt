package com.avax.alpr.guard.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AccessLogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(accessLog: AccessLogEntity)

    @Query(
        """
        SELECT *
        FROM access_logs
        ORDER BY eventTimestampUtc DESC
        LIMIT :limit
        """
    )
    fun observeRecent(limit: Int): Flow<List<AccessLogEntity>>

    @Query(
        """
        SELECT *
        FROM access_logs
        ORDER BY eventTimestampUtc DESC
        LIMIT :limit
        """
    )
    suspend fun getRecent(limit: Int): List<AccessLogEntity>

    @Query(
        """
        SELECT *
        FROM access_logs
        WHERE syncState = :syncState
        ORDER BY eventTimestampUtc ASC
        """
    )
    suspend fun getBySyncState(
        syncState: AccessLogSyncState
    ): List<AccessLogEntity>

    @Query(
        """
        UPDATE access_logs
        SET syncState = :syncState
        WHERE localLogId = :localLogId
        """
    )
    suspend fun updateSyncState(
        localLogId: String,
        syncState: AccessLogSyncState
    ): Int

    @Query(
        """
        SELECT *
        FROM access_logs
        WHERE localLogId = :localLogId
        LIMIT 1
        """
    )
    suspend fun findByLocalLogId(
        localLogId: String
    ): AccessLogEntity?

    @Query("SELECT COUNT(*) FROM access_logs")
    suspend fun count(): Int
}