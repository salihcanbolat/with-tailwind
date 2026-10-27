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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.fail

/**
 * Plays the scenarios of `reference/motion.json` through [GaugeMotion] and compares every animated value
 * with what Chromium computed for design/Gosterge.dc.html (see tools/gauge-reference).
 */
class PrototypeReferenceTest {
    private val reference =
        Json.parseToJsonElement(checkNotNull(javaClass.getResource("/reference/motion.json")).readText()).jsonObject

    private val colors =
        mapOf("#34C759" to Shape.UP, "#FF3B30" to Shape.DOWN, "#FFCC00" to Shape.FLAT, "#FF9500" to Shape.WAVE)

    private fun visual(props: Map<String, String>) =
        GaugeVisual(
            shape = Shape.valueOf(props.getValue("tip").uppercase()),
            upperRing = colors.getValue(props.getValue("c1")),
            lowerRing = colors.getValue(props.getValue("c2")),
            closed = props.getValue("kapali").toBoolean(),
            pulseTurns = props.getValue("donus").toDouble().toInt() / 360,
        )

    private fun JsonObject.props(): Map<String, String> = mapValues { it.value.jsonPrimitive.content }

    @Test
    fun `every animated value matches the prototype in Chromium`() {
        val failures = mutableListOf<String>()
        var checked = 0
        for (scenario in reference.getValue("scenarios").jsonArray.map { it.jsonObject }) {
            val name = scenario.getValue("name").jsonPrimitive.content
            val size = scenario.getValue("size").jsonPrimitive.double
            val u = size / Geometry.WIDTH
            var props = scenario.getValue("from").jsonObject.props()
            val motion = GaugeMotion(visual(props), speed = props.getValue("hiz").toDouble())
            val steps = scenario.getValue("steps").jsonArray.map { it.jsonObject }
            var nextStep = 0
            for (sample in scenario.getValue("samples").jsonArray.map { it.jsonObject }) {
                val t = sample.getValue("t").jsonPrimitive.double
                while (nextStep < steps.size && steps[nextStep].getValue("at").jsonPrimitive.double <= t) {
                    props = props + steps[nextStep].getValue("props").jsonObject.props()
                    motion.update(visual(props), steps[nextStep].getValue("at").jsonPrimitive.double)
                    nextStep++
                }
                val frame = motion.frame(t)

                fun check(
                    what: String,
                    expected: Double,
                    actual: Double,
                    tolerance: Double,
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

                fun matrix(key: String) = sample.getValue(key).jsonArray.map { it.jsonPrimitive.double }

                fun matrix(
                    key: String,
                    inner: String,
                ) = sample
                    .getValue(key)
                    .jsonObject
                    .getValue(inner)
                    .jsonArray
                    .map { it.jsonPrimitive.double }

                fun number(
                    key: String,
                    inner: String,
                ) = sample
                    .getValue(key)
                    .jsonObject
                    .getValue(inner)
                    .jsonPrimitive.double

                val radians = Math.toRadians(frame.dropRotation)
                val drop = matrix("dropWrap", "m")
                check("drop.a", drop[0], frame.dropScale * cos(radians), 0.003)
                check("drop.b", drop[1], frame.dropScale * sin(radians), 0.003)
                check("drop.ty", drop[5], frame.dropTranslateY * u, 0.02)
                check("drop.alpha", number("dropWrap", "o"), frame.dropAlpha, 0.003)
                check("circle.scale", matrix("circWrap", "m")[0], frame.circleScale, 0.003)
                check("circle.alpha", number("circWrap", "o"), frame.circleAlpha, 0.003)
                for (key in listOf("dropSquash", "circSquash")) {
                    check("$key.x", matrix(key)[0], frame.squashX, 0.002)
                    check("$key.y", matrix(key)[3], frame.squashY, 0.002)
                }
                checkColor("fill", sample.getValue("fill").jsonArray, frame.bodyColor)
                checkColor("circleFill", sample.getValue("circFill").jsonArray, frame.bodyColor)
                check("halo.alpha", number("halo", "o"), frame.haloAlpha, 0.003)
                check("halo.scale", matrix("halo", "m")[0], frame.haloScale, 0.003)
                check("halo.left", number("halo", "left"), size / 2 + (frame.haloX - Geometry.HALO_DIAMETER / 2) * u, 0.01)
                check("halo.top", number("halo", "top"), size / 2 + (frame.haloY - Geometry.HALO_DIAMETER / 2) * u, 0.01)
                checkColor(
                    "halo.color",
                    sample
                        .getValue("halo")
                        .jsonObject
                        .getValue("color")
                        .jsonArray,
                    frame.haloColor,
                )
                checkColor(
                    "upper.color",
                    sample
                        .getValue("arc1")
                        .jsonObject
                        .getValue("color")
                        .jsonArray,
                    frame.upperRingColor,
                )
                checkColor(
                    "lower.color",
                    sample
                        .getValue("arc2")
                        .jsonObject
                        .getValue("color")
                        .jsonArray,
                    frame.lowerRingColor,
                )
                // Deliberate difference: the prototype skips this redraw when shape and ring change together.
                if (!(name == "interrupted" && t >= 300)) {
                    check("upper.offset", number("arc1", "offset"), frame.upperRingDashOffset, 0.05)
                }
                check("lower.offset", number("arc2", "offset"), frame.lowerRingDashOffset, 0.05)
                val logo = matrix("logoWrap")
                check("logo.x", logo[4], frame.logoX * u, 0.02)
                check("logo.y", logo[5], frame.logoY * u, 0.02)
                checkColor("logo.color", sample.getValue("logoColor").jsonArray, frame.logoTextColor)
                check("glint.alpha", number("hl", "o"), frame.glintAlpha, 0.003)
                check("glint.x", matrix("hl", "m")[4], frame.glintX * u, 0.02)
                check("glint.y", matrix("hl", "m")[5], frame.glintY * u, 0.02)
                val light = Math.toRadians(frame.lightRotation)
                check("light.cos", matrix("swRot")[0], cos(light), 0.003)
                check("light.sin", matrix("swRot")[1], sin(light), 0.003)
            }
        }
        if (failures.isNotEmpty()) {
            fail("${failures.size} of $checked values differ from the prototype:\n" + failures.take(40).joinToString("\n"))
        }
        println("Checked $checked values against the prototype")
    }
}
