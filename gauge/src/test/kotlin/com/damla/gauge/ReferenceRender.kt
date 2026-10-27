package com.damla.gauge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Renders the cases of tools/gauge-reference/render-cases.json with [GaugePainter] into SVG files, which
 * tools/gauge-reference/compare.js puts next to the prototype in Chromium. Run with
 * `./gradlew :gauge:renderReference`.
 */
fun main(args: Array<String>) {
    val cases =
        File(args[0]).readText().let {
            Json
                .parseToJsonElement(it)
                .jsonObject
                .getValue("cases")
                .jsonArray
        }
    val out = File(args[1]).apply { mkdirs() }
    for (case in cases.map { it.jsonObject }) {
        var props = case.getValue("from").jsonObject.strings()
        val motion = GaugeMotion(visualOf(props), speed = props.getValue("hiz").toDouble())
        for (step in case.getValue("steps").jsonArray.map { it.jsonObject }) {
            props = props + step.getValue("props").jsonObject.strings()
            motion.update(
                visualOf(props),
                step
                    .getValue("at")
                    .jsonPrimitive.content
                    .toDouble(),
            )
        }
        val size = props.getValue("size").toDouble()
        val layout = GaugeLayout(size)
        val pad = size
        val canvas = SvgCanvas(size + 2 * pad, layout.heightPx + 2 * pad)
        canvas.translate(pad, pad)
        GaugePainter.paint(
            motion.frame(
                case
                    .getValue("t")
                    .jsonPrimitive.content
                    .toDouble(),
            ),
            layout,
            logoOf(props.getValue("logo")),
            canvas,
        )
        File(out, case.getValue("name").jsonPrimitive.content + ".svg").writeText(canvas.toSvg())
    }
    println("Rendered ${cases.size} cases to $out")
}

private fun JsonObject.strings(): Map<String, String> = mapValues { it.value.jsonPrimitive.content }

private val shapesByColor = mapOf("#34C759" to Shape.UP, "#FF3B30" to Shape.DOWN, "#FFCC00" to Shape.FLAT, "#FF9500" to Shape.WAVE)

internal fun visualOf(props: Map<String, String>) =
    GaugeVisual(
        shape = Shape.valueOf(props.getValue("tip").uppercase()),
        upperRing = shapesByColor.getValue(props.getValue("c1")),
        lowerRing = shapesByColor.getValue(props.getValue("c2")),
        closed = props.getValue("kapali").toBoolean(),
        pulseTurns = props.getValue("donus").toDouble().toInt() / 360,
    )

internal fun logoOf(text: String): Logo =
    when (text) {
        "Au" -> Logo.Badge(BadgeKind.GOLD)
        "Ag" -> Logo.Badge(BadgeKind.SILVER)
        "$" -> Logo.Badge(BadgeKind.USD)
        "€" -> Logo.Badge(BadgeKind.EUR)
        "₿" -> Logo.Badge(BadgeKind.BITCOIN)
        else -> Logo.Text(text)
    }
