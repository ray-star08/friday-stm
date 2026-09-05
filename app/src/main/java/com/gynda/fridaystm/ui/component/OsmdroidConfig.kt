package com.gynda.fridaystm.ui.component

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration

/**
 * One-time osmdroid bootstrap, shared by every map surface ([GeofenceMiniMap] and
 * the Larkam run map).
 *
 * osmdroid persists its own configuration (tile cache paths, user agent) into a
 * `SharedPreferences`; a dedicated `osmdroid` file is used rather than the app's
 * default prefs so map internals never collide with app state. Setting
 * `userAgentValue` is mandatory — OpenStreetMap tile servers answer 403 without it.
 *
 * Suspends and does its disk work on [Dispatchers.IO]: the load touches storage and
 * must not block the main thread during startup (SKILL.md §1).
 */
internal suspend fun configureOsmdroid(context: Context) = withContext(Dispatchers.IO) {
    val appContext = context.applicationContext
    val prefs = appContext.getSharedPreferences(OSMDROID_PREFS, Context.MODE_PRIVATE)
    Configuration.getInstance().load(appContext, prefs)
    Configuration.getInstance().userAgentValue = appContext.packageName
}

/**
 * osmdroid's own preferences file. Replaces the deprecated
 * `android.preference.PreferenceManager.getDefaultSharedPreferences`, without
 * pulling in the `androidx.preference` artifact for a single call.
 */
private const val OSMDROID_PREFS = "osmdroid"
