package com.m57.hermescontrol.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.data.model.ModelAssignmentRequest
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.safeApiCall
import com.m57.hermescontrol.data.ws.HermesWsClient
import com.m57.hermescontrol.data.ws.WsEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** One model's probe result. Timings in ms; tps from the server's own
 * message.complete payload (avg_tps / usage), honest — no client-side guess. */
data class ProbeResult(
    val provider: String,
    val model: String,
    val status: ProbeStatus = ProbeStatus.PENDING,
    val ttftMs: Long? = null,
    val latencyMs: Long? = null,
    val tps: Double? = null,
    val error: String? = null,
)

enum class ProbeStatus { PENDING, RUNNING, OK, FAILED }

data class ProbeUiState(
    val results: Map<String, ProbeResult> = emptyMap(),
    val isRunning: Boolean = false,
    val selected: Set<String> = emptySet(),
    val toastMessage: String? = null,
)

/**
 * Live probe-testing harness for the model manager. Sends a minimal real
 * request ("Reply with exactly: ok") to the ACTIVE chat session after
 * assigning the target model to that session scope, then measures from the WS
 * event stream:
 *  - TTFT  = send -> first MessageToken
 *  - latency = send -> MessageComplete
 *  - TPS   = message.complete payload usage/avg_tps when the server reports it
 *
 * There is no REST probe endpoint and chat runs over the WebSocket only, so a
 * real short turn is the only honest probe. The probe runs on the user's
 * active session (one short assistant reply lands in the transcript per
 * model); the original model assignment is restored at the end.
 */
class ProbeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ProbeUiState())
    val uiState: StateFlow<ProbeUiState> = _uiState.asStateFlow()

    private var probeJob: Job? = null

    fun key(
        provider: String,
        model: String,
    ) = "$provider:$model"

    fun toggleSelect(
        provider: String,
        model: String,
    ) {
        val k = key(provider, model)
        _uiState.update {
            it.copy(selected = if (k in it.selected) it.selected - k else it.selected + k)
        }
    }

    fun selectAll(pairs: List<Pair<String, String>>) {
        _uiState.update { it.copy(selected = pairs.map { (p, m) -> key(p, m) }.toSet()) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selected = emptySet()) }
    }

    fun toastShown() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun initResults(pairs: List<Pair<String, String>>) {
        _uiState.update { state ->
            val fresh =
                pairs.associate { (p, m) ->
                    key(p, m) to (state.results[key(p, m)] ?: ProbeResult(p, m))
                }
            state.copy(results = fresh)
        }
    }

    /** Probe every selected model sequentially (parallel probes would share the
     * one WS session and corrupt each other's timing). Restores the session's
     * original model assignment in a finally block. */
    fun runProbes(
        sessionId: String?,
        currentModelLabel: String?,
    ) {
        if (_uiState.value.isRunning) return
        if (sessionId == null) {
            _uiState.update { it.copy(toastMessage = "Open a chat session to probe models") }
            return
        }
        val targets = _uiState.value.selected.toList()
        if (targets.isEmpty()) {
            _uiState.update { it.copy(toastMessage = "Select at least one model") }
            return
        }
        // Original assignment ("provider/model" label) restored in the finally
        // below, so probing never leaves the session on the last probed model.
        val originalProvider = currentModelLabel?.substringBefore('/')
        val originalModel = currentModelLabel?.substringAfter('/', "")
        probeJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isRunning = true) }
                try {
                    targets.forEach { k ->
                        val provider = k.substringBefore(':')
                        val model = k.substringAfter(':')
                        probeOne(sessionId, provider, model)
                    }
                } finally {
                    if (!originalProvider.isNullOrBlank() && !originalModel.isNullOrBlank()) {
                        withContext(Dispatchers.IO) {
                            safeApiCall {
                                ApiClient.hermesApi.setModelAssignment(
                                    ModelAssignmentRequest(
                                        scope = "session",
                                        provider = originalProvider,
                                        model = originalModel,
                                    ),
                                )
                            }
                        }
                    }
                    _uiState.update { it.copy(isRunning = false) }
                }
            }
    }

    fun cancel() {
        probeJob?.cancel()
        _uiState.update { it.copy(isRunning = false) }
    }

    private suspend fun probeOne(
        sessionId: String,
        provider: String,
        model: String,
    ) {
        val k = key(provider, model)
        _uiState.update { state ->
            state.copy(results = state.results + (k to ProbeResult(provider, model, ProbeStatus.RUNNING)))
        }

        // Assign the model to this session scope only (scope = "session"), so
        // the global default model is untouched.
        when (
            val assign =
                safeApiCall {
                    ApiClient.hermesApi.setModelAssignment(
                        ModelAssignmentRequest(
                            scope = "session",
                            provider = provider,
                            model = model,
                        ),
                    )
                }
        ) {
            is NetworkResult.Failure -> {
                markResult(k, provider, model, ProbeStatus.FAILED, error = assign.error.message)
                return
            }

            is NetworkResult.Success -> {
                Unit
            }
        }

        val sendStart = System.currentTimeMillis()
        var firstTokenAt: Long? = null

        // Watch the event stream for this probe's turn before sending so no
        // early token races past the collector. Events are filtered to this
        // session so a concurrent chat elsewhere can't satisfy the probe.
        val collector =
            viewModelScope.launch {
                HermesWsClient.events.collect { event ->
                    if (event is WsEvent.MessageToken &&
                        event.sessionId == sessionId &&
                        firstTokenAt == null
                    ) {
                        firstTokenAt = System.currentTimeMillis()
                    }
                }
            }

        HermesWsClient.sendMessage(sessionId, PROBE_PROMPT)

        val complete =
            withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                HermesWsClient.events.first {
                    it is WsEvent.MessageComplete &&
                        (it.sessionId == sessionId || it.storedSessionId == sessionId)
                }
            }
        collector.cancel()
        val end = System.currentTimeMillis()

        if (complete == null) {
            markResult(k, provider, model, ProbeStatus.FAILED, error = "Timed out (${PROBE_TIMEOUT_MS / 1000}s)")
            return
        }
        complete as WsEvent.MessageComplete
        val tps =
            (complete.rawPayload?.get("avg_tps") as? Number)?.toDouble()
                ?: ((complete.rawPayload?.get("usage") as? Map<*, *>)?.get("avg_tps") as? Number)?.toDouble()
        _uiState.update { state ->
            state.copy(
                results =
                    state.results +
                        (
                            k to
                                ProbeResult(
                                    provider = provider,
                                    model = model,
                                    status = ProbeStatus.OK,
                                    ttftMs = firstTokenAt?.let { it - sendStart },
                                    latencyMs = end - sendStart,
                                    tps = tps,
                                )
                        ),
            )
        }
        // Small settle gap so back-to-back probes don't trip rate limits.
        delay(400)
    }

    private fun markResult(
        k: String,
        provider: String,
        model: String,
        status: ProbeStatus,
        error: String? = null,
    ) {
        _uiState.update { state ->
            state.copy(
                results = state.results + (k to ProbeResult(provider, model, status, error = error)),
            )
        }
    }

    companion object {
        private const val PROBE_PROMPT = "Reply with exactly: ok"
        private const val PROBE_TIMEOUT_MS = 45_000L
    }
}
