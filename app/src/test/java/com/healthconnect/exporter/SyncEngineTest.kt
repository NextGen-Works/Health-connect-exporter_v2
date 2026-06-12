package com.healthconnect.exporter

import com.healthconnect.exporter.core.AppException
import com.healthconnect.exporter.core.Result
import com.healthconnect.exporter.storage.AppDatabase
import com.healthconnect.exporter.storage.NormalizedHealthRecord
import com.healthconnect.exporter.storage.STATUS_ACKNOWLEDGED
import com.healthconnect.exporter.storage.STATUS_FAILED
import com.healthconnect.exporter.storage.STATUS_IN_FLIGHT
import com.healthconnect.exporter.storage.STATUS_PENDING
import com.healthconnect.exporter.storage.SyncCursor
import com.healthconnect.exporter.sync.SyncRepository
import com.healthconnect.exporter.sync.SyncRepositoryImpl
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncEngineTest {

    private lateinit var database: AppDatabase
    private lateinit var syncRepository: SyncRepository
    private val normalizedDao = mockk<com.healthconnect.exporter.storage.NormalizedHealthRecordDao>()
    private val rawDao = mockk<com.healthconnect.exporter.storage.RawHealthRecordDao>()
    private val cursorDao = mockk<com.healthconnect.exporter.storage.SyncCursorDao>()
    private val eventLogDao = mockk<com.healthconnect.exporter.storage.AppEventLogDao>()

    @Before
    fun setup() {
        database = mockk {
            every { normalizedHealthRecordDao() } returns normalizedDao
            every { rawHealthRecordDao() } returns rawDao
            every { syncCursorDao() } returns cursorDao
            every { eventLogDao() } returns eventLogDao
        }
    }

    @Test
    fun `revoked permissions abort execution without breaking database state`() = runTest {
        coEvery { cursorDao.getAll() } returns emptyList()
        coEvery { cursorDao.updateLastAttempt(any(), any()) } just Runs

        val result = Result.failure<AppException>(AppException.PermissionDeniedException())

        assertTrue(result.isFailure)
        when (val error = result.exceptionOrNull()) {
            is AppException.PermissionDeniedException -> {
                assertEquals("Health Connect permissions denied", error.message)
            }
            else -> throw AssertionError("Expected PermissionDeniedException")
        }

        coVerify(exactly = 0) { normalizedDao.insertAll(any()) }
        coVerify(exactly = 0) { cursorDao.advanceCursor(any(), any(), any()) }
    }

    @Test
    fun `cursors are preserved on server errors and advance only on full acknowledgments`() = runTest {
        val existingCursor = SyncCursor(
            id = 1,
            recordType = "StepsRecord",
            highWatermarkMs = 1000L,
            rollingLookbackMs = 86400000L
        )
        coEvery { cursorDao.getAll() } returns listOf(existingCursor)
        coEvery { cursorDao.updateLastAttempt("StepsRecord", any()) } just Runs
        coEvery { cursorDao.advanceCursor(any(), any(), any()) } just Runs
        coEvery { normalizedDao.insertAll(any()) } returns listOf(1L)
        coEvery { rawDao.insertAll(any()) } returns listOf(1L)
        coEvery { normalizedDao.getByStatuses(any(), any()) } returns emptyList()

        val cursorAfterError = existingCursor.copy(highWatermarkMs = 1000L)
        coEvery { cursorDao.getByType("StepsRecord") } returns cursorAfterError

        assertEquals(1000L, existingCursor.highWatermarkMs)

        coVerify(exactly = 0) { cursorDao.advanceCursor(any(), any(), any()) }
    }

    @Test
    fun `duplicate uploads are blocked using deterministic keys`() = runTest {
        val key = NormalizedHealthRecord.buildDeduplicationKey(
            "StepsRecord",
            "hc-123",
            1700000000000L
        )

        val existingRecord = NormalizedHealthRecord(
            id = 1,
            recordType = "StepsRecord",
            healthConnectId = "hc-123",
            deduplicationKey = key,
            payload = """{"count": 100}""",
            capturedAt = 1700000000000L,
            status = STATUS_ACKNOWLEDGED
        )

        coEvery { normalizedDao.findByDeduplicationKey(key) } returns existingRecord
        coEvery { normalizedDao.insert(any()) } returns 0L

        val duplicate = NormalizedHealthRecord(
            id = 2,
            recordType = "StepsRecord",
            healthConnectId = "hc-123",
            deduplicationKey = key,
            payload = """{"count": 100}""",
            capturedAt = 1700000000000L,
            status = STATUS_PENDING
        )

        val insertResult = normalizedDao.insert(duplicate)
        assertEquals(0L, insertResult)

        val existing = normalizedDao.findByDeduplicationKey(key)
        assertTrue(existing != null)
        assertEquals(STATUS_ACKNOWLEDGED, existing?.status)
    }

    @Test
    fun `failed batch reverts status to PENDING for retry`() = runTest {
        val records = listOf(
            NormalizedHealthRecord(
                id = 1,
                recordType = "StepsRecord",
                healthConnectId = "hc-1",
                deduplicationKey = "StepsRecord_hc-1_1700000000000",
                payload = """{"count": 50}""",
                capturedAt = 1700000000000L,
                status = STATUS_IN_FLIGHT
            ),
            NormalizedHealthRecord(
                id = 2,
                recordType = "StepsRecord",
                healthConnectId = "hc-2",
                deduplicationKey = "StepsRecord_hc-2_1700000001000",
                payload = """{"count": 75}""",
                capturedAt = 1700000001000L,
                status = STATUS_IN_FLIGHT
            )
        )

        coEvery { normalizedDao.updateStatusBatch(any(), STATUS_PENDING, any()) } just Runs
        coEvery { normalizedDao.recordAttempt(any(), any(), any(), any()) } just Runs

        normalizedDao.updateStatusBatch(
            records.map { it.id },
            STATUS_PENDING,
            "Connection timeout"
        )

        coVerify(exactly = 1) {
            normalizedDao.updateStatusBatch(
                listOf(1L, 2L),
                STATUS_PENDING,
                "Connection timeout"
            )
        }
    }

    @Test
    fun `partial acknowledgements update individual record statuses correctly`() = runTest {
        val records = listOf(
            NormalizedHealthRecord(
                id = 1, recordType = "StepsRecord", healthConnectId = "hc-1",
                deduplicationKey = "StepsRecord_hc-1_1700000000000",
                payload = "{}", capturedAt = 1700000000000L, status = STATUS_IN_FLIGHT
            ),
            NormalizedHealthRecord(
                id = 2, recordType = "StepsRecord", healthConnectId = "hc-2",
                deduplicationKey = "StepsRecord_hc-2_1700000001000",
                payload = "{}", capturedAt = 1700000001000L, status = STATUS_IN_FLIGHT
            ),
            NormalizedHealthRecord(
                id = 3, recordType = "StepsRecord", healthConnectId = "hc-3",
                deduplicationKey = "StepsRecord_hc-3_1700000002000",
                payload = "{}", capturedAt = 1700000002000L, status = STATUS_IN_FLIGHT
            )
        )

        val ackKeys = setOf("StepsRecord_hc-1_1700000000000", "StepsRecord_hc-3_1700000002000")
        val rejKeys = setOf("StepsRecord_hc-2_1700000001000")

        coEvery { normalizedDao.updateStatus(any(), STATUS_ACKNOWLEDGED, any()) } just Runs
        coEvery { normalizedDao.recordAttempt(any(), STATUS_FAILED, any(), any()) } just Runs

        for (record in records) {
            when {
                record.deduplicationKey in ackKeys -> {
                    normalizedDao.updateStatus(record.id, STATUS_ACKNOWLEDGED)
                }
                record.deduplicationKey in rejKeys -> {
                    normalizedDao.recordAttempt(record.id, STATUS_FAILED, System.currentTimeMillis(), "Rejected")
                }
            }
        }

        coVerify(exactly = 1) { normalizedDao.updateStatus(1, STATUS_ACKNOWLEDGED, any()) }
        coVerify(exactly = 1) { normalizedDao.updateStatus(3, STATUS_ACKNOWLEDGED, any()) }
        coVerify(exactly = 1) { normalizedDao.recordAttempt(2, STATUS_FAILED, any(), any()) }
    }

    @Test
    fun `deduplication key format is deterministic and correct`() {
        val key1 = NormalizedHealthRecord.buildDeduplicationKey("StepsRecord", "hc-123", 1700000000000L)
        val key2 = NormalizedHealthRecord.buildDeduplicationKey("StepsRecord", "hc-123", 1700000000000L)
        assertEquals(key1, key2)
        assertEquals("StepsRecord_hc-123_1700000000000", key1)
    }

    @Test
    fun `different inputs produce different deduplication keys`() {
        val key1 = NormalizedHealthRecord.buildDeduplicationKey("StepsRecord", "hc-1", 1700000000000L)
        val key2 = NormalizedHealthRecord.buildDeduplicationKey("HeartRateRecord", "hc-1", 1700000000000L)
        val key3 = NormalizedHealthRecord.buildDeduplicationKey("StepsRecord", "hc-2", 1700000000000L)
        val key4 = NormalizedHealthRecord.buildDeduplicationKey("StepsRecord", "hc-1", 1700000001000L)

        assertFalse(key1 == key2)
        assertFalse(key1 == key3)
        assertFalse(key1 == key4)
    }

    @Test
    fun `repair sync reprocesses records in the specified lookback range`() = runTest {
        val sinceMs = System.currentTimeMillis() - (7 * 24L * 60 * 60 * 1000)
        val now = System.currentTimeMillis()

        val recordsInRange = listOf(
            NormalizedHealthRecord(
                id = 1, recordType = "StepsRecord", healthConnectId = "hc-1",
                deduplicationKey = "StepsRecord_hc-1_1700000000000",
                payload = "{}", capturedAt = 1700000000000L, status = STATUS_FAILED
            ),
            NormalizedHealthRecord(
                id = 2, recordType = "StepsRecord", healthConnectId = "hc-2",
                deduplicationKey = "StepsRecord_hc-2_1700000001000",
                payload = "{}", capturedAt = 1700000001000L, status = STATUS_ACKNOWLEDGED
            )
        )

        coEvery { normalizedDao.getInRange("StepsRecord", sinceMs, any()) } returns recordsInRange
        coEvery { normalizedDao.updateStatusBatch(any(), STATUS_IN_FLIGHT, null) } just Runs

        val pendingRecords = recordsInRange.filter { it.status != STATUS_ACKNOWLEDGED }
        assertEquals(1, pendingRecords.size)
        assertEquals(STATUS_FAILED, pendingRecords[0].status)
    }
}
