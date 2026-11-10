package com.lumenchord.pianoweave.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Debug builds (and any other debuggable install): used to show developer-only tools. */
val Context.isDebuggable: Boolean
    get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
