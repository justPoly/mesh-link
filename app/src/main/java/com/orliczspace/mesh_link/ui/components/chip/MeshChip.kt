package com.orliczspace.mesh_link.ui.components.chip

import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun MeshChip(

    text: String,

    onClick: () -> Unit

) {

    AssistChip(

        onClick = onClick,

        label = {

            Text(text)

        },

        colors = AssistChipDefaults.assistChipColors()

    )

}