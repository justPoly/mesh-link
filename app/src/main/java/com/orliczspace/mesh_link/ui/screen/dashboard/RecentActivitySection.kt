package com.orliczspace.mesh_link.ui.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orliczspace.mesh_link.network.NetworkActivity
import com.orliczspace.mesh_link.network.NetworkActivityLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecentActivitySection() {

    val activities =
        NetworkActivityLog.events

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            text = "Recent Activity",
            style = MaterialTheme.typography.titleLarge
        )

        if (activities.isEmpty()) {
            EmptyActivityCard()
        } else {

            activities
                .take(5)
                .forEach { activity ->
                    ActivityCard(
                        activity = activity
                    )
                }
        }
    }
}

@Composable
private fun ActivityCard(
    activity: NetworkActivity
) {
    val icon =
        when {
            activity.message.contains(
                "Gateway",
                ignoreCase = true
            ) -> Icons.Default.Router

            activity.message.contains(
                "Internet",
                ignoreCase = true
            ) -> Icons.Default.Wifi

            activity.message.contains(
                "flow",
                ignoreCase = true
            ) -> Icons.Default.SwapHoriz

            activity.message.contains(
                "discovered",
                ignoreCase = true
            ) -> Icons.Default.WifiTethering

            else -> Icons.Default.History
        }

    val iconColor =
        when {
            activity.message.contains(
                "failed",
                ignoreCase = true
            ) -> Color(0xFFEF4444)

            activity.message.contains(
                "disconnected",
                ignoreCase = true
            ) -> Color(0xFFFFB020)

            else -> Color(0xFF60A5FA)
        }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF101D25),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(16.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = Color(0xFF182848),
                        shape = RoundedCornerShape(13.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor
                )
            }

            Spacer(
                modifier = Modifier.size(12.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = activity.message,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!activity.detail.isNullOrBlank()) {
                    Text(
                        text = activity.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(
                modifier = Modifier.size(8.dp)
            )

            Text(
                text = formatActivityTime(
                    activity.timestamp
                ),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun EmptyActivityCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF101D25),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(32.dp)
            )

            Spacer(
                modifier = Modifier.size(10.dp)
            )

            Text(
                text = "No recent activity",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )

            Spacer(
                modifier = Modifier.size(4.dp)
            )

            Text(
                text = "Network events will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

private fun formatActivityTime(
    timestamp: Long
): String {
    return SimpleDateFormat(
        "HH:mm",
        Locale.getDefault()
    ).format(
        Date(timestamp)
    )
}