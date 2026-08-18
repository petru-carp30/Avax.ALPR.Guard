package com.avax.alpr.guard.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.avax.alpr.guard.domain.model.AccessArea
import com.avax.alpr.guard.domain.model.AccessDecision
import com.avax.alpr.guard.domain.model.AccessDecisionStatus
import com.avax.alpr.guard.domain.model.ValidityWindow
import com.avax.alpr.guard.domain.model.VehicleRecord
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class AccessLogStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext

        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @Test
    fun accessLogSurvivesDatabaseReopen() = runBlocking {
        var database = createDatabase()

        val store = AccessLogStore(
            accessLogDao = database.accessLogDao(),
            clock = fixedClock(),
            idProvider = { "log-001" }
        )

        val result = store.recordIfRequired(
            inputPlate = "TEST 123",
            decision = grantedDecision()
        )

        assertEquals(
            AccessLogPersistenceStatus.Persisted,
            result
        )

        database.close()

        database = createDatabase()

        try {
            val accessLog =
                database.accessLogDao()
                    .findByLocalLogId("log-001")

            assertNotNull(accessLog)

            assertEquals(
                "2026-08-18T10:00:00Z",
                accessLog?.eventTimestampUtc
            )

            assertEquals(
                "TEST123",
                accessLog?.normalizedLicensePlate
            )

            assertEquals(
                AccessLogSyncState.Pending,
                accessLog?.syncState
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun markingOneLogSynchronizedDoesNotAffectOtherLogs() =
        runBlocking {

            val database = createDatabase()

            try {
                val ids = mutableListOf(
                    "log-001",
                    "log-002"
                )

                val store = AccessLogStore(
                    accessLogDao = database.accessLogDao(),
                    clock = fixedClock(),
                    idProvider = {
                        ids.removeAt(0)
                    }
                )

                store.recordIfRequired(
                    inputPlate = "TEST 123",
                    decision = grantedDecision()
                )

                store.recordIfRequired(
                    inputPlate = "TEST 456",
                    decision = grantedDecision(
                        normalizedPlate = "TEST456"
                    )
                )

                assertEquals(
                    2,
                    store.getPending().size
                )

                val updated =
                    store.markSynchronized("log-001")

                assertEquals(true, updated)

                val pending = store.getPending()

                assertEquals(1, pending.size)
                assertEquals(
                    "log-002",
                    pending.single().localLogId
                )

                assertEquals(
                    AccessLogSyncState.Synced,
                    database.accessLogDao()
                        .findByLocalLogId("log-001")
                        ?.syncState
                )

                assertEquals(
                    AccessLogSyncState.Pending,
                    database.accessLogDao()
                        .findByLocalLogId("log-002")
                        ?.syncState
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun snapshotReplacementDoesNotDeleteAccessLogs() =
        runBlocking {

            val database = createDatabase()

            try {
                val cacheStore =
                    VehicleCacheStore(database)

                cacheStore.replaceSnapshot(
                    vehicles = listOf(
                        vehicleEntity(
                            normalizedPlate = "OLD123",
                            sourceVehicleId = 1
                        )
                    ),
                    metadata = metadata(
                        snapshotGeneratedAtUtc =
                            "2026-08-18T09:00:00Z",
                        vehicleCount = 1
                    )
                )

                val accessLogStore = AccessLogStore(
                    accessLogDao = database.accessLogDao(),
                    clock = fixedClock(),
                    idProvider = { "log-001" }
                )

                accessLogStore.recordIfRequired(
                    inputPlate = "OLD 123",
                    decision = grantedDecision(
                        normalizedPlate = "OLD123",
                        sourceVehicleId = 1
                    )
                )

                assertEquals(
                    1,
                    database.vehicleDao().count()
                )

                assertEquals(
                    1,
                    database.accessLogDao().count()
                )

                cacheStore.replaceSnapshot(
                    vehicles = listOf(
                        vehicleEntity(
                            normalizedPlate = "NEW123",
                            sourceVehicleId = 2
                        )
                    ),
                    metadata = metadata(
                        snapshotGeneratedAtUtc =
                            "2026-08-18T11:00:00Z",
                        vehicleCount = 1
                    )
                )

                assertEquals(
                    1,
                    database.accessLogDao().count()
                )

                assertNotNull(
                    database.accessLogDao()
                        .findByLocalLogId("log-001")
                )

                assertNotNull(
                    database.vehicleDao()
                        .findByNormalizedPlate("NEW123")
                )

                val syncMetadata =
                    database.syncMetadataDao().get()

                assertNotNull(syncMetadata)

                assertEquals(
                    "2026-08-18T11:00:00Z",
                    syncMetadata?.snapshotGeneratedAtUtc
                )
            } finally {
                database.close()
            }
        }

    private fun createDatabase(): GuardDatabase {
        return Room.databaseBuilder(
            context,
            GuardDatabase::class.java,
            TEST_DATABASE_NAME
        )
            .addMigrations(
                GuardDatabaseMigrations.MIGRATION_1_2
            )
            .build()
    }

    private fun fixedClock(): Clock {
        return Clock.fixed(
            Instant.parse("2026-08-18T10:00:00Z"),
            ZoneOffset.UTC
        )
    }

    private fun grantedDecision(
        normalizedPlate: String = "TEST123",
        sourceVehicleId: Int = 1
    ): AccessDecision {
        return AccessDecision(
            status = AccessDecisionStatus.Granted,
            requestedArea = AccessArea.Site,
            normalizedLicensePlate = normalizedPlate,
            vehicle = vehicleRecord(
                normalizedPlate = normalizedPlate,
                sourceVehicleId = sourceVehicleId
            )
        )
    }

    private fun vehicleRecord(
        normalizedPlate: String,
        sourceVehicleId: Int
    ) = VehicleRecord(
        sourceVehicleId = sourceVehicleId,
        normalizedLicensePlate = normalizedPlate,
        displayLicensePlate = normalizedPlate,
        countryId = 0,
        brand = "TestBrand",
        model = "TestModel",
        color = "Blue",
        personId = null,
        departmentId = null,
        hasParkingLotAccess = false,
        parkingLotValidity =
            ValidityWindow(null, null),
        hasSiteAccess = true,
        siteValidity =
            ValidityWindow(null, null),
        hasCampAccess = false,
        campValidity =
            ValidityWindow(null, null),
        isTemporaryPlate = false,
        isPrivate = false,
        isRentalCar = false,
        accessNotes = "Synthetic test vehicle"
    )

    private fun vehicleEntity(
        normalizedPlate: String,
        sourceVehicleId: Int
    ) = VehicleEntity(
        normalizedLicensePlate = normalizedPlate,
        sourceVehicleId = sourceVehicleId,
        displayLicensePlate = normalizedPlate,
        countryId = 0,
        brand = "TestBrand",
        model = "TestModel",
        color = "Blue",
        personId = null,
        departmentId = null,
        hasParkingLotAccess = false,
        parkingLotAccessStart = null,
        parkingLotAccessEnd = null,
        hasSiteAccess = true,
        siteAccessStart = null,
        siteAccessEnd = null,
        hasCampAccess = false,
        campAccessStart = null,
        campAccessEnd = null,
        isTemporaryPlate = false,
        isPrivate = false,
        isRentalCar = false,
        accessNotes = null
    )

    private fun metadata(
        snapshotGeneratedAtUtc: String,
        vehicleCount: Int
    ) = SyncMetadataEntity(
        contractVersion = 1,
        snapshotGeneratedAtUtc =
            snapshotGeneratedAtUtc,
        importedAtUtc =
            "2026-08-18T10:00:00Z",
        vehicleCount = vehicleCount
    )

    @Test
    fun grantedDecisionIsPersisted() = runBlocking {
        assertDecisionIsPersisted(AccessDecisionStatus.Granted)
    }

    @Test
    fun deniedDecisionIsPersisted() = runBlocking {
        assertDecisionIsPersisted(AccessDecisionStatus.Denied)
    }

    @Test
    fun expiredDecisionIsPersisted() = runBlocking {
        assertDecisionIsPersisted(AccessDecisionStatus.Expired)
    }

    @Test
    fun notYetValidDecisionIsPersisted() = runBlocking {
        assertDecisionIsPersisted(AccessDecisionStatus.NotYetValid)
    }

    @Test
    fun vehicleNotFoundDecisionIsPersisted() = runBlocking {
        val database = createDatabase()

        try {
            val store = AccessLogStore(
                accessLogDao = database.accessLogDao(),
                clock = fixedClock(),
                idProvider = { "vehicle-not-found-log" }
            )

            val decision = AccessDecision(
                status = AccessDecisionStatus.VehicleNotFound,
                requestedArea = AccessArea.Site,
                normalizedLicensePlate = "UNKNOWN123",
                vehicle = null
            )

            val result = store.recordIfRequired(
                inputPlate = "UNKNOWN 123",
                decision = decision
            )

            assertEquals(
                AccessLogPersistenceStatus.Persisted,
                result
            )

            val log = database.accessLogDao()
                .findByLocalLogId("vehicle-not-found-log")

            assertNotNull(log)
            assertEquals(
                AccessDecisionStatus.VehicleNotFound,
                log?.decisionStatus
            )
            assertEquals("UNKNOWN123", log?.normalizedLicensePlate)
            assertEquals(null, log?.sourceVehicleId)
            assertEquals(AccessLogSyncState.Pending, log?.syncState)
        } finally {
            database.close()
        }
    }

    private suspend fun assertDecisionIsPersisted(
        status: AccessDecisionStatus
    ) {
        val database = createDatabase()

        try {
            val logId = "log-${status.name}"

            val store = AccessLogStore(
                accessLogDao = database.accessLogDao(),
                clock = fixedClock(),
                idProvider = { logId }
            )

            val decision = AccessDecision(
                status = status,
                requestedArea = AccessArea.Site,
                normalizedLicensePlate = "TEST123",
                vehicle = vehicleRecord(
                    normalizedPlate = "TEST123",
                    sourceVehicleId = 1
                )
            )

            val result = store.recordIfRequired(
                inputPlate = "TEST 123",
                decision = decision
            )

            assertEquals(
                AccessLogPersistenceStatus.Persisted,
                result
            )

            val log = database.accessLogDao()
                .findByLocalLogId(logId)

            assertNotNull(log)
            assertEquals(status, log?.decisionStatus)
            assertEquals(AccessArea.Site, log?.accessArea)
            assertEquals("TEST123", log?.normalizedLicensePlate)
            assertEquals(1, log?.sourceVehicleId)
            assertEquals(AccessLogSyncState.Pending, log?.syncState)
        } finally {
            database.close()
        }
    }

    companion object {
        private const val TEST_DATABASE_NAME =
            "access_log_store_test.db"
    }
}