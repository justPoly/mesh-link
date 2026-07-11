package com.orliczspace.mesh_link.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MeshTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit
) {

    OutlinedTextField(

        modifier = Modifier.fillMaxWidth(),

        value = value,

        onValueChange = onValueChange,

        label = {

            Text(label)

        },

        shape = MaterialTheme.shapes.medium

    )

}