package com.pdfpocket.lite.features.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pdfpocket.lite.R
import com.pdfpocket.lite.navigation.Routes

@Composable
fun ToolsScreen(onOpenTool: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.tab_tools),
                style = MaterialTheme.typography.headlineMedium
            )
        }
        item {
            ToolCard(
                icon = Icons.Default.Draw,
                title = stringResource(R.string.fill_sign_title),
                description = stringResource(R.string.tool_fill_sign_description),
                onClick = { onOpenTool(Routes.FILL_SIGN) }
            )
        }
        item {
            ToolCard(
                icon = Icons.Default.CallMerge,
                title = stringResource(R.string.action_merge),
                description = stringResource(R.string.tool_merge_description),
                onClick = { onOpenTool(Routes.MERGE) }
            )
        }
        item {
            ToolCard(
                icon = Icons.Default.ContentCut,
                title = stringResource(R.string.action_extract_pages),
                description = stringResource(R.string.tool_split_description),
                onClick = { onOpenTool(Routes.SPLIT) }
            )
        }
        item {
            ToolCard(
                icon = Icons.Default.AutoAwesomeMotion,
                title = stringResource(R.string.pages_title),
                description = stringResource(R.string.tool_pages_description),
                onClick = { onOpenTool(Routes.PAGES) }
            )
        }
        item {
            ToolCard(
                icon = Icons.Default.SwapHoriz,
                title = stringResource(R.string.convert_title),
                description = stringResource(R.string.tool_convert_description),
                onClick = { onOpenTool(Routes.CONVERT) }
            )
        }
        item {
            ToolCard(
                icon = Icons.Default.WaterDrop,
                title = stringResource(R.string.watermark_title),
                description = stringResource(R.string.tool_watermark_description),
                onClick = { onOpenTool(Routes.WATERMARK) }
            )
        }
        item {
            ToolCard(
                icon = Icons.Default.Image,
                title = stringResource(R.string.action_images_to_pdf),
                description = stringResource(R.string.tool_images_description),
                onClick = { onOpenTool(Routes.IMAGES_TO_PDF) }
            )
        }
        item {
            Text(
                text = stringResource(R.string.tools_coming_soon_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        item {
            Text(
                text = stringResource(R.string.tools_coming_soon_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ToolCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
