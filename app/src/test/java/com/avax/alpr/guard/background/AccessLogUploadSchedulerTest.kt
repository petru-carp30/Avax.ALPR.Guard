package com.avax.alpr.guard.background

import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessLogUploadSchedulerTest {

    @Test
    fun `upload uses one unique work queue`() {
        assertEquals("access-log-upload", AccessLogUploadScheduler.UNIQUE_WORK_NAME)
    }

    @Test
    fun `new local events replace redundant pending worker`() {
        assertEquals(ExistingWorkPolicy.REPLACE, AccessLogUploadScheduler.NEW_LOG_POLICY)
    }

    @Test
    fun `application recovery keeps persistent existing worker`() {
        assertEquals(ExistingWorkPolicy.KEEP, AccessLogUploadScheduler.RECOVERY_POLICY)
    }

    @Test
    fun `worker configuration requires network and exponential backoff`() {
        assertEquals(NetworkType.CONNECTED, AccessLogUploadScheduler.REQUIRED_NETWORK_TYPE)
        assertEquals(BackoffPolicy.EXPONENTIAL, AccessLogUploadScheduler.RETRY_BACKOFF_POLICY)
        assertEquals(30L, AccessLogUploadScheduler.BACKOFF_DELAY_SECONDS)
    }
}