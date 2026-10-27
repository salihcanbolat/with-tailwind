package com.damla.app.main

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.damla.app.R
import com.damla.app.ui.DamlaColors
import com.damla.app.ui.GlassCard
import com.damla.app.ui.Icons
import com.damla.app.ui.Label
import com.damla.app.ui.PrimaryButton
import com.damla.app.ui.Separator
import com.damla.app.ui.tap
import com.damla.app.ui.text
import com.damla.gauge.Shape
import com.damla.widget.DamlaWidgetReceiver

/**
 * The first start (docs/07 lists it; no design yet, so this is a proposal in the screens' language): what
 * Damla is, how to put the widget on the home screen, and a button that asks the launcher to place it.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val canPin = canPinWidget(context)
    Box(Modifier.fillMaxSize()) {
        ScreenColumn(top = 56.dp, bottom = 180.dp, gap = 20.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                ShapeGauge(Shape.UP, 46.dp)
                ShapeGauge(Shape.FLAT, 46.dp)
                ShapeGauge(Shape.DOWN, 46.dp)
            }
            Label(stringResource(R.string.intro_title), text(32f, FontWeight.Bold, letterSpacing = -0.4f))
            Label(stringResource(R.string.intro_text), text(15f, color = DamlaColors.secondaryText, lineHeight = 21f))
            GlassCard(radius = 20.dp) {
                val steps = listOf(R.string.intro_step_1, R.string.intro_step_2, R.string.intro_step_3)
                steps.forEachIndexed { i, step ->
                    if (i > 0) Separator(Modifier.padding(start = 52.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(26.dp).background(DamlaColors.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                            Label((i + 1).toString(), text(13f, FontWeight.SemiBold, DamlaColors.accentLight))
                        }
                        Label(stringResource(step), text(15f, lineHeight = 20f), Modifier.weight(1f))
                    }
                }
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(DamlaColors.bottomBar)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (canPin) {
                PrimaryButton(stringResource(R.string.intro_pin), Modifier.fillMaxWidth(), icon = Icons.WIDGET) {
                    pinWidget(context)
                    onDone()
                }
            }
            Label(
                stringResource(R.string.intro_skip),
                text(15f, FontWeight.SemiBold, DamlaColors.accent, align = TextAlign.Center),
                Modifier.fillMaxWidth().tap(onClick = onDone).padding(vertical = 12.dp),
            )
        }
    }
}

/** Launchers may refuse to place widgets for apps (`requestPinAppWidget`). */
private fun canPinWidget(context: Context): Boolean = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

private fun pinWidget(context: Context) {
    AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, DamlaWidgetReceiver::class.java), null, null)
}
