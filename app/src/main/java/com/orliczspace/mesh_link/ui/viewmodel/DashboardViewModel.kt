package com.orliczspace.mesh_link.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.orliczspace.mesh_link.network.RoutingState
import com.orliczspace.mesh_link.network.gateway.NatEntry
import com.orliczspace.mesh_link.ui.mapper.*
import com.orliczspace.mesh_link.ui.state.DashboardUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class DashboardViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())

    val uiState: StateFlow<DashboardUiState> = _uiState

    fun updateInternet(
        connected: Boolean,
        type: String
    ) {

        _uiState.update {

            it.copy(

                internetAvailable = connected,

                connectionType = type

            )

        }

    }

    fun updateNodes(
        routingStates: List<RoutingState>
    ) {

        _uiState.update {

            it.copy(

                nodes = routingStates.toUiNodes()

            )

        }

    }

    fun updateRoutes(
        routingStates: List<RoutingState>
    ) {

        _uiState.update {

            it.copy(

                routes = routingStates.toUiRoutes()

            )

        }

    }

    fun updateGateways(
        routingStates: List<RoutingState>
    ) {

        _uiState.update {

            it.copy(

                gateways = routingStates.toUiGateways()

            )

        }

    }

    fun updatePerformance(
        flows: List<NatEntry>
    ) {

        _uiState.update {

            it.copy(

                performance = flows.toUiPerformance()

            )

        }

    }

}