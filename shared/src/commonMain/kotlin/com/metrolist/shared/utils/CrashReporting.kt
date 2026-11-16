package com.metrolist.shared.utils

import com.metrolist.shared.BuildConfig
import com.metrolist.shared.peek.PeekFork

// PeekMusic: PeekFork.CRASH_REPORTING_AVAILABLE gates every crash-reporting call site, including ones upstream adds later.
fun isCrashReportingAvailable(appVersion: String = BuildConfig.APP_VERSION): Boolean =
    PeekFork.CRASH_REPORTING_AVAILABLE && '+' !in appVersion
