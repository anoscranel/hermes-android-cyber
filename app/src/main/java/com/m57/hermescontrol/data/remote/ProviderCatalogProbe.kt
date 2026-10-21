package com.m57.hermescontrol.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Request
import java.io.IOException

/**
 * Direct OpenAI-compatible catalog probe for the add-provider flow (batch 5A).
 *
 * The existing probe machinery (ProbeSheet / ProbeViewModel) measures a real
 * chat turn over the WS session and cannot list a provider's catalog without
 * one, so this takes the spec's fallback path: the app's plain HTTP client
 * GETs `{baseUrl}/models` — the OpenAI-compatible catalog endpoint almost
 * every provider implements. The entered API key rides as a Bearer header
 * when present. Uses [OkHttpProvider.probe] (short timeouts) on Dispatchers.IO.
 */
object ProviderCatalogProbe {
    /**
     * GET `{baseUrl}/models` and return the catalog ids ("data[].id" shape;
     * also tolerates a top-level array or a "models" array). Never throws —
     * failures arrive as [NetworkResult.Failure].
     */
    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
    ): NetworkResult<List<String>> =
        withContext(Dispatchers.IO) {
            val request =
                try {
                    val builder = Request.Builder().url("${baseUrl.trim().trimEnd('/')}/models")
                    if (apiKey.isNotBlank()) {
                        builder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
                    }
                    builder.build()
                } catch (e: IllegalArgumentException) {
                    return@withContext NetworkResult.Failure(
                        NetworkError.Unknown("Invalid base URL", e),
                    )
                }
            try {
                OkHttpProvider.probe.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        NetworkResult.Failure(mapHttpError(response.code, body))
                    } else {
                        try {
                            NetworkResult.Success(parseModelIds(body))
                        } catch (e: SerializationException) {
                            NetworkResult.Failure(NetworkError.Unknown("Unexpected catalog response", e))
                        }
                    }
                }
            } catch (e: IOException) {
                NetworkResult.Failure(
                    NetworkError.Connection(
                        "Network connection failure: ${e.message ?: "unknown connection error"}",
                        e,
                    ),
                )
            }
        }

    internal fun parseModelIds(body: String): List<String> {
        val root = OkHttpProvider.json.parseToJsonElement(body)
        val entries =
            when (root) {
                is JsonObject -> {
                    (root["data"] as? JsonArray) ?: (root["models"] as? JsonArray) ?: JsonArray(emptyList())
                }

                is JsonArray -> {
                    root
                }

                else -> {
                    JsonArray(emptyList())
                }
            }
        val ids = mutableListOf<String>()
        entries.forEach { element ->
            modelIdOf(element)?.takeIf(String::isNotBlank)?.let(ids::add)
        }
        return ids.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    private fun modelIdOf(element: JsonElement): String? =
        when (element) {
            is JsonObject -> (element["id"] as? JsonPrimitive)?.content
            is JsonPrimitive -> if (element.isString) element.content else null
            else -> null
        }
}
