package com.orliczspace.mesh_link.ui.components.textfield

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MeshTextField(

    value: String,

    onValueChange: (String) -> Unit,

    label: String

) {

    OutlinedTextField(

        modifier = Modifier.fillMaxWidth(),

        value = value,

        onValueChange = onValueChange,

        label = {

            Text(label)

        }

    )

}