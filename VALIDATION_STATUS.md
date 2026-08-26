# Validation status

This tree is a compile-repair of the existing OXYGEN AI architecture. It is not a rewrite.

## Compile repairs applied

- Restored missing `com.oxygen.ai.models.ModelManager` (package was referenced everywhere, file was absent on GitHub).
- ChatViewModel conversation collector now uses `messageJob` (it previously referenced undeclared `collectJob`).
- Generation/unload isolation uses `LlamaCppRuntime.withGenerationLock()` — the lock stays on the runtime that owns native session lifetime (the engine no longer references a non-existent `lifecycleLock`).
- `ModelManager.load` returns a non-null `ModelSession`.
- `StorageInsufficient` call sites match the `(neededBytes, freeBytes)` constructor.
- GitHub Actions installs NDK 27 + CMake and builds `assembleDebug`.
- CMake fetches llama.cpp `b5210` from the official tag archive (not a mock).
- Debug APK applicationId is `com.oxygen.ai` (no `.debug` suffix).

## Verified in this environment

- Source-level consistency of the repaired APIs.
- Required Gradle / Android / CMake / JNI project structure.

## Not verified here (no Android SDK/NDK on the repair machine)

- Gradle dependency resolution
- `assembleDebug` / `assembleRelease` on-device
- CMake compilation of llama.cpp
- JNI load on physical ARM64
- Real GGUF inference

Push this tree to GitHub and let **Android Build** produce the Debug APK artifact.
