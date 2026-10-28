package com.damla.backend.digest

import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import com.damla.engine.State
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/** One line of the day for the writer: at [time] ("14:05", the phone's time) [name]'s drop turned [from] -> [to]. */
data class DayLine(
    val time: String,
    val name: String,
    val from: State,
    val to: State,
)

/** Writes the summary sentence of a day ("yapay zeka destekli", docs/09); null leaves the phone's own sentence. */
fun interface DigestWriter {
    suspend fun write(lines: List<DayLine>): String?
}

/**
 * The summary sentence from Claude. It only retells the moments it is given; [DigestText.accept] throws away
 * any answer with prices, percentages or numbers that are not in them, and the phone then writes its own.
 */
class ClaudeDigestWriter(
    private val client: AnthropicClient,
) : DigestWriter {
    private val log = LoggerFactory.getLogger(ClaudeDigestWriter::class.java)

    override suspend fun write(lines: List<DayLine>): String? {
        if (lines.isEmpty()) return null
        val prompt = DigestText.prompt(lines)
        val params =
            MessageCreateParams
                .builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .system(DigestText.SYSTEM)
                .addUserMessage(prompt)
                // A short retelling needs little thought.
                .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
                // A declined request is retried on the model Anthropic recommends for its category.
                .addBeta(AnthropicBeta.of(FALLBACK_BETA))
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build()
        return try {
            val message = withContext(Dispatchers.IO) { client.beta().messages().create(params) }
            if (message.stopReason().orElse(null) == BetaStopReason.REFUSAL) return null
            val text = message.content().flatMap { block -> block.text().map { listOf(it.text()) }.orElse(emptyList()) }.joinToString(" ")
            DigestText.accept(text, prompt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("The summary could not be written; the phone writes its own", e)
            null
        }
    }

    companion object {
        private const val MODEL = "claude-opus-5-5"
        private const val MAX_TOKENS = 2_048L
        private const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
    }
}

/** The instructions, the input and the check of the summary sentence. */
object DigestText {
    const val MAX_LENGTH = 240

    val SYSTEM =
        """
        You write the evening summary of Damla, a phone app that shows the direction of currencies, gold,
        silver and crypto as a drop. Its short-term direction is the "damla" (drop).

        Write in Turkish: one or two short sentences, at most 200 characters, plain text with no emoji,
        markdown or quotes. Retell only the moments you are given: whose drop changed and how each one ended
        the day. Use these words for the states: UP = yükselişte, DOWN = düşüşte, FLAT = sabit,
        WAVE = dalgalı. Use the asset names exactly as given. Never mention prices, percentages, amounts or
        reasons, and give no advice or forecasts. Times may only appear as written in the moments.
        """.trimIndent()

    /** The day's moments, one per line: "14:05 Gram altın: FLAT -> UP". */
    fun prompt(lines: List<DayLine>): String =
        "Bugünün anları:\n" + lines.joinToString("\n") { "${it.time} ${it.name}: ${it.from.name} -> ${it.to.name}" }

    /**
     * The answer as one line, or null when it is empty, too long, mentions money or percentages, or has a
     * number that is not in [prompt] (so it cannot invent a price or a time).
     */
    fun accept(
        text: String,
        prompt: String,
    ): String? {
        val line = text.replace(Regex("\\s+"), " ").trim()
        if (line.isEmpty() || line.length > MAX_LENGTH) return null
        if (line.any { it in "%₺$€£" }) return null
        val allowed = Regex("\\d+").findAll(prompt).map { it.value }.toSet()
        if (Regex("\\d+").findAll(line).any { it.value !in allowed }) return null
        return line
    }
}
