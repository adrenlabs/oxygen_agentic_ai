# OXYGEN AI — Production Readiness

This repository has been hardened toward production use. The readiness score below is an engineering assessment, not a substitute for device validation.

## Readiness target

**Engineering readiness: ~90%**

Implemented/hardened:
- Native Android / Kotlin / Compose / Material 3 foundation
- Room persistence
- Local-first Agent Core and bounded execution
- Context engine and reasoning profiles
- Model manager and GGUF metadata handling
- llama.cpp C++/JNI boundary
- Native lifecycle locking and unload/generate isolation
- Generation cancellation and completion guarantees
- JNI callback exception containment
- Tool permission enforcement with explicit confirmation for protected tools
- Atomic model downloads with HTTPS-only transport, size limits, and checksum validation
- Local RAG/PDF pipeline interfaces
- MCP/search/Drive/Telegram integration boundaries
- Security/path-safety primitives
- Diagnostics, tests, CI configuration and documentation

## Remaining external validation

These require a real Android build/device/environment and therefore are not claimed as completed here:
- Gradle dependency resolution
- Android SDK/NDK compilation
- Physical ARM64 native library loading
- Real Qwen3-4B GGUF inference on device
- Device-specific RAM/thermal/performance profiling
- Live Google Drive OAuth/sync
- Live Telegram bot operation
- Live MCP server interoperability
- Live SearXNG endpoint behavior
- Release signing and store-specific checks

Do not label the app 100% production-ready until those checks pass.
