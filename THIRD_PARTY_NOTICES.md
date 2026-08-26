# THIRD_PARTY_NOTICES.md

OXYGEN AI does not claim ownership of third-party libraries. Licenses below were the current public licenses when this project was assembled. Verify each artifact’s license at release time.

| Package | Version | License | Repository |
| --- | --- | --- | --- |
| Android Gradle Plugin | 8.7.3 | Apache-2.0 | https://developer.android.com/studio |
| Kotlin | 2.0.21 | Apache-2.0 | https://github.com/JetBrains/kotlin |
| KSP | 2.0.21-1.0.28 | Apache-2.0 | https://github.com/google/ksp |
| AndroidX Core / AppCompat / Lifecycle / Activity / Navigation / Room / Work / DataStore / Security Crypto / DocumentFile | see `gradle/libs.versions.toml` | Apache-2.0 | https://github.com/androidx/androidx |
| Jetpack Compose BOM | 2024.12.01 | Apache-2.0 | https://developer.android.com/jetpack/compose |
| Material 3 + Adaptive Navigation Suite | BOM / 1.0.0 adaptive | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-material3 |
| OkHttp | 4.12.0 | Apache-2.0 | https://github.com/square/okhttp |
| kotlinx.coroutines | 1.9.0 | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| kotlinx.serialization | 1.7.3 | Apache-2.0 | https://github.com/Kotlin/kotlinx.serialization |
| kotlinx.datetime | 0.6.1 | Apache-2.0 | https://github.com/Kotlin/kotlinx-datetime |
| PdfBox-Android (`com.tom-roush:pdfbox-android`) | 2.0.27.0 | Apache-2.0 | https://github.com/TomRoush/PdfBox-Android |
| Apache PDFBox (upstream) | 2.0.27 | Apache-2.0 | https://pdfbox.apache.org |
| Bouncy Castle (transitive via PdfBox-Android) | (transitive) | MIT | https://www.bouncycastle.org |
| llama.cpp (FetchContent, tag `b5210`) | git tag | MIT | https://github.com/ggerganov/llama.cpp |
| ggml (via llama.cpp) | (bundled) | MIT | https://github.com/ggerganov/ggml |
| JUnit | 4.13.2 | EPL-1.0 | https://junit.org |
| AndroidX Test / Espresso | 1.2.1 / 3.6.1 | Apache-2.0 | https://developer.android.com/training/testing |
| Robolectric | 4.14.1 | Apache-2.0 | https://github.com/robolectric/robolectric |

## Models

Qwen, Gemma, Phi, DeepSeek, and any other GGUF you import remain under **their** model licenses (typically Apache-2.0, custom, or research-only). OXYGEN does not redistribute weights.

## Attribution notes

- PdfBox-Android is a port of Apache PDFBox; include Apache-2.0 NOTICE if you distribute binaries that link it.
- llama.cpp is MIT; include its copyright in binary distributions.
- Do **not** copy ChatterUI or any other third-party app source into this tree.

This file is the attribution record required by the master specification.
