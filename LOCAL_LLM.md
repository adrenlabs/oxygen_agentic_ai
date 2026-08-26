# LOCAL_LLM.md

## Engine

`llama.cpp` compiled by CMake / NDK into `liboxygen_inference.so`.

Kotlin contracts:

- `InferenceEngine`
- `ModelRuntime`
- `ModelSession`
- `GenerationConfig`
- `GenerationEvent`
- `GenerationResult`

## JNI

`LlamaJni` is the only class that talks to native code. The UI never sees pointers.

Capabilities: GGUF load, metadata, init/unload, streaming tokens (Kotlin `Flow`), cancel, context size, temperature, top-k/p, min-p, repetition penalty, seed, stop sequences, chat templates, status, metrics, model switch, errors.

All native work runs off the main thread.

## GGUF metadata

Two readers exist:

1. Native `gguf_reader.cpp`
2. Pure Kotlin `GgufMetadataReader` (unit-tested)

Model Manager can inspect a file before loading weights.

## Default model

`Qwen3-4B-Q4_K_M.gguf` — **not** packaged in the APK.

Native context 32,768. YaRN up to 131,072 only when the user opts in and the Context Engine needs it. CPU is the default backend; the JNI load path accepts `n_gpu_layers` for future acceleration.

If no model is loaded, generation fails with `ModelNotFound`. Nothing is faked.
