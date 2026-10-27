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
                check("box.alpha", num(obj("box"), "alpha"), frame.boxAlpha)
                check("box.scale", num(obj("box"), "scale"), frame.boxScale)
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
}
