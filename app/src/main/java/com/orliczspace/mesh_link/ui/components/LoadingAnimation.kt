package com.orliczspace.mesh_link.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LoadingAnimation(

    text: String = "Scanning nearby nodes..."

) {

    Column(

        modifier = Modifier.fillMaxWidth(),

        horizontalAlignment = Alignment.CenterHorizontally

    ) {

        CircularProgressIndicator()

        Spacer(Modifier.height(16.dp))

        Text(

            text,

            style = MaterialTheme.typography.bodyLarge

        )

    }

}