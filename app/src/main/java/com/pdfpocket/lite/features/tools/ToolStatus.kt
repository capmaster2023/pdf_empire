package com.pdfpocket.lite.features.tools

import android.net.Uri

sealed interface ToolStatus {
    data object Idle : ToolStatus
    data object Working : ToolStatus
    data class Done(val output: Uri, val outputName: String) : ToolStatus
    data object Error : ToolStatus
}
