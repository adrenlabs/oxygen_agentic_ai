#!/usr/bin/env python3
"""Static archive/project integrity checks that require no network."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
required = [
    root / "settings.gradle.kts",
    root / "build.gradle.kts",
    root / "gradle" / "libs.versions.toml",
    root / "app" / "build.gradle.kts",
    root / "app" / "src" / "main" / "AndroidManifest.xml",
    root / "app" / "src" / "main" / "cpp" / "CMakeLists.txt",
    root / "app" / "src" / "main" / "cpp" / "oxygen_jni.cpp",
    root / "app" / "src" / "main" / "cpp" / "oxygen_inference.cpp",
]
missing = [str(p.relative_to(root)) for p in required if not p.exists()]
if missing:
    print("Missing required project files:")
    print("\n".join(missing))
    sys.exit(1)

java_root = root / "app" / "src" / "main" / "java"
sources = list(java_root.rglob("*.kt"))
if not sources:
    print("No Kotlin sources found")
    sys.exit(1)

for path in sources:
    text = path.read_text(encoding="utf-8")
    if "TODO: implement" in text or "throw NotImplementedError" in text:
        print(f"Unimplemented marker found: {path.relative_to(root)}")
        sys.exit(1)

native = (root / "app" / "src" / "main" / "cpp" / "oxygen_inference.cpp").read_text(encoding="utf-8")
for bad in ("defined(llama_sampler_sample)", "defined(llama_tokenize)", "defined(llama_model_load_from_file)"):
    if bad in native:
        print(f"Outdated function-detection guard remains: {bad}")
        sys.exit(1)

manifest = (root / "app" / "src" / "main" / "AndroidManifest.xml").read_text(encoding="utf-8")
if 'package=' in manifest:
    print("Warning: namespace should be defined in Gradle, not the manifest.")

print(f"Static verification passed: {len(sources)} Kotlin source files checked.")
