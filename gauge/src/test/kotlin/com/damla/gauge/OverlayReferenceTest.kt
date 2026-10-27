package com.damla.gauge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.fail

/**
 * Plays the scenarios of `reference/overlay.json` through [OverlayScene] and compares every animated value
 * with what Chromium computed for design/Prototip.dc.html (tools/gauge-reference/capture-overlay.js).
 */
class OverlayReferenceTest {
    private val reference =
        Json.parseToJsonElement(checkNotNull(javaClass.getResource("/reference/overlay.json")).readText()).jsonObject

    private fun color(hex: String): Int = (0xFF000000 or hex.removePrefix("#").toLong(16)).toInt()

    private fun JsonObject.points() = getValue("points").jsonArray.map { it.jsonPrimitive.double }

    private fun JsonObject.words() = getValue("words").jsonArray.map { color(it.jsonPrimitive.content) }

    private fun stage(index: Int) = OverlayStage.entries[index]

    @Test
    fun `every animated value of the overlay matches the prototype in Chromium`() {
        val failures = mutableListOf<String>()
        var checked = 0
        for (scenario in reference.getValue("scenarios").jsonArray.map { it.jsonObject }) {
            val name = scenario.getValue("name").jsonPrimitive.content
            val props = scenario.getValue("props").jsonObject.mapValues { it.value.jsonPrimitive.content }
            val start = scenario.getValue("start").jsonObject
            val size =
                when (props.getValue("boyut")) {
                    "1 yuva · 3 damla" -> PrototypeWidget.MINI_THREE
                    "1 yuva · 2 damla" -> PrototypeWidget.MINI_TWO
                    "3×1" -> PrototypeWidget.WIDE_THREE
                    "2×1" -> PrototypeWidget.WIDE_TWO
                    else -> PrototypeWidget.SINGLE
                }
            val bare = props.getValue("stil") == "Kutusuz"
            val geometry = OverlayGeometry.prototype(size, bare, right = props.getValue("taraf") == "sağ")
            val initial = scenario.getValue("initial").jsonObject
            val scene =
                OverlayScene(
                    geometry = geometry,
                    assetCount = 4,
                    flow = props.getValue("acilis") == "Akış",
                    mini = size == PrototypeWidget.MINI_THREE || size == PrototypeWidget.MINI_TWO,
                    bare = bare,
                    speed = props.getValue("hiz").toDouble(),
                    initialChart = initial.points(),
                    initialChartColor = color(initial.getValue("color").jsonPrimitive.content),
                    initialWords = initial.words(),
                    initialStage = stage(start["stage"]?.jsonPrimitive?.int ?: 0),
                    initialFrom = stage(start["from"]?.jsonPrimitive?.int ?: 0),
                    initialSelected = start["sel"]?.jsonPrimitive?.int ?: 0,
                )
            val steps = scenario.getValue("steps").jsonArray.map { it.jsonObject }
            var nextStep = 0
            for (sample in scenario.getValue("samples").jsonArray.map { it.jsonObject }) {
                val t = sample.getValue("t").jsonPrimitive.double
                while (nextStep < steps.size && steps[nextStep].getValue("at").jsonPrimitive.double <= t) {
                    val step = steps[nextStep]
                    val at = step.getValue("at").jsonPrimitive.double
                    val selected = step["sel"]?.jsonPrimitive?.int
                    when {
                        "go" in step -> scene.show(stage(step.getValue("go").jsonPrimitive.int), at, selected ?: scene.selected)
                        selected != null -> scene.select(selected, at)
                        "rng" in step -> scene.pickRange(step.getValue("rng").jsonPrimitive.int, at)
                        "share" in step -> scene.share(step.getValue("share").jsonPrimitive.boolean, at)
                    }
                    val chart = step.getValue("chart").jsonObject
                    scene.setChart(chart.points(), color(chart.getValue("color").jsonPrimitive.content), chart.words(), at)
                    nextStep++
                }
                val frame = scene.frame(t)

                fun check(
                    what: String,
                    expected: Double,
                    actual: Double,
                    tolerance: Double = 0.003,
                ) {
                    checked++
                    if (abs(expected - actual) > tolerance) failures += "$name t=$t $what: prototype $expected, model $actual"
                }

                fun checkColor(
                    what: String,
                    expected: JsonArray,
                    actual: Int,
                ) {
                    val channels = listOf((actual shr 16) and 0xFF, (actual shr 8) and 0xFF, actual and 0xFF)
                    expected.forEachIndexed { i, e -> check("$what[$i]", e.jsonPrimitive.double, channels[i].toDouble(), 1.01) }
                }

                fun obj(key: String) = sample.getValue(key).jsonObject

                fun num(
                    o: JsonObject,
                    key: String,
                ) = o.getValue(key).jsonPrimitive.double

                fun matrix(o: JsonObject) = o.getValue("m").jsonArray.map { it.jsonPrimitive.double }

                check("home.blur", num(obj("home"), "blur"), frame.homeBlur, 0.01)
                check("home.scale", num(obj("home"), "scale"), frame.homeScale)
                check("dim", sample.getValue("dim").jsonPrimitive.double, frame.dimAlpha)
                check("detailDim", sample.getValue("detailDim").jsonPrimitive.double, frame.detailDimAlpha)
                val box = obj("box")
                check("box.alpha", num(box, "alpha"), frame.boxAlpha)
                check("box.scale", num(box, "scale"), frame.boxScale)
                val origin =
                    box
                        .getValue("origin")
                        .jsonPrimitive.content
                        .split(" ")
                        .map { it.removeSuffix("px").toDouble() }
                check("box.originX", origin[0], frame.boxOriginX)
                check("box.originY", origin[1], frame.boxOriginY)
                // Chromium lays boxes out in 1/64 px steps; a size can be off by one step at each edge.
                check("box.left", num(box, "left"), frame.boxLeft, LAYOUT_UNIT)
                check("box.top", num(box, "top"), frame.boxTop, LAYOUT_UNIT)
                check("box.width", num(box, "width"), frame.boxWidth, 2 * LAYOUT_UNIT)
                check("box.height", num(box, "height"), frame.boxHeight, 2 * LAYOUT_UNIT)
                val background = box.getValue("background").jsonArray.map { it.jsonPrimitive.double }
                val backgroundAlpha = background.getOrElse(3) { 1.0 }
                check("box.background.alpha", backgroundAlpha, (frame.boxBackground ushr 24) / 255.0, COLOR_ALPHA)
                // Chromium writes any fully transparent colour as rgba(0, 0, 0, 0).
                if (backgroundAlpha > 0) {
                    checkColor("box.background", JsonArray(box.getValue("background").jsonArray.take(3)), frame.boxBackground)
                }
                val shadow = box.getValue("shadow").jsonArray.map { it.jsonPrimitive.double }
                check("box.shadow.alpha", shadow[3], frame.boxShadowAlpha, COLOR_ALPHA)
                check("box.shadow.y", shadow[5], frame.boxShadowY, 0.01)
                check("box.shadow.blur", shadow[6], frame.boxShadowBlur, 0.01)
                check("box.inset.alpha", shadow[11], frame.boxInsetAlpha, COLOR_ALPHA)
                check("widgetLayer", sample.getValue("widgetLayer").jsonPrimitive.double, frame.widgetLayerAlpha)
                check("header.alpha", num(obj("header"), "alpha"), frame.headerAlpha)
                check("header.y", num(obj("header"), "y"), frame.headerOffset, 0.01)
                val clip = obj("clip")
                val circle = clip.getValue("circle").jsonArray.map { it.jsonPrimitive.double }
                // The prototype places the circle in a card whose top-left corner is (100, 122) or (10, 122).
                val cardLeft = if (geometry.right) 100.0 else 10.0
                check("clip.radius", circle[0], frame.detailClipRadius, 0.01)
                check("clip.x", circle[1] + cardLeft, frame.detailClipX, 0.01)
                check("clip.y", circle[2] + 122, frame.detailClipY, 0.01)
                check("clip.alpha", num(clip, "alpha"), frame.detailClipAlpha)
                sample.getValue("cells").jsonArray.forEachIndexed { i, element ->
                    val cell = element.jsonObject
                    val m = matrix(cell)
                    val (cx, cy) = geometry.folderCell(i)
                    val model = frame.cells[i]
                    check("cell$i.x", m[4], model.x - cx, 0.05)
                    check("cell$i.y", m[5], model.y - cy, 0.05)
                    check("cell$i.scale", m[0], model.scale)
                    check("cell$i.alpha", num(cell, "alpha"), model.alpha)
                    check("cell$i.blur", num(cell, "blur"), model.blur, 0.01)
                    check("cell$i.shadow", num(cell, "shadow"), model.shadowAlpha, 0.005)
                    check("cell$i.label", num(cell, "label"), model.labelAlpha)
                }
                val hero = obj("hero")
                check("hero.scale", matrix(hero)[0], frame.heroScale)
                check("hero.x", matrix(hero)[4], frame.heroX, 0.05)
                check("hero.y", matrix(hero)[5], frame.heroY, 0.05)
                check("hero.alpha", num(hero, "alpha"), frame.heroAlpha)
                for (section in sample.getValue("sections").jsonArray.map { it.jsonObject }) {
                    val n = section.getValue("n").jsonPrimitive.int
                    check("sec$n.alpha", num(section, "alpha"), frame.sections[n].alpha)
                    check("sec$n.y", num(section, "y"), frame.sections[n].offset, 0.02)
                    check("sec$n.blur", num(section, "blur"), frame.sections[n].blur, 0.01)
                }
                sample.getValue("annos").jsonArray.forEachIndexed { j, element ->
                    val anno = element.jsonObject
                    check("anno$j.alpha", num(anno, "alpha"), frame.annotations[j].alpha)
                    check("anno$j.x", num(anno, "x"), frame.annotations[j].offset, 0.02)
                    check("anno$j.blur", num(anno, "blur"), frame.annotations[j].blur, 0.01)
                    checkColor("anno$j.color", anno.getValue("color").jsonArray, frame.annotationColors[j])
                }
                sample.getValue("lines").jsonArray.forEachIndexed { k, element ->
                    check("line$k.offset", num(element.jsonObject, "offset"), frame.annotationLineOffsets[k], 0.05)
                    check("line$k.dot", num(element.jsonObject, "dot"), frame.annotationDotAlphas[k])
                }
                val chart = obj("chart")
                check("chart.offset", num(chart, "offset"), frame.chartLineOffset, 0.1)
                checkColor("chart.color", chart.getValue("color").jsonArray, frame.chartColor)
                chart.points().forEachIndexed { i, y -> check("chart.point$i", y, frame.chartPoints[i], 0.06) }
                check("chart.area", num(chart, "area"), frame.chartAreaAlpha)
                check("chart.end", num(chart, "end"), frame.chartEndAlpha)
                checkColor("chart.endColor", chart.getValue("endColor").jsonArray, frame.chartEndColor)
                check("range", sample.getValue("rng").jsonPrimitive.double, frame.rangePillX, 0.02)
                sample.getValue("dots").jsonArray.forEachIndexed { i, element ->
                    check("dot$i.width", num(element.jsonObject, "width"), frame.dotWidths[i], 0.02)
                    check("dot$i.alpha", num(element.jsonObject, "alpha"), frame.dotAlphas[i])
                }
                val share = obj("share")
                check("share.dim", num(share, "dim"), frame.shareDimAlpha)
                check("share.alpha", num(share, "alpha"), frame.shareAlpha)
                check("share.y", num(share, "y"), frame.shareTranslateY, 0.02)
                check("share.scale", num(share, "scale"), frame.shareScale)
            }
        }
        if (failures.isNotEmpty()) {
            val byKind = failures.groupBy { it.substringAfter(" t=").substringAfter(" ").substringBefore(":") }.mapValues { it.value.size }
            fail("${failures.size} of $checked values differ: $byKind\n" + failures.take(40).joinToString("\n"))
        }
        println("Checked $checked overlay values against the prototype")
    }

    private companion object {
        const val LAYOUT_UNIT = 1.0 / 64

        /** Chromium keeps colour alpha in 8 bits. */
        const val COLOR_ALPHA = 1.3 / 255
    }
}
