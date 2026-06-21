package com.danis.nadi.network.server

import java.io.File

object BrowserClientAssets {
    const val HTML_FILE_NAME = "browser-client.html"
    const val CSS_FILE_NAME = "browser-client.css"
    const val JS_FILE_NAME = "browser-client.js"
    const val FILE_NAME = HTML_FILE_NAME

    fun html(): String {
        return asset(HTML_FILE_NAME)?.content
            ?: error("Missing browser client asset: $HTML_FILE_NAME")
    }

    fun asset(fileName: String): BrowserClientAsset? {
        if (fileName !in ASSET_FILE_NAMES) return null
        val content = classpathAsset(fileName) ?: sourceAsset(fileName) ?: return null
        return BrowserClientAsset(
            fileName = fileName,
            mimeType = mimeType(fileName),
            content = content
        )
    }

    fun mimeType(fileName: String): String {
        return when (fileName) {
            HTML_FILE_NAME -> "text/html; charset=utf-8"
            CSS_FILE_NAME -> "text/css; charset=utf-8"
            JS_FILE_NAME -> "application/javascript; charset=utf-8"
            else -> "application/octet-stream"
        }
    }

    private fun classpathAsset(fileName: String): String? {
        return javaClass.classLoader
            ?.getResourceAsStream(fileName)
            ?.bufferedReader()
            ?.use { it.readText() }
    }

    private fun sourceAsset(fileName: String): String? {
        return sourceAssetPaths(fileName)
            .map(::File)
            .firstOrNull { it.isFile }
            ?.readText()
    }

    private fun sourceAssetPaths(fileName: String): List<String> {
        return listOf(
            "app/src/main/assets/$fileName",
            "src/main/assets/$fileName"
        )
    }

    val ASSET_FILE_NAMES = setOf(
        HTML_FILE_NAME,
        CSS_FILE_NAME,
        JS_FILE_NAME
    )
}

data class BrowserClientAsset(
    val fileName: String,
    val mimeType: String,
    val content: String
)
