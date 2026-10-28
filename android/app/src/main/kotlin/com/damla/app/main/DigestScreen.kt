package com.damla.app.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.damla.api.DigestMoment
import com.damla.app.R
import com.damla.app.ui.DamlaColors
import com.damla.app.ui.GlassCard
import com.damla.app.ui.Label
import com.damla.app.ui.NavHeader
import com.damla.app.ui.PrimaryButton
import com.damla.app.ui.Separator
import com.damla.app.ui.text
import com.damla.core.Formats
import com.damla.core.StoredDigest
import com.damla.core.logo
import com.damla.core.sentence
import com.damla.core.stateWord
import com.damla.core.stateWordLower
import com.damla.core.visual
import com.damla.gauge.Logo
import com.damla.gauge.Shape
import com.damla.gauge.android.Damla
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "Günün özeti" (docs/09): the latest evening summary and the day's moments, each drop turning from its old
 * shape to its new one in turn with the gauge's own state change (docs/04). Not in the designs: a proposal in
 * the app's own language. Reduced motion shows the new shapes at once.
 */
@Composable
fun DigestScreen(
    model: AppModel,
    navigator: Navigator,
) {
    val digest by model.digest.collectAsState()
    val watchlist by model.watchlist.collectAsState()
    val context = LocalContext.current

    fun name(id: String): String = watchlist.firstOrNull { it.id == id }?.name ?: model.asset(id)?.name ?: id

    ScreenColumn(top = 0.dp, bottom = 40.dp) {
        val day = digest?.let { runCatching { LocalDate.parse(it.day) }.getOrNull() }
        NavHeader(
            title = stringResource(R.string.digest_title),
            subtitle = day?.let { DateTimeFormatter.ofPattern("d MMMM EEEE", Formats.TURKISH).format(it) },
            back = stringResource(R.string.action_back_panel),
            onBack = { navigator.pop() },
        )
        val current = digest
        if (current == null || current.moments.isEmpty()) {
            Label(stringResource(R.string.digest_none), text(15f, color = DamlaColors.secondaryText, lineHeight = 21f))
            return@ScreenColumn
        }
        Label(stringResource(R.string.digest_count, current.count), text(22f, FontWeight.Bold, letterSpacing = -0.3f))
        Label(
            current.text.ifEmpty { current.sentence(context, ::name) },
            text(15f, color = DamlaColors.text85, lineHeight = 21f),
        )
        Replay(current, ::name) { id -> model.asset(id)?.logo() }
    }
}

/** The moments in order of time; [played] of them already show their new shape. */
@Composable
private fun Replay(
    digest: StoredDigest,
    name: (String) -> String,
    logo: (String) -> Logo?,
) {
    val moments = digest.moments
    var round by remember(digest) { mutableIntStateOf(0) }
    var played by remember(digest) { mutableIntStateOf(if (reduceMotion) moments.size else 0) }
    LaunchedEffect(digest, round) {
        if (reduceMotion) {
            played = moments.size
            return@LaunchedEffect
        }
        played = 0
        delay(START_MILLIS)
        for (i in 1..moments.size) {
            played = i
            delay(STEP_MILLIS)
        }
    }
    GlassCard(radius = 20.dp) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            moments.forEachIndexed { i, moment ->
                if (i > 0) Separator()
                // A new round starts each gauge afresh on its old shape, rather than turning back to it.
                key(round, i) { MomentRow(moment, name(moment.asset), logo(moment.asset), after = i < played) }
            }
        }
    }
    PrimaryButton(stringResource(R.string.digest_replay), height = 46.dp, enabled = !reduceMotion) { round++ }
}

@Composable
private fun MomentRow(
    moment: DigestMoment,
    name: String,
    logo: Logo?,
    after: Boolean,
) {
    val context = LocalContext.current
    val visual = moment.visual(after) ?: return
    val from = Shape.valueOf(moment.from)
    val to = Shape.valueOf(moment.to)
    val time =
        runCatching { Instant.parse(moment.at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) }
            .getOrDefault("")
    val change =
        context.getString(
            R.string.digest_change,
            context.getString(stateWord(from, closed = false)),
            context.getString(stateWord(to, closed = false)),
        )
    val description = context.getString(R.string.digest_moment_description, time, name, change)
    Row(
        Modifier.heightIn(min = 58.dp).clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(time, text(13f, color = DamlaColors.text55), Modifier.width(40.dp))
        Damla(
            visual = visual,
            size = 30.dp,
            logo = logo,
            contentDescription =
                context.getString(
                    com.damla.core.R.string.gauge_description,
                    name,
                    context.getString(stateWordLower(visual.shape, closed = false)),
                ),
            reduceMotion = reduceMotion,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(name, text(15f, FontWeight.SemiBold), maxLines = 1)
            Label(change, text(13f, color = partColor(to, grey = false)), maxLines = 1)
        }
    }
}

/** The screen settles before the first moment plays (docs/05 `dur.open`). */
private const val START_MILLIS = 600L

/** One moment after another: the state change's longest part, the light spreading, takes 1300 ms (docs/04). */
private const val STEP_MILLIS = 1_300L
