package com.damla.app.main

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/** The app's screens (docs/07). */
sealed interface Route {
    /** The three tabs. */
    data object Panel : Route

    data object Alarms : Route

    data object Settings : Route

    data class Asset(
        val id: String,
    ) : Route

    data object Add : Route

    data object Cross : Route

    /** A new alarm (optionally for [assetId]) or the alarm [alarmId]. */
    data class AlarmEdit(
        val alarmId: String? = null,
        val assetId: String? = null,
    ) : Route

    data object Widget : Route

    /** "Günün özeti": the latest evening summary and its moments, replayed. */
    data object Digest : Route

    data object Onboarding : Route
}

/** A plain back stack: the tabs are roots, other screens are pushed on top. */
class Navigator(
    start: Route = Route.Panel,
) {
    val stack: SnapshotStateList<Route> = mutableStateListOf(start)

    val current: Route get() = stack.last()

    val tab: Route get() = stack.first()

    fun push(route: Route) {
        stack.add(route)
    }

    /** Returns false when there is nothing to go back to. */
    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun selectTab(route: Route) {
        stack.clear()
        stack.add(route)
    }
}
