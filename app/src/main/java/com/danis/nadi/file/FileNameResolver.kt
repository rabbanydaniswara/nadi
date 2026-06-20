package com.danis.nadi.file

object FileNameResolver {
    fun uniqueName(originalName: String, exists: (String) -> Boolean): String {
        val cleanName = originalName.trim().ifBlank { "file" }
        if (!exists(cleanName)) return cleanName

        val dotIndex = cleanName.lastIndexOf('.').takeIf { it > 0 && it < cleanName.lastIndex - 1 }
        val base = if (dotIndex != null) cleanName.substring(0, dotIndex) else cleanName
        val extension = if (dotIndex != null) cleanName.substring(dotIndex) else ""

        var index = 1
        while (true) {
            val candidate = "$base ($index)$extension"
            if (!exists(candidate)) return candidate
            index++
        }
    }
}
