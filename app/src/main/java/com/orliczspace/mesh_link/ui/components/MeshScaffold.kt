package com.orliczspace.mesh_link.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.orliczspace.mesh_link.ui.theme.MeshDimens

@Composable
fun MeshScaffold(

    topBar: @Composable () -> Unit = {},

    bottomBar: @Composable () -> Unit = {},

    content: @Composable (PaddingValues) -> Unit

) {

    Scaffold(

        topBar = topBar,

        bottomBar = bottomBar

    ) { padding ->

        content(

            PaddingValues(

                top = padding.calculateTopPadding(),

                bottom = padding.calculateBottomPadding(),

                start = MeshDimens.ScreenPadding,

                end = MeshDimens.ScreenPadding

            )

        )

    }

}