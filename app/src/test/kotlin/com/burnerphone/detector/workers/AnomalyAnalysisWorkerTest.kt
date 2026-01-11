package com.burnerphone.detector.workers

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.burnerphone.detector.BurnerPhoneApplication
import com.burnerphone.detector.data.AppDatabase
import com.burnerphone.detector.data.dao.AnomalyDetectionDao
import com.burnerphone.detector.data.dao.DeviceDetectionDao
import com.burnerphone.detector.data.dao.WhitelistedDeviceDao
import com.burnerphone.detector.data.models.DeviceType
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * Unit tests for AnomalyAnalysisWorker
 * Tests periodic background anomaly analysis
 */
class AnomalyAnalysisWorkerTest {

    private lateinit var context: Context
    private lateinit var worker: AnomalyAnalysisWorker
    private lateinit var mockApp: BurnerPhoneApplication
    private lateinit var mockDatabase: AppDatabase
    private lateinit var mockDeviceDetectionDao: DeviceDetectionDao
    private lateinit var mockAnomalyDetectionDao: AnomalyDetectionDao
    private lateinit var mockWhitelistedDeviceDao: WhitelistedDeviceDao

    @Before
    fun setup() {
        // Create mocks
        context = mock(Context::class.java)
        mockApp = mock(BurnerPhoneApplication::class.java)
        mockDatabase = mock(AppDatabase::class.java)
        mockDeviceDetectionDao = mock(DeviceDetectionDao::class.java)
        mockAnomalyDetectionDao = mock(AnomalyDetectionDao::class.java)
        mockWhitelistedDeviceDao = mock(WhitelistedDeviceDao::class.java)

        // Setup mock relationships
        `when`(context.applicationContext).thenReturn(mockApp)
        `when`(mockApp.database).thenReturn(mockDatabase)
        `when`(mockDatabase.deviceDetectionDao()).thenReturn(mockDeviceDetectionDao)
        `when`(mockDatabase.anomalyDetectionDao()).thenReturn(mockAnomalyDetectionDao)
        `when`(mockDatabase.whitelistedDeviceDao()).thenReturn(mockWhitelistedDeviceDao)

        // Setup default DAO responses
        `when`(mockDeviceDetectionDao.getUniqueDeviceCount(DeviceType.WIFI_NETWORK))
            .thenReturn(0)
        `when`(mockDeviceDetectionDao.getUniqueDeviceCount(DeviceType.BLUETOOTH_DEVICE))
            .thenReturn(0)
        `when`(mockDeviceDetectionDao.getUniqueDeviceAddresses(any()))
            .thenReturn(emptyList())
        `when`(mockAnomalyDetectionDao.getTotalCount()).thenReturn(0)
        `when`(mockAnomalyDetectionDao.getActiveCount()).thenReturn(0)
        `when`(mockWhitelistedDeviceDao.getAllWhitelistedList())
            .thenReturn(emptyList())
        `when`(mockWhitelistedDeviceDao.isWhitelisted(anyString()))
            .thenReturn(false)
        `when`(mockWhitelistedDeviceDao.getAllWhitelisted())
            .thenReturn(flowOf(emptyList()))

        // Create worker
        worker = TestListenableWorkerBuilder<AnomalyAnalysisWorker>(context)
            .build()
    }

    @Test
    fun `doWork returns success when analysis completes successfully`() = runTest {
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork analyzes all device types`() = runTest {
        worker.doWork()

        // Verify both WiFi and Bluetooth device types are queried
        verify(mockDeviceDetectionDao).getUniqueDeviceAddresses(DeviceType.WIFI_NETWORK)
        verify(mockDeviceDetectionDao).getUniqueDeviceAddresses(DeviceType.BLUETOOTH_DEVICE)
    }

    @Test
    fun `doWork logs device statistics`() = runTest {
        `when`(mockDeviceDetectionDao.getUniqueDeviceCount(DeviceType.WIFI_NETWORK))
            .thenReturn(5)
        `when`(mockDeviceDetectionDao.getUniqueDeviceCount(DeviceType.BLUETOOTH_DEVICE))
            .thenReturn(3)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(mockDeviceDetectionDao).getUniqueDeviceCount(DeviceType.WIFI_NETWORK)
        verify(mockDeviceDetectionDao).getUniqueDeviceCount(DeviceType.BLUETOOTH_DEVICE)
    }

    @Test
    fun `doWork tracks anomaly counts before and after analysis`() = runTest {
        `when`(mockAnomalyDetectionDao.getTotalCount())
            .thenReturn(10)
            .thenReturn(15) // After analysis
        `when`(mockAnomalyDetectionDao.getActiveCount())
            .thenReturn(5)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(mockAnomalyDetectionDao, atLeast(2)).getTotalCount()
        verify(mockAnomalyDetectionDao).getActiveCount()
    }

    @Test
    fun `doWork handles empty device list`() = runTest {
        `when`(mockDeviceDetectionDao.getUniqueDeviceAddresses(any()))
            .thenReturn(emptyList())

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork handles database access correctly`() = runTest {
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(mockApp).database
        verify(mockDatabase).deviceDetectionDao()
        verify(mockDatabase).anomalyDetectionDao()
        verify(mockDatabase).whitelistedDeviceDao()
    }

    @Test
    fun `doWork attempts ML model training if not ready`() = runTest {
        // Note: ML model training requires sufficient data which won't be present in empty DAOs
        // The worker should handle this gracefully
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        // Verify whitelisted devices were queried for ML training data
        verify(mockWhitelistedDeviceDao).getAllWhitelistedList()
    }

    @Test
    fun `worker has correct work name`() {
        assertEquals("anomaly_analysis_work", AnomalyAnalysisWorker.WORK_NAME)
    }

    @Test
    fun `worker is CoroutineWorker`() {
        assertTrue(worker is androidx.work.CoroutineWorker)
    }

    @Test
    fun `doWork queries all necessary DAOs`() = runTest {
        worker.doWork()

        verify(mockDeviceDetectionDao).getUniqueDeviceCount(DeviceType.WIFI_NETWORK)
        verify(mockDeviceDetectionDao).getUniqueDeviceCount(DeviceType.BLUETOOTH_DEVICE)
        verify(mockDeviceDetectionDao).getUniqueDeviceAddresses(DeviceType.WIFI_NETWORK)
        verify(mockDeviceDetectionDao).getUniqueDeviceAddresses(DeviceType.BLUETOOTH_DEVICE)
        verify(mockAnomalyDetectionDao, atLeast(1)).getTotalCount()
        verify(mockAnomalyDetectionDao).getActiveCount()
        verify(mockWhitelistedDeviceDao).getAllWhitelistedList()
    }

    @Test
    fun `doWork completes without errors when no anomalies found`() = runTest {
        `when`(mockAnomalyDetectionDao.getTotalCount()).thenReturn(0)
        `when`(mockAnomalyDetectionDao.getActiveCount()).thenReturn(0)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork reports new anomalies correctly`() = runTest {
        // Simulate finding 5 new anomalies
        `when`(mockAnomalyDetectionDao.getTotalCount())
            .thenReturn(10) // Before
            .thenReturn(15) // After
        `when`(mockAnomalyDetectionDao.getActiveCount())
            .thenReturn(5)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(mockAnomalyDetectionDao, times(2)).getTotalCount()
    }

    @Test
    fun `doWork handles large device counts`() = runTest {
        `when`(mockDeviceDetectionDao.getUniqueDeviceCount(DeviceType.WIFI_NETWORK))
            .thenReturn(100)
        `when`(mockDeviceDetectionDao.getUniqueDeviceCount(DeviceType.BLUETOOTH_DEVICE))
            .thenReturn(50)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }
}
