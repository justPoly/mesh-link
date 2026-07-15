package com.orliczspace.mesh_link.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(

    primary = MeshGreen,

    secondary = MeshBlue,

    background = BackgroundDark,

    surface = SurfaceDark

)

private val LightColors = lightColorScheme(

    primary = MeshGreen,

    secondary = MeshBlue,

    background = BackgroundLight,

    surface = SurfaceLight

)

@Composable
fun MeshlinkTheme(

    darkTheme: Boolean = isSystemInDarkTheme(),

    content: @Composable () -> Unit

) {

    val colors =
        if (darkTheme) DarkColorScheme
        else LightColors

    val view = LocalView.current

    if (!view.isInEditMode) {

        SideEffect {

            val window = (view.context as Activity).window

            window.statusBarColor = colors.background.toArgb()

            WindowCompat
                .getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme

        }

    }

    MaterialTheme(

        colorScheme = colors,

        typography = Typography,

        shapes = Shapes,

        content = content

    )

}