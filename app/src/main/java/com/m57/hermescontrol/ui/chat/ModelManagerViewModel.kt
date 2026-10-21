package com.m57.hermescontrol.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.data.model.ConfigUpdateRequest
import com.m57.hermescontrol.data.model.ModelAssignmentRequest
import com.m57.hermescontrol.data.model.ModelCapabilities
import com.m57.hermescontrol.data.model.ModelProvider
import com.m57.hermescontrol.data.model.reasoningSupport
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.safeApiCall
import com.m57.hermescontrol.data.remote.safeApiRead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * One staged capability override for a single model. Null = untouched (the
 * catalog value stands); non-null = the user's choice, written to config on
 * Save under `model_overrides.<provider>.<model>.supports_*` (the shape the
 * Hermes server documents: providers.md "model_overrides" / per-named-provider
 * `providers.<name>.models.<id>.supports_vision`).
 *
 * `attachments` maps to the server's native-attach behavior: a model with
 * attachments=true gets `supports_vision: true` so the agent attaches images
 * natively (image_input_mode auto); false writes `supports_vision: false`.
 */
data class CapabilityOverride(
    val fast: Boolean? = null,
    val reasoning: Boolean? = null,
    val tools: Boolean? = null,
    val vision: Boolean? = null,
    val attachments: Boolean? = null,
    /** Staged display name from the model-row rename (swipe-right). Written as
     * `model_overrides.<provider>.<model>.display_name` on Save; null falls the
     * row back to the catalog id. */
    val displayName: String? = null,
)

/** A custom provider staged in the add-provider sheet, written to
 * `providers.<name>` on Save (schema: api/name/api_key|key_env/transport/
 * models/discover_models per providers.md "Named Custom Providers").
 *
 * The provider editor (batch 5A) stages through the SAME map: it may leave
 * [baseUrl] blank to keep the server's current endpoint — [save] then skips
 * the api/transport keys and writes only the fields the user filled in.
 * [enabled]=false maps to the documented `providers.<name>.enabled` disable
 * key; [discoverModels] null keeps the key out of the payload (edits), true
 * reproduces the add flow's historical write. */
data class CustomProviderDraft(
    val slug: String,
    val displayName: String,
    val baseUrl: String,
    val apiKey: String = "",
    val transport: String = "chat_completions",
    val models: List<String> = emptyList(),
    val discoverModels: Boolean? = null,
    val enabled: Boolean = true,
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
    /** Models staged for removal ("provider:model" keys) after the confirm
     * popup. Written as `enabled: false` overrides on Save — the server hides
     * an entry without deleting catalog data (same semantics as the documented
     * provider-level `enabled: false`). */
    val hiddenModels: Set<String> = emptySet(),
    /** Providers staged for removal after the confirm popup. Written as
     * `providers.<slug>.enabled: false` on Save (the documented disable key) —
     * the catalog entry survives, the provider leaves the manager. */
    val hiddenProviders: Set<String> = emptySet(),
    /** Custom providers staged in the add-provider sheet. */
    val customProviders: Map<String, CustomProviderDraft> = emptyMap(),
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

    /** Catalog re-pull with refresh=true: the server re-resolves provider
     * capability data (its own probe of the catalog / models.dev source).
     * Used after Save and when the user finishes adding a provider/model —
     * per the design, catalog probing is an add-flow step, not a standing
     * toolbar action (the bottom-left button now opens the probe-TEST sheet). */
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

    private fun key(
        provider: String,
        model: String,
    ) = "$provider:$model"

    /** Effective capability for a model: user override wins over catalog. */
    fun effectiveCapabilities(
        provider: String,
        model: String,
    ): ModelCapabilities? {
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

    /** Attachments toggle state: explicit override if staged, else the
     * model's effective vision capability (native attach rides on vision). */
    fun effectiveAttachments(
        provider: String,
        model: String,
    ): Boolean {
        val override = _uiState.value.overrides[key(provider, model)]
        override?.attachments?.let { return it }
        return effectiveCapabilities(provider, model)?.supports_vision == true
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

    /** Staged display-name override for a model row; null = show the catalog id. */
    fun displayNameOverride(
        provider: String,
        model: String,
    ): String? =
        _uiState.value.overrides[key(provider, model)]
            ?.displayName
            ?.takeIf { it.isNotBlank() }

    /**
     * Capability toggles write through immediately (batch-6 F2): the override
     * is staged for the optimistic UI AND persisted at once via the same
     * `model_overrides.<provider>.<model>` updateConfig path [save] uses —
     * there is no Save gate on the toggle path. The staged override stays
     * staged after a successful write so the row keeps showing override-first
     * values until the next server catalog re-pull; on failure the stage is
     * rolled back and the error surfaced. Other staged features (custom
     * models, renames, provider edits) remain Save-gated.
     */
    fun applyCapabilityOverride(
        provider: String,
        model: String,
        transform: (CapabilityOverride) -> CapabilityOverride,
    ) {
        val k = key(provider, model)
        val previous = _uiState.value.overrides[k]
        setOverride(provider, model, transform)
        val staged = _uiState.value.overrides[k] ?: return
        viewModelScope.launch {
            val modelOverridesJson =
                buildJsonObject {
                    put(
                        provider,
                        buildJsonObject { put(model, overrideToJson(staged)) },
                    )
                }
            val body =
                ConfigUpdateRequest(config = mapOf("model_overrides" to modelOverridesJson))
            when (val result = safeApiCall { ApiClient.hermesApi.updateConfig(body) }) {
                is NetworkResult.Success -> {
                    _uiState.update {
                        it.copy(toastMessage = "Capability saved for $model")
                    }
                }

                is NetworkResult.Failure -> {
                    _uiState.update { state ->
                        if (state.overrides[k] == staged) {
                            state.copy(
                                overrides =
                                    if (previous == null) {
                                        state.overrides - k
                                    } else {
                                        state.overrides + (k to previous)
                                    },
                                errorMessage = result.error.message,
                            )
                        } else {
                            // A newer tap owns the staged value now; that tap's
                            // own write will succeed or roll back.
                            state.copy(errorMessage = result.error.message)
                        }
                    }
                }
            }
        }
    }

    fun addCustomModel(
        providerSlug: String,
        modelName: String,
    ) {
        val name = modelName.trim()
        if (name.isEmpty()) return
        _uiState.update {
            it.copy(
                customModels = it.customModels + key(providerSlug, name),
                toastMessage = "Staged model $name — Save to write to config",
            )
        }
    }

    /** Stage a model for removal (any model, not just custom ones). The UI
     * shows the confirmation popup before calling this. Removal is written as
     * an `enabled: false` override on Save — catalog data stays intact and the
     * model can be re-enabled later. */
    fun stageRemoveModel(
        providerSlug: String,
        modelName: String,
    ) {
        val k = key(providerSlug, modelName)
        _uiState.update {
            it.copy(
                hiddenModels = it.hiddenModels + k,
                toastMessage = "Staged removal of $modelName — Save to apply",
            )
        }
    }

    fun unstageRemoveModel(
        providerSlug: String,
        modelName: String,
    ) {
        val k = key(providerSlug, modelName)
        _uiState.update { it.copy(hiddenModels = it.hiddenModels - k) }
    }

    fun isHidden(
        providerSlug: String,
        modelName: String,
    ): Boolean = key(providerSlug, modelName) in _uiState.value.hiddenModels

    /** The `model_overrides.<provider>.<model>` JSON for ONE staged override —
     * shared by [save] (bulk write) and [applyCapabilityOverride] (write-through),
     * so both paths emit the documented schema exactly the same way. */
    private fun overrideToJson(override: CapabilityOverride) =
        buildJsonObject {
            override.fast?.let { put("fast", JsonPrimitive(it)) }
            override.reasoning?.let { put("supports_reasoning", JsonPrimitive(it)) }
            override.tools?.let { put("supports_tools", JsonPrimitive(it)) }
            override.vision?.let { put("supports_vision", JsonPrimitive(it)) }
            override.attachments?.let { put("supports_vision", JsonPrimitive(it)) }
            override.displayName?.takeIf(String::isNotBlank)?.let { put("display_name", JsonPrimitive(it)) }
        }

    /**
     * Stage an add or an edit of a provider (both flows persist through this
     * one map). A blank [CustomProviderDraft.baseUrl] means "keep the server
     * endpoint" (edit path); the add dialog enforces a URL before confirm.
     */
    fun stageCustomProvider(draft: CustomProviderDraft) {
        if (draft.slug.isBlank()) return
        _uiState.update {
            it.copy(
                customProviders = it.customProviders + (draft.slug to draft),
                toastMessage = "Staged provider ${draft.displayName} — Save to write to config",
            )
        }
    }

    fun removeCustomProvider(slug: String) {
        _uiState.update { it.copy(customProviders = it.customProviders - slug) }
    }

    /** Stage a configured provider for removal: on Save it is written as
     * `providers.<slug>.enabled = false`. A staged-but-unsaved custom provider
     * is dropped from the staging map instead (it never reached config). */
    fun stageRemoveProvider(slug: String) {
        _uiState.update { state ->
            if (slug in state.customProviders) {
                state.copy(customProviders = state.customProviders - slug)
            } else {
                state.copy(
                    hiddenProviders = state.hiddenProviders + slug,
                    toastMessage = "Staged provider removal — Save to write to config",
                )
            }
        }
    }

    fun unstageRemoveProvider(slug: String) {
        _uiState.update { it.copy(hiddenProviders = it.hiddenProviders - slug) }
    }

    /** Set a model as the server's main chat model (scope "main" — the only
     * assignment scope for chat models). Surfaced as the swipe-right action on
     * a model row. */
    fun setAsMain(
        providerSlug: String,
        modelName: String,
    ) {
        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    safeApiCall {
                        ApiClient.hermesApi.setModelAssignment(
                            ModelAssignmentRequest(
                                scope = "main",
                                provider = providerSlug,
                                model = modelName,
                            ),
                        )
                    }
                }
            val toast =
                when (result) {
                    is NetworkResult.Success -> "Main model: $modelName"
                    is NetworkResult.Failure -> result.error.message
                }
            _uiState.update { it.copy(toastMessage = toast) }
        }
    }

    /** Persist staged overrides, hidden models, custom models, and custom
     * providers through the structured config update (server merges into the
     * active profile). Keys follow the documented schema:
     * `model_overrides.<provider>.<model>.supports_*` and
     * `providers.<name>.{api,name,api_key,transport,models,discover_models}`. */
    fun save() {
        if (_uiState.value.isSaving) return
        val state = _uiState.value
        val dirty =
            state.overrides.isNotEmpty() ||
                state.customModels.isNotEmpty() ||
                state.hiddenModels.isNotEmpty() ||
                state.hiddenProviders.isNotEmpty() ||
                state.customProviders.isNotEmpty()
        if (!dirty) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }

            // model_overrides.<provider>.<model> — capabilities + enabled flags,
            // nested per the documented schema (not flat "slug:model" keys).
            val overridesByProvider =
                mutableMapOf<String, MutableMap<String, kotlinx.serialization.json.JsonObject>>()
            state.overrides.forEach { (k, override) ->
                val provider = k.substringBefore(':')
                val model = k.substringAfter(':')
                overridesByProvider.getOrPut(provider) { mutableMapOf() }[model] = overrideToJson(override)
            }
            state.hiddenModels.forEach { k ->
                val provider = k.substringBefore(':')
                val model = k.substringAfter(':')
                val existing = overridesByProvider.getOrPut(provider) { mutableMapOf() }[model]
                val merged =
                    buildJsonObject {
                        existing?.forEach { (jk, jv) -> put(jk, jv) }
                        put("enabled", JsonPrimitive(false))
                    }
                overridesByProvider.getOrPut(provider) { mutableMapOf() }[model] = merged
            }
            val modelOverridesJson =
                buildJsonObject {
                    overridesByProvider.forEach { (provider, models) ->
                        put(
                            provider,
                            buildJsonObject {
                                models.forEach { (model, json) -> put(model, json) }
                            },
                        )
                    }
                }

            // providers.<name> — staged custom providers + custom models on
            // existing providers (added to the provider's `models` list).
            val providersJson =
                buildJsonObject {
                    state.customProviders.forEach { (slug, draft) ->
                        put(
                            slug,
                            buildJsonObject {
                                // Editor-saved drafts keep the current endpoint by
                                // leaving baseUrl blank; only UI-filled keys go out.
                                if (draft.baseUrl.isNotBlank()) {
                                    put("api", JsonPrimitive(draft.baseUrl))
                                    put("transport", JsonPrimitive(draft.transport))
                                }
                                put("name", JsonPrimitive(draft.displayName))
                                if (draft.apiKey.isNotBlank()) {
                                    put("api_key", JsonPrimitive(draft.apiKey))
                                }
                                draft.discoverModels?.let { put("discover_models", JsonPrimitive(it)) }
                                if (!draft.enabled) {
                                    put("enabled", JsonPrimitive(false))
                                }
                                if (draft.models.isNotEmpty()) {
                                    put(
                                        "models",
                                        kotlinx.serialization.json.JsonArray(
                                            draft.models.map { JsonPrimitive(it) },
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    state.customModels
                        .groupBy { it.substringBefore(':') }
                        .forEach { (provider, keys) ->
                            // Custom models on a provider the user did not stage
                            // wholesale: append under providers.<slug>.models.
                            // Skipped for providers staged for removal — the
                            // disable write below owns that slug.
                            if (provider !in state.customProviders && provider !in state.hiddenProviders) {
                                put(
                                    provider,
                                    buildJsonObject {
                                        put(
                                            "models",
                                            kotlinx.serialization.json.JsonArray(
                                                keys.map { JsonPrimitive(it.substringAfter(':')) },
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    // Provider removal: the documented disable key. The catalog
                    // entry survives server-side; the provider drops out of the
                    // configured set.
                    state.hiddenProviders.forEach { slug ->
                        put(
                            slug,
                            buildJsonObject {
                                put("enabled", JsonPrimitive(false))
                            },
                        )
                    }
                }

            val body =
                ConfigUpdateRequest(
                    config =
                        mapOf(
                            "model_overrides" to modelOverridesJson,
                            "providers" to providersJson,
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

/** Visible provider→models after the keyword filter, minus staged removals. */
fun filterProviders(
    providers: List<ModelProvider>,
    customModels: Set<String>,
    hiddenModels: Set<String>,
    hiddenProviders: Set<String>,
    query: String,
): List<Pair<ModelProvider, List<String>>> {
    val q = query.trim().lowercase()
    return providers.mapNotNull { provider ->
        // Only providers that are actually in config belong in the manager:
        // the server flags them via `authenticated`; providers staged for
        // removal disappear immediately.
        if (provider.authenticated != true || provider.slug in hiddenProviders) {
            return@mapNotNull null
        }
        val catalogModels = provider.models.orEmpty()
        val staged =
            customModels
                .filter { it.substringBefore(':') == provider.slug }
                .map { it.substringAfter(':') }
        val all =
            (catalogModels + staged)
                .distinct()
                .filter { "${provider.slug}:$it" !in hiddenModels }
        // Multi-term search: every whitespace-separated term must match the
        // provider OR the model, so "openai gpt" finds openai's gpt models and
        // "gpt openai" works identically (theta: provider+model keywords at
        // the same time, not one or the other).
        val terms = q.split(Regex("\\s+")).filter { it.isNotBlank() }
        val providerHay = "${provider.name.lowercase()} ${provider.slug.lowercase()}"
        val visible =
            all.filter { model ->
                val m = model.lowercase()
                terms.all { term -> providerHay.contains(term) || m.contains(term) }
            }
        if (visible.isEmpty() && q.isNotEmpty()) {
            null
        } else {
            provider to visible
        }
    }
}
