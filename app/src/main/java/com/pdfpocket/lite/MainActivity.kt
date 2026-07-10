package com.pdfpocket.lite

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.pdfpocket.lite.data.ThemeMode
import com.pdfpocket.lite.navigation.AppRoot
import com.pdfpocket.lite.ui.theme.PdfPocketTheme

class MainActivity : ComponentActivity() {

    private val pendingPdfUri = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingPdfUri.value = extractPdfUri(intent)

        setContent {
            val app = application as PdfPocketApp
            val themeMode by app.container.settings.themeMode
                .collectAsState(initial = ThemeMode.SYSTEM)
            PdfPocketTheme(themeMode = themeMode) {
                AppRoot(
                    pendingPdfUri = pendingPdfUri.value,
                    onPendingConsumed = { pendingPdfUri.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractPdfUri(intent)?.let { pendingPdfUri.value = it }
    }

    private fun extractPdfUri(intent: Intent?): String? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.toString()
            Intent.ACTION_SEND -> {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.toString()
            }
            else -> null
        }
    }
}
