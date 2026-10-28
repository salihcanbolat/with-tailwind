package com.damla.backend.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Runtime settings, read from environment variables so that secrets never live in the repository.
 *
 * Without `DATABASE_URL` everything is kept in memory (local development and tests). Without
 * `FCM_PROJECT_ID` and `GOOGLE_APPLICATION_CREDENTIALS` pushes are logged instead of sent.
 */
data class AppConfig(
    val port: Int = 8080,
    val databaseUrl: String? = null,
    val databaseUser: String? = null,
    val databasePassword: String? = null,
    val sources: List<String> = listOf("binance", "truncgil"),
    val fcmProjectId: String? = null,
    val fcmCredentialsFile: String? = null,
    /** Lets Claude write the evening summary's sentence; without it the phone writes its own. */
    val anthropicApiKey: String? = null,
    /** Candles kept in memory: the longest offered interval (1 month) plus a day. */
    val memory: Duration = 31.days,
    /** Candles kept in the database, for history charts up to one year. */
    val retention: Duration = 400.days,
    val evaluateEvery: Duration = 2.seconds,
) {
    companion object {
        fun fromEnv(env: Map<String, String>): AppConfig =
            AppConfig(
                port = env["PORT"]?.toInt() ?: 8080,
                databaseUrl = env["DATABASE_URL"],
                databaseUser = env["DATABASE_USER"],
                databasePassword = env["DATABASE_PASSWORD"],
                sources =
                    env["DAMLA_SOURCES"]
                        ?.split(',')
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?: listOf("binance", "truncgil"),
                fcmProjectId = env["FCM_PROJECT_ID"],
                fcmCredentialsFile = env["GOOGLE_APPLICATION_CREDENTIALS"],
                anthropicApiKey = env["ANTHROPIC_API_KEY"]?.takeIf { it.isNotBlank() },
            )
    }
}
