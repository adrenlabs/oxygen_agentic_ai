# Third-party source

OXYGEN can build against a locally vendored copy of `llama.cpp`.

Expected path:

`third_party/llama.cpp/`

The CMake option is:

`-DOXYGEN_LLAMA_SOURCE_DIR=<absolute-or-build-relative-path>`

When this directory contains `include/llama.h`, OXYGEN links the local source tree. Otherwise CMake fetches the pinned upstream tag configured by `oxygen.llama.cpp.tag` (default `b5210`) from the official `ggml-org/llama.cpp` repository.

Do not commit a multi-hundred-megabyte or gigabyte model into this repository. Models are imported separately through the OXYGEN Model Manager.
