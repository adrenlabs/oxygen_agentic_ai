# MODEL_MANAGER.md

`ModelManager` owns discovery, import, download, checksum, load/unload, rename, favorite, delete.

## Sources

- Android Storage Access Framework
- Generic HTTPS (user-started; never automatic multi-GB downloads)
- Google Drive (copy then import)
- Hugging Face URLs the user pastes

No single model URL is hardcoded.

## Profile fields

`modelId displayName runtime contextLimit recommendedContext quantization chatTemplate thinkingSupport toolCallingSupport streamingSupport multimodalSupport defaultGenerationConfig`

Profiles let you swap Qwen3-4B, Qwen3-8B, Gemma, Phi, DeepSeek, or other GGUF files without touching Agent Core.

## Integrity

SHA-256 is stored. Optional expected hash on download. Tiny / non-GGUF files are rejected as corrupted.

Model binaries are gitignored.
