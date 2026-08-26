package com.oxygen.ai.core.error

/**
 * Typed application errors. User-facing copy is separate from developer detail.
 */
sealed class OxygenError(
    val code: String,
    val userMessage: String,
    val developerMessage: String,
    cause: Throwable? = null,
) : Exception(developerMessage, cause) {

    fun toUserFacing(): UserFacingError = UserFacingError(code, userMessage)

    class ModelNotFound(path: String) : OxygenError(
        "MODEL_NOT_FOUND",
        "No local model is available. Import a GGUF file to start chatting.",
        "Model not found at $path",
    )

    class ModelCorrupted(path: String, detail: String) : OxygenError(
        "MODEL_CORRUPTED",
        "This model file looks damaged and cannot be loaded.",
        "Corrupt GGUF $path: $detail",
    )

    class ModelLoadFailed(path: String, detail: String) : OxygenError(
        "MODEL_LOAD_FAILED",
        "The model could not be loaded on this device.",
        "Load failed for $path: $detail",
    )

    class InferenceFailed(detail: String) : OxygenError(
        "INFERENCE_FAILED",
        "Generation stopped because the local model encountered an error.",
        detail,
    )

    class ContextOverflow(used: Int, limit: Int) : OxygenError(
        "CONTEXT_OVERFLOW",
        "This conversation is too large for the current context window.",
        "Context used=$used limit=$limit",
    )

    class OutOfMemoryRisk(detail: String) : OxygenError(
        "OOM_RISK",
        "This device does not have enough free memory for that model or context size.",
        detail,
    )

    class MemoryReadFailed(detail: String) : OxygenError(
        "MEMORY_READ_FAILED",
        "Saved memories could not be read.",
        detail,
    )

    class MemoryWriteFailed(detail: String) : OxygenError(
        "MEMORY_WRITE_FAILED",
        "A memory could not be saved.",
        detail,
    )

    class RagIndexFailed(detail: String) : OxygenError(
        "RAG_INDEX_FAILED",
        "The document could not be indexed.",
        detail,
    )

    class PdfExtractionFailed(detail: String) : OxygenError(
        "PDF_EXTRACTION_FAILED",
        "Text could not be extracted from this PDF.",
        detail,
    )

    class EmbeddingFailed(detail: String) : OxygenError(
        "EMBEDDING_FAILED",
        "Document embeddings could not be created.",
        detail,
    )

    class McpConnectionFailed(endpoint: String, detail: String) : OxygenError(
        "MCP_CONNECTION_FAILED",
        "The MCP server could not be reached.",
        "$endpoint: $detail",
    )

    class McpToolFailed(tool: String, detail: String) : OxygenError(
        "MCP_TOOL_FAILED",
        "A tool call failed.",
        "$tool: $detail",
    )

    class SearchFailed(detail: String) : OxygenError(
        "SEARCH_FAILED",
        "Web search is unavailable right now.",
        detail,
    )

    class TelegramFailed(detail: String) : OxygenError(
        "TELEGRAM_FAILED",
        "Telegram could not complete that request.",
        detail,
    )

    class DriveSyncFailed(detail: String) : OxygenError(
        "DRIVE_SYNC_FAILED",
        "Google Drive sync could not finish.",
        detail,
    )

    class AuthenticationFailed(detail: String) : OxygenError(
        "AUTH_FAILED",
        "Sign-in was cancelled or rejected.",
        detail,
    )

    class StorageInsufficient(neededBytes: Long, freeBytes: Long) : OxygenError(
        "STORAGE_INSUFFICIENT",
        "There is not enough free storage for that file.",
        "needed=$neededBytes free=$freeBytes",
    )

    class PermissionDenied(action: String) : OxygenError(
        "PERMISSION_DENIED",
        "That action is not allowed with the current tool permissions.",
        action,
    )

    class Cancelled : OxygenError(
        "CANCELLED",
        "The task was cancelled.",
        "Cancelled by user or timeout",
    )

    class LimitReached(limit: String) : OxygenError(
        "LIMIT_REACHED",
        "The agent stopped because a safety limit was reached.",
        limit,
    )

    class Offline(feature: String) : OxygenError(
        "OFFLINE",
        "This feature needs a network connection.",
        "Offline: $feature",
    )

    class Validation(detail: String) : OxygenError(
        "VALIDATION",
        "That input is not valid.",
        detail,
    )
}

data class UserFacingError(
    val code: String,
    val message: String,
)
