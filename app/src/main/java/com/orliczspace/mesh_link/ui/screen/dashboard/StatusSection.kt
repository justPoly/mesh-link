package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.ui.components.card.StatusCard

@Composable
fun StatusSection() {

    Column(
        modifier = Modifier.padding(horizontal = 20.dp)
    ) {

        StatusCard(

            status = "Connected",

            online = true

        )

    }

}