# BUILD.md

## Prerequisites

- Android Studio Ladybug / Koala or newer
- JDK 17 (Android Studio bundled JDK is fine)
- Android SDK 35
- NDK 27.0.12077973 (or the side-by-side NDK Studio offers; update `ndkVersion` if needed)
- CMake 3.22.1
- Git (CMake FetchContent clones `llama.cpp`)
- An ARM64 Android device or emulator with **≥ 6 GB RAM** recommended for Qwen3-4B Q4_K_M

This project does **not** require Node.js, Python, Termux, Expo, React Native, or Flutter.

## Import

1. Unzip `OXYGEN_AI_Source.zip`.
2. Copy `local.properties.example` → `local.properties`.
3. Set `sdk.dir` to your SDK (never commit this file).
4. Android Studio → **Open** the `OXYGEN_AI` directory.
5. Let Gradle sync.

## Gradle commands

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Release signing: create `keystore.properties` (gitignored) with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Without it, `assembleRelease` still compiles and packages an unsigned/debug-signed preview depending on Studio, but a Play-ready keystore is expected for distribution.

## Native / llama.cpp

`app/src/main/cpp/CMakeLists.txt` FetchContents [llama.cpp](https://github.com/ggerganov/llama.cpp) at the tag in `gradle.properties`:

```
oxygen.enableLlama=true
oxygen.llama.cpp.tag=b5210
```

The first native build downloads and compiles llama.cpp for `arm64-v8a`. This takes several minutes.

If you only want to iterate on Kotlin/UI and have no NDK:

```
oxygen.enableLlama=false
```

Metadata import still works (Kotlin + native GGUF readers). Generation will report that llama.cpp was not compiled in. **Do not ship that configuration as the production agent.**

## Installing a local model

Models are **not** inside the APK.

1. Download `Qwen3-4B-Q4_K_M.gguf` (or another compatible GGUF) from Hugging Face or copy from storage / Drive.
2. Open **Models → Import GGUF**.
3. Tap the card to **load** it.

Qwen3-4B native context is 32,768 tokens. Extended YaRN (up to 131,072) is used only when the user enables it **and** the Context Engine actually needs it.

## ABI

Release/debug `abiFilters` is `arm64-v8a` only. Physical-device validation target is ARM64.

## Tests

- Unit tests: `app/src/test` (context budget, ranking, memory merge, chunking, embeddings, reasoning, planner, MCP/tool parse, search parse, Telegram router, Drive conflicts, GGUF header, calculator, injection defense).
- Instrumentation: `app/src/androidTest` (home + chat navigation).

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest   # device required
```

## GitHub Actions

`.github/workflows/android-build.yml` builds the Debug APK on `ubuntu-latest`:

1. JDK 17
2. Android SDK + platform 35
3. NDK `27.0.12077973` and CMake `3.22.1`
4. `./gradlew assembleDebug --stacktrace`
5. `./gradlew testDebugUnitTest`
6. Upload `app/build/outputs/apk/debug/*.apk` as `oxygen-debug-apk`

The first native configure downloads llama.cpp `b5210` (CMake FetchContent). That step needs network and several minutes.

## Troubleshooting

See [TROUBLESHOOTING.md](TROUBLESHOOTING.md).
