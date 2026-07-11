package com.pdfpocket.lite.navigation

object Routes {
    const val HOME = "home"
    const val FILES = "files"
    const val TOOLS = "tools"
    const val SETTINGS = "settings"
    const val MERGE = "tools/merge"
    const val SPLIT = "tools/split"
    const val IMAGES_TO_PDF = "tools/images_to_pdf"
    const val PAGES = "tools/pages"
    const val CONVERT = "tools/convert"
    const val WATERMARK = "tools/watermark"
    const val FILL_SIGN = "tools/fill_sign"
    const val FILL_SIGN_PATTERN = "tools/fill_sign?uri={uri}"
    const val VIEWER = "viewer/{uri}"

    fun viewer(uriString: String): String = "viewer/" + android.net.Uri.encode(uriString)

    fun fillSign(uriString: String): String =
        "tools/fill_sign?uri=" + android.net.Uri.encode(uriString)
}
