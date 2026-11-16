package com.metrolist.shared.utils

import com.metrolist.shared.peek.PeekFork
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CrashReportingTest {
    @Test
    fun plainReleaseVersionsAllowReporting() {
        // PeekMusic: plain versions report only while the fork enables crash reporting.
        assertEquals(PeekFork.CRASH_REPORTING_AVAILABLE, isCrashReportingAvailable("14.0.0"))
        assertEquals(PeekFork.CRASH_REPORTING_AVAILABLE, isCrashReportingAvailable("14.1.2"))
    }

    @Test
    fun buildMetadataDisablesReporting() {
        assertFalse(isCrashReportingAvailable("14.0.0+24897bc"))
        assertFalse(isCrashReportingAvailable("14.1.2+build.123"))
    }
}
