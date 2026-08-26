package com.oxygen.ai.security

import java.io.File

object PathSafety {
    fun sanitizeFileName(raw: String): String {
        val trimmed = raw.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = trimmed.replace(Regex("[^A-Za-z0-9._\\- ]"), "_").trim()
        return cleaned.ifBlank { "file" }.take(180)
    }

    fun assertInside(root: File, candidate: File) {
        val rootPath = root.canonicalFile
        val child = candidate.canonicalFile
        val prefix = rootPath.path.let { if (it.endsWith("/")) it else "$it/" }
        require(child.path == rootPath.path || child.path.startsWith(prefix)) {
            "Path escapes storage root: ${child.path}"
        }
    }

    fun isOversized(sizeBytes: Long, maxBytes: Long): Boolean = sizeBytes > maxBytes
}
