package com.damla.app.overlay

import android.animation.ValueAnimator
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.damla.app.MainActivity
import com.damla.app.R
import com.damla.core.DamlaServices
import com.damla.core.WidgetSize
import com.damla.core.WidgetStyle
import com.damla.gauge.OverlayGeometry
import com.damla.gauge.WidgetArrangement
import com.damla.widget.DamlaWidget
import kotlinx.coroutines.delay
import java.io.File

/**
 * The overlay opened from the widget (docs/06): a transparent full-screen window that starts its animation
 * exactly where the widget is (`Intent.getSourceBounds`) and finishes when the scene is back in the widget.
 */
class OverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        val widgetId = intent.getIntExtra(DamlaWidget.EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val bounds = intent.sourceBounds
        val blur = WindowBlur(window)
        val reduceMotion = !ValueAnimator.areAnimatorsEnabled()
        val actions = OverlayActions(finish = ::leave, openApp = ::openApp, share = ::shareImage)
        setContent {
            val repository = DamlaServices.repository
            val data by produceState<OverlayData?>(null) { value = OverlayData.load(repository, widgetId) }
            var origin by remember { mutableStateOf<Offset?>(null) }
            var size by remember { mutableStateOf(IntSize.Zero) }
            val density = LocalDensity.current
            val scope = rememberCoroutineScope()
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned {
                        origin = it.positionOnScreen()
                        size = it.size
                    },
            ) {
                val loaded = data
                val at = origin
                if (loaded != null && (loaded.assets.isEmpty() || bounds == null)) {
                    // Nothing to animate yet (no states, or not opened from a widget): the app explains why.
                    LaunchedEffect(Unit) { openApp(null) }
                } else if (loaded != null && bounds != null && at != null && size != IntSize.Zero) {
                    val model =
                        remember(loaded) {
                            with(density) {
                                val left = (bounds.left - at.x).toDp().value.toDouble()
                                val top = (bounds.top - at.y).toDp().value.toDouble()
                                val width =
                                    bounds
                                        .width()
                                        .toDp()
                                        .value
                                        .toDouble()
                                val arrangement =
                                    WidgetArrangement.of(
                                        width,
                                        bare = loaded.config.style == WidgetStyle.BARE,
                                        single = loaded.config.size == WidgetSize.SINGLE,
                                        available = loaded.inWidget,
                                    )
                                val geometry =
                                    OverlayGeometry.forWidget(
                                        screenWidth =
                                            size.width
                                                .toDp()
                                                .value
                                                .toDouble(),
                                        screenHeight =
                                            size.height
                                                .toDp()
                                                .value
                                                .toDouble(),
                                        left = left,
                                        top = top,
                                        width = width,
                                        height =
                                            bounds
                                                .height()
                                                .toDp()
                                                .value
                                                .toDouble(),
                                        arrangement = arrangement,
                                    )
                                OverlayModel(loaded, geometry, arrangement, reduceMotion, repository, scope)
                            }
                        }
                    LiveUpdates(model)
                    OverlayScreen(model, widgetId, blur, actions)
                }
            }
        }
    }

    /** Leaves without a window animation: the scene has already flown back into the widget. */
    private fun leave() {
        finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun openApp(route: AppRoute?) {
        val intent =
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .apply {
                    when (route) {
                        is AppRoute.Asset -> {
                            putExtra(MainActivity.EXTRA_ASSET, route.id)
                        }

                        is AppRoute.Alarm -> {
                            putExtra(MainActivity.EXTRA_ALARM_ASSET, route.id)
                        }

                        is AppRoute.WidgetSettings -> {
                            putExtra(MainActivity.EXTRA_WIDGET_ID, route.widgetId)
                        }

                        null -> {}
                    }
                }
        startActivity(intent)
        leave()
    }

    /** Hands the share card to Android's share sheet as a PNG (docs/06). */
    private fun shareImage(bitmap: Bitmap) {
        val directory = File(cacheDir, SHARE_DIRECTORY).apply { mkdirs() }
        val file = File(directory, SHARE_FILE)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(this, "$packageName.share", file)
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, getString(R.string.share_chooser)))
    }

    private companion object {
        const val SHARE_DIRECTORY = "share"
        const val SHARE_FILE = "damla.png"
    }
}

/** Live prices while the overlay is visible (docs/06, "Nabız"); the stream closes when it is not. */
@androidx.compose.runtime.Composable
private fun LiveUpdates(model: OverlayModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(model) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                runCatching {
                    DamlaServices.repository.api
                        .stream(model.ids())
                        .collect(model::onPrice)
                }
                delay(RECONNECT_DELAY)
            }
        }
    }
}

private const val RECONNECT_DELAY = 3_000L
