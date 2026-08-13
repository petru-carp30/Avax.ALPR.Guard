package com.avax.alpr.guard.data.repository

import android.database.sqlite.SQLiteException
import com.avax.alpr.guard.data.local.SyncMetadataEntity
import com.avax.alpr.guard.data.local.VehicleCacheStore
import com.avax.alpr.guard.data.network.NetworkStatusProvider
import com.avax.alpr.guard.data.network.VehicleSyncApi
import com.google.gson.JsonParseException
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CancellationException

class VehicleSyncRepository(
    private val api: VehicleSyncApi,
    private val networkStatusProvider: NetworkStatusProvider,
    private val snapshotValidator: SnapshotValidator,
    private val vehicleCacheStore: VehicleCacheStore
) {

    suspend fun synchronize(): SyncResult {
        if (!networkStatusProvider.isNetworkAvailable()) return SyncResult.NoNetwork

        val response = try {
            api.getVehicleSnapshot()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: JsonParseException) {
            return SyncResult.MalformedSnapshot
        } catch (_: IOException) {
            return SyncResult.BackendUnavailable
        } catch (_: RuntimeException) {
            return SyncResult.MalformedSnapshot
        }

        if (response.code() == 409) return SyncResult.Conflict
        if (response.code() == 503) return SyncResult.BackendUnavailable
        if (!response.isSuccessful) return SyncResult.HttpError(response.code())

        val body = response.body() ?: return SyncResult.MalformedSnapshot

        val validated = when (val result = snapshotValidator.validate(body)) {
            is SnapshotValidator.Result.Valid -> result
            is SnapshotValidator.Result.UnsupportedVersion -> return SyncResult.UnsupportedContractVersion(result.receivedVersion)
            SnapshotValidator.Result.Malformed -> return SyncResult.MalformedSnapshot
        }

        val importedAtUtc = Instant.now().toString()

        val metadata = SyncMetadataEntity(
            contractVersion = validated.contractVersion,
            snapshotGeneratedAtUtc = validated.snapshotGeneratedAtUtc,
            importedAtUtc = importedAtUtc,
            vehicleCount = validated.vehicles.size
        )

        return try {
            vehicleCacheStore.replaceSnapshot(validated.vehicles, metadata)

            SyncResult.Success(
                vehicleCount = validated.vehicles.size,
                snapshotGeneratedAtUtc = validated.snapshotGeneratedAtUtc,
                importedAtUtc = importedAtUtc
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: SQLiteException) {
            SyncResult.DatabaseFailure
        } catch (_: RuntimeException) {
            SyncResult.DatabaseFailure
        }
    }
}