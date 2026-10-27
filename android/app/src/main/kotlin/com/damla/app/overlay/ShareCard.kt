package com.damla.app.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.damla.api.AssetSnapshot
import com.damla.app.R
import com.damla.core.Formats
import com.damla.core.logo
import com.damla.core.shape
import com.damla.core.stateWord
import com.damla.core.stateWordLower
import com.damla.gauge.BlurProfile
import com.damla.gauge.OverlayFrame
import com.damla.gauge.OverlayStage
import com.damla.gauge.android.Damla
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The share card (docs/06): the scene darkens and blurs, and a dark card rises from below. It shares the
 * direction, never the price; "Paylaş" hands the card as an image to Android's share sheet.
 */
@Composable
internal fun ShareLayer(
    model: OverlayModel,
    frame: OverlayFrame,
    actions: OverlayActions,
) {
    val open = model.scene.sharing && model.scene.stage == OverlayStage.DETAIL
    val dismiss = stringResource(R.string.share_dismiss)
    val dialog = stringResource(R.string.share_dialog)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f * frame.shareDimAlpha.toFloat()))
            .then(
                if (open) {
                    Modifier
                        .semantics { contentDescription = dismiss }
                        .pointerInput(model) { detectTapGestures { model.share(false) } }
                } else {
                    Modifier
                },
            ),
    )
    if (frame.shareAlpha <= 0 && !open) return
    val card = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val asset = model.selected
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val top = ((maxHeight.value - DIALOG_HEIGHT) / 2).coerceAtLeast(MIN_TOP)
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .width(CARD_WIDTH.dp)
                .graphicsLayer {
                    translationY = (top + frame.shareTranslateY.toFloat()).dp.toPx()
                    scaleX = frame.shareScale.toFloat()
                    scaleY = frame.shareScale.toFloat()
                    alpha = frame.shareAlpha.toFloat()
                }.semantics { contentDescription = dialog },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ShareCard(
                model,
                asset,
                Modifier
                    .drawBehind {
                        boxShadow(0f, 0f, size.width, size.height, 30.dp.toPx(), 30.dp.toPx(), 70.dp.toPx(), Color.Black.copy(alpha = 0.6f))
                    }.drawWithContent {
                        // Recorded without the outer shadow: this is the image that gets shared.
                        card.record { this@drawWithContent.drawContent() }
                        drawLayer(card)
                    },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogButton(
                    stringResource(R.string.share_close),
                    Color.White.copy(alpha = 0.14f),
                    Color.White,
                    FontWeight.SemiBold,
                    open,
                    Modifier.weight(1f),
                ) {
                    model.share(false)
                }
                DialogButton(
                    stringResource(R.string.share_send),
                    Color.White,
                    OverlayColors.SHARE_BUTTON_TEXT,
                    FontWeight.Bold,
                    open,
                    Modifier.weight(2f),
                    icon = true,
                ) {
                    scope.launch { actions.share(card.toImageBitmap().asAndroidBitmap()) }
                }
            }
        }
    }
}

/** 300 x 380, #0E141B, corner 30: signature and date, the gauge on a soft glow, name, direction, the three parts. */
@Composable
private fun ShareCard(
    model: OverlayModel,
    asset: AssetSnapshot,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val drop = asset.drop ?: return
    val grey = model.grey(asset)
    val glow = Color(model.chartColor(asset))
    val visual = model.visual(asset) ?: return
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier
            .size(CARD_WIDTH.dp, CARD_HEIGHT.dp)
            .clip(shape)
            .background(OverlayColors.SHARE_CARD)
            .border(0.5.dp, Color.White.copy(alpha = 0.14f), shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // `filter: blur(50px)` on a 160 circle at (70, 70), 45% opaque.
            val radius = 80.0
            val stops = BlurProfile.stops(radius, GLOW_BLUR)
            val outer =
                BlurProfile
                    .outerRadius(radius, GLOW_BLUR)
                    .toFloat()
                    .dp
                    .toPx()
            drawCircle(
                Brush.radialGradient(
                    *stops.map { it.offset.toFloat() to glow.copy(alpha = (it.alpha * GLOW_ALPHA).toFloat()) }.toTypedArray(),
                    center = Offset(150.dp.toPx(), 150.dp.toPx()),
                    radius = outer,
                ),
                radius = outer,
                center = Offset(150.dp.toPx(), 150.dp.toPx()),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(12.dp, 14.dp)) {
                        // A 24 x 24 view box in 12 x 14: scaled to 12 and centred vertically.
                        translate(top = 1.dp.toPx()) {
                            scale(size.width / 24f, pivot = Offset.Zero) { drawPath(DROP_MARK, OverlayColors.SHARE_TEXT) }
                        }
                    }
                    BasicText(
                        stringResource(R.string.overlay_folder_title),
                        style = designText(12f, FontWeight.Bold, OverlayColors.SHARE_TEXT),
                    )
                }
                BasicText(
                    DateTimeFormatter.ofPattern("EEE, d MMM", Formats.TURKISH).format(LocalDate.now()),
                    style = designText(12f, FontWeight.Normal, Color(0x99EBEBF5)),
                )
            }
            Spacer(Modifier.height(18.dp))
            Damla(
                visual = visual.copy(pulseTurns = 0),
                size = 104.dp,
                logo = asset.logo(),
                contentDescription =
                    context.getString(
                        com.damla.core.R.string.gauge_description,
                        asset.short,
                        context.getString(stateWordLower(drop.shape(), grey)),
                    ),
                reduceMotion = true,
            )
            Spacer(Modifier.height(12.dp))
            BasicText(asset.name, style = designText(24f, FontWeight.Bold, OverlayColors.SHARE_TEXT, letterSpacing = -0.3f), maxLines = 1)
            BasicText(
                stringResource(
                    R.string.detail_change,
                    context.getString(stateWord(drop.shape(), grey)),
                    Formats.interval(context, drop.interval),
                ),
                style = designText(15f, FontWeight.Bold, OverlayColors.bright(drop.shape(), grey)),
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val names = listOf(R.string.part_drop, R.string.part_upper_ring, R.string.part_lower_ring)
                OverlayModel.parts(asset).forEachIndexed { j, part ->
                    Column(
                        Modifier
                            .weight(1f)
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        BasicText(
                            context.getString(names[j]),
                            style = designText(10f, FontWeight.Normal, Color(0x8CEBEBF5)),
                            maxLines = 1,
                            softWrap = false,
                        )
                        BasicText(
                            context.getString(stateWord(part.shape(), grey)),
                            style = designText(12f, FontWeight.Bold, OverlayColors.bright(part.shape(), grey)),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogButton(
    text: String,
    background: Color,
    content: Color,
    weight: FontWeight,
    enabled: Boolean,
    modifier: Modifier,
    icon: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(48.dp)
            .background(background, RoundedCornerShape(16.dp))
            .clickable(remember { MutableInteractionSource() }, null, enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) Icon(OverlayIcon.SHARE, 16f, 2.2f, content)
        BasicText(text, style = designText(16f, weight, content))
    }
}

private const val CARD_WIDTH = 300f
private const val CARD_HEIGHT = 380f

/** The card, the 14 gap and the 48 tall buttons. */
private const val DIALOG_HEIGHT = CARD_HEIGHT + 14 + 48
private const val MIN_TOP = 24f
private const val GLOW_BLUR = 50.0
private const val GLOW_ALPHA = 0.45
