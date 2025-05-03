package com.neko.themeengine

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.annotation.BoolRes
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.IntegerRes

val Context.isDarkMode: Boolean
    get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

@ChecksSdkIntAtLeast(api = 31)
fun hasS(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

fun Context.getBooleanSafe(@BoolRes res: Int, defaultValue: Boolean): Boolean =
    try { resources.getBoolean(res) } catch (_: Resources.NotFoundException) { defaultValue }

fun Context.getIntSafe(@IntegerRes res: Int, defaultValue: Int): Int =
    try { resources.getInteger(res) } catch (_: Resources.NotFoundException) { defaultValue }
