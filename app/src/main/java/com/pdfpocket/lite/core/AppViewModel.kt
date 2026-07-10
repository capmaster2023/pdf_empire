package com.pdfpocket.lite.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pdfpocket.lite.AppContainer
import com.pdfpocket.lite.PdfPocketApp

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline builder: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as PdfPocketApp).container
    return viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return builder(container) as T
        }
    })
}
