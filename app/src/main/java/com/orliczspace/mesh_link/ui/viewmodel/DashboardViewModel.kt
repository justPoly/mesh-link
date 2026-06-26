package com.orliczspace.mesh_link.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.orliczspace.mesh_link.network.RoutingState
import com.orliczspace.mesh_link.ui.model.UiFlow

data class DashboardUiState(

    val internetAvailable: Boolean = false,

    val connectionType: String = "None",

    val neighbours: List<String> = emptyList(),

    val routingStates: List<RoutingState> = emptyList(),

    val activeFlows: List<UiFlow> = emptyList(),

    val showDebug: Boolean = false
)

class DashboardViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())

    val uiState: StateFlow<DashboardUiState> = _uiState

    fun updateInternetStatus(available: Boolean, type: String) {

        _uiState.value = _uiState.value.copy(
            internetAvailable = available,
            connectionType = type
        )
    }

    fun updateNeighbours(nodes: List<String>) {

        _uiState.value = _uiState.value.copy(
            neighbours = nodes
        )
    }

    fun updateRouting(states: List<RoutingState>) {

        _uiState.value = _uiState.value.copy(
            routingStates = states
        )
    }

    fun updateFlows(flows: List<UiFlow>) {

        _uiState.value = _uiState.value.copy(
            activeFlows = flows
        )
    }
}