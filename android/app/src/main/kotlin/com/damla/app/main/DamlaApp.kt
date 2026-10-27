package com.damla.app.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.damla.app.alarms.AlarmEditScreen
import com.damla.app.alarms.AlarmsScreen
import com.damla.app.ui.DamlaColors
import kotlinx.coroutines.delay

/** The app's screens (docs/07): the first start, the three tabs and the screens pushed on them. */
@Composable
fun DamlaApp(
    model: AppModel,
    navigator: Navigator,
) {
    val onboarded by model.onboarded.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        // On each return a full sync (registration too), then a fresh snapshot every minute until hidden.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.refresh()
            while (true) {
                delay(POLL_MILLIS)
                model.poll()
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(DamlaColors.background),
    ) {
        when (onboarded) {
            // Still reading the device store: the background alone, so no screen flashes past.
            null -> {
                Unit
            }

            false -> {
                OnboardingScreen(onDone = model::finishOnboarding)
            }

            true -> {
                // Declared before the screens, so a screen's own back handler (an unsaved draft) wins.
                BackHandler(enabled = navigator.stack.size > 1) { navigator.pop() }
                when (val route = navigator.current) {
                    Route.Panel -> PanelScreen(model, navigator)
                    Route.Alarms -> AlarmsScreen(model, navigator)
                    Route.Settings -> SettingsScreen(model)
                    is Route.Asset -> AssetScreen(model, navigator, route.id)
                    Route.Add -> AddScreen(model, navigator)
                    Route.Cross -> CrossScreen(model, navigator)
                    is Route.AlarmEdit -> AlarmEditScreen(model, navigator, route.alarmId, route.assetId)
                    Route.Widget -> WidgetScreen(model, navigator)
                    Route.Onboarding -> OnboardingScreen(onDone = { navigator.pop() })
                }
                if (navigator.current in TABS) {
                    TabBar(navigator.current, navigator::selectTab, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

private val TABS = setOf(Route.Panel, Route.Alarms, Route.Settings)

private const val POLL_MILLIS = 60_000L
