package com.m57.hermescontrol.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.data.model.ConfigUpdateRequest
import com.m57.hermescontrol.data.model.ModelCapabilities
import com.m57.hermescontrol.data.model.ModelProvider
import com.m57.hermescontrol.data.model.reasoningSupport
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.safeApiCall
import com.m57.hermescontrol.data.remote.safeApiRead
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** One staged capability override for a single model. */
data class CapabilityOverride(
    val fast: Boolean? = null,
    val reasoning: Boolean? = null,
    val tools: Boolean? = null,
    val vision: Boolean? = null,
)

data class ModelManagerUiState(
    val isLoading: Boolean = false,
    val isProbing: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val toastMessage: String? = null,
    val providers: List<ModelProvider> = emptyList(),
    val query: String = "",
    /** "providerSlug:modelName" -> override staged by the user, not yet saved. */
    val overrides: Map<String, CapabilityOverride> = emptyMap(),
    /** Models the user added manually this session ("provider:model" keys). */
    val customModels: Set<String> = emptySet(),
    val savedOnce: Boolean = false,
)

class ModelManagerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ModelManagerUiState())
    val uiState: StateFlow<ModelManagerUiState> = _uiState.asStateFlow()

    fun load(forceRefresh: Boolean = false) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (
                val result =
                    safeApiRead {
                        ApiClient.hermesApi.getModelOptions(
                            refresh = forceRefresh,
                            includeUnconfigured = true,
                        )
                    }
            ) {
                is NetworkResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            providers = result.data.providers,
                        )
                    }
                }

                is NetworkResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.error.message,
                        )
                    }
                }
            }
        }
    }

    /** "Probe": re-pull the catalog with refresh=true so the server re-resolves
     * provider capability data, then merge. The catalog is the server's own
     * capability source — there is no separate per-model test endpoint. */
    fun probeCatalog() {
        if (_uiState.value.isProbing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProbing = true, errorMessage = null) }
            when (
                val result =
                    safeApiRead {
                        ApiClient.hermesApi.getModelOptions(
                            refresh = true,
                            includeUnconfigured = true,
                        )
                    }
            ) {
                is NetworkResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isProbing = false,
                            providers = result.data.providers,
                            toastMessage = "Capabilities refreshed from server",
                        )
                    }
                }

                is NetworkResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isProbing = false,
                            errorMessage = result.error.message,
                        )
                    }
                }
            }
        }
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun toastShown() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun key(provider: String, model: String) = "$provider:$model"

    /** Effective capability for a model: user override wins over catalog. */
    fun effectiveCapabilities(provider: String, model: String): ModelCapabilities? {
        val state = _uiState.value
        val catalog =
            state.providers
                .firstOrNull { it.slug == provider }
                ?.capabilities
                ?.get(model)
        val override = state.overrides[key(provider, model)] ?: return catalog
        return ModelCapabilities(
            fast = override.fast ?: catalog?.fast,
            reasoning = override.reasoning ?: catalog?.reasoning,
            can_disable_reasoning = catalog?.can_disable_reasoning,
            supports_tools = override.tools ?: catalog?.supports_tools,
            supports_vision = override.vision ?: catalog?.supports_vision,
            supports_reasoning = override.reasoning ?: catalog?.reasoningSupport,
            context_window = catalog?.context_window,
            max_output_tokens = catalog?.max_output_tokens,
            model_family = catalog?.model_family,
        )
    }

    fun setOverride(
        provider: String,
        model: String,
        transform: (CapabilityOverride) -> CapabilityOverride,
    ) {
        val k = key(provider, model)
        _uiState.update { state ->
            val current = state.overrides[k] ?: CapabilityOverride()
            state.copy(overrides = state.overrides + (k to transform(current)))
        }
    }

    fun addCustomModel(providerSlug: String, modelName: String) {
        val name = modelName.trim()
        if (name.isEmpty()) return
        _uiState.update {
            it.copy(
                customModels = it.customModels + key(providerSlug, name),
                toastMessage = "Staged model $name — Save to write to config",
            )
        }
    }

    fun removeCustomModel(providerSlug: String, modelName: String) {
        val k = key(providerSlug, modelName)
        _uiState.update {
            it.copy(
                customModels = it.customModels - k,
                overrides = it.overrides - k,
            )
        }
    }

    /** Persist staged overrides + custom models through the structured config
     * update (server merges into the active profile). */
    fun save() {
        if (_uiState.value.isSaving) return
        val state = _uiState.value
        if (state.overrides.isEmpty() && state.customModels.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val capabilitiesJson =
                buildJsonObject {
                    state.overrides.forEach { (k, override) ->
                        put(
                            k,
                            buildJsonObject {
                                override.fast?.let { put("fast", JsonPrimitive(it)) }
                                override.reasoning?.let { put("reasoning", JsonPrimitive(it)) }
                                override.tools?.let { put("supports_tools", JsonPrimitive(it)) }
                                override.vision?.let { put("supports_vision", JsonPrimitive(it)) }
                            },
                        )
                    }
                }
            val customJson =
                buildJsonObject {
                    state.customModels.forEach { k ->
                        put(k, JsonPrimitive(k.substringAfter(':')))
                    }
                }
            val body =
                ConfigUpdateRequest(
                    config =
                        mapOf(
                            "model_capabilities" to capabilitiesJson,
                            "custom_models" to customJson,
                        ),
                )
            when (val result = safeApiCall { ApiClient.hermesApi.updateConfig(body) }) {
                is NetworkResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            savedOnce = true,
                            toastMessage = "Model config saved",
                        )
                    }
                    probeCatalog()
                }

                is NetworkResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = result.error.message,
                        )
                    }
                }
            }
        }
    }
}

/** Visible provider→models after the keyword filter. */
fun filterProviders(
    providers: List<ModelProvider>,
    customModels: Set<String>,
    query: String,
): List<Pair<ModelProvider, List<String>>> {
    val q = query.trim().lowercase()
    return providers.mapNotNull { provider ->
        val catalogModels = provider.models.orEmpty()
        val staged =
            customModels
                .filter { it.substringBefore(':') == provider.slug }
                .map { it.substringAfter(':') }
        val all = (catalogModels + staged).distinct()
        val providerMatches =
            q.isEmpty() ||
                provider.name.lowercase().contains(q) ||
                provider.slug.lowercase().contains(q)
        val visible =
            if (providerMatches) {
                all
            } else {
                all.filter { it.lowercase().contains(q) }
            }
        if (visible.isEmpty() && q.isNotEmpty()) {
            null
        } else {
            provider to visible
        }
    }
}
