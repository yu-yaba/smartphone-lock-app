package jp.kawai.ultrafocus.service

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class ServiceRestartSchedulerTest {

    @Before
    fun setUp() {
        ServiceRestartScheduler.resetForTest()
    }

    @After
    fun tearDown() {
        ServiceRestartScheduler.resetForTest()
    }

    @Test
    fun shouldScheduleRespectsMinIntervalPerRequestCode() {
        val interval = ServiceRestartScheduler.minScheduleIntervalForTest()
        val requestCode = 1001

        assertTrue(ServiceRestartScheduler.shouldScheduleForTest(requestCode, nowElapsed = 0L))
        assertFalse(ServiceRestartScheduler.shouldScheduleForTest(requestCode, nowElapsed = interval - 1L))
        assertTrue(ServiceRestartScheduler.shouldScheduleForTest(requestCode, nowElapsed = interval))
    }

    @Test
    fun shouldScheduleIsIndependentPerRequestCode() {
        val interval = ServiceRestartScheduler.minScheduleIntervalForTest()
        val first = 2001
        val second = 2002

        assertTrue(ServiceRestartScheduler.shouldScheduleForTest(first, nowElapsed = 0L))
        assertTrue(ServiceRestartScheduler.shouldScheduleForTest(second, nowElapsed = 0L))
        assertFalse(ServiceRestartScheduler.shouldScheduleForTest(first, nowElapsed = interval - 1L))
        assertFalse(ServiceRestartScheduler.shouldScheduleForTest(second, nowElapsed = interval - 1L))
        assertTrue(ServiceRestartScheduler.shouldScheduleForTest(first, nowElapsed = interval))
        assertTrue(ServiceRestartScheduler.shouldScheduleForTest(second, nowElapsed = interval))
    }
}
