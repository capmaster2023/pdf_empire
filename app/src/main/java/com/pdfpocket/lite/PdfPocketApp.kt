package com.pdfpocket.lite

import android.app.Application
import com.pdfpocket.lite.data.DocumentRepository
import com.pdfpocket.lite.data.SettingsRepository
import com.pdfpocket.lite.data.db.AppDatabase
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Conteneur d'injection de dépendances manuel (léger et fiable pour la v0.1).
 * Hilt pourra être introduit dans une étape ultérieure sans changer l'architecture.
 */
class AppContainer(val app: Application) {
    val database: AppDatabase by lazy { AppDatabase.build(app) }
    val documents: DocumentRepository by lazy { DocumentRepository(app, database.documentDao()) }
    val settings: SettingsRepository by lazy { SettingsRepository(app) }
}

class PdfPocketApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
    }
}
