package com.m57.hermescontrol.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.data.model.AuxiliaryTaskAssignment
import com.m57.hermescontrol.data.model.ModelAssignmentRequest
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

data class MediaModelsUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val tasks: List<AuxiliaryTaskAssignment> = emptyList(),
    val catalogPairs: List<Pair<String, String>> = emptyList(),
    val toastMessage: String? = null,
)

/**
 * ViewModel behind [MediaModelsSheet]: reads the server's auxiliary task
 * routing (GET api/model/auxiliary) plus the text-model catalog (for the
 * picker), and reassigns a task via POST api/model/set with scope=auxiliary.
 */
class MediaModelsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MediaModelsUiState())
    val uiState: StateFlow<MediaModelsUiState> = _uiState.asStateFlow()

    fun load() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val auxResult =
                withContext(Dispatchers.IO) {
                    safeApiRead { ApiClient.hermesApi.getAuxiliaryModels() }
                }
            val catalogResult =
                withContext(Dispatchers.IO) {
                    safeApiRead { ApiClient.hermesApi.getModelOptions() }
                }
            val tasks =
                when (auxResult) {
                    is NetworkResult.Success -> {
                        auxResult.data.tasks
                    }

                    is NetworkResult.Failure -> {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = auxResult.error.message)
                        }
                        return@launch
                    }
                }
            val pairs =
                when (catalogResult) {
                    is NetworkResult.Success -> {
                        catalogResult.data.providers.flatMap { provider ->
                            provider.models.orEmpty().map { model -> provider.slug to model }
                        }
                    }

                    is NetworkResult.Failure -> {
                        emptyList()
                    }
                }
            _uiState.update {
                it.copy(isLoading = false, tasks = tasks, catalogPairs = pairs)
            }
        }
    }

    fun assign(
        task: String,
        provider: String,
        model: String,
    ) {
        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    safeApiCall {
                        ApiClient.hermesApi.setModelAssignment(
                            ModelAssignmentRequest(
                                scope = "auxiliary",
                                task = task,
                                provider = provider,
                                model = model,
                            ),
                        )
                    }
                }
            when (result) {
                is NetworkResult.Success -> {
                    _uiState.update {
                        it.copy(toastMessage = "Aux task '$task' set to $provider / $model")
                    }
                    load()
                }

                is NetworkResult.Failure -> {
                    _uiState.update {
                        it.copy(toastMessage = "Failed to set $task: ${result.error.message}")
                    }
                }
            }
        }
    }

    fun toastShown() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
