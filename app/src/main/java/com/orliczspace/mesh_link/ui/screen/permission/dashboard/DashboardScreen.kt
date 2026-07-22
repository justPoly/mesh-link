package com.orliczspace.mesh_link.ui.screen.dashboard
import com.orliczspace.mesh_link.ui.components.HeroHeader
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.orliczspace.mesh_link.ui.components.AnimatedStatusCard
import com.orliczspace.mesh_link.ui.navigation.Routes
import com.orliczspace.mesh_link.ui.scaffold.MeshScaffold
import com.orliczspace.mesh_link.ui.screen.permission.dashboard.*

@Composable
fun DashboardScreen(
    navController: NavHostController
) {

    MeshScaffold(

        title = "MeshLink",

        navController = navController,

        currentRoute = Routes.Dashboard.route

    ) { padding ->

        DashboardContent(padding)

    }

}


@Composable
private fun DashboardContent(
    padding: PaddingValues
) {

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        item {
            HeroHeader(
                username = "Polycarp",
                connected = true
            )
        }

    }

}