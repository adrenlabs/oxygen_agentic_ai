#include "gguf_reader.h"

#include <cstdio>
#include <cstring>
#include <fstream>
#include <sstream>

namespace oxygen {
namespace {

enum class GgufType : uint32_t {
    UINT8 = 0,
    INT8 = 1,
    UINT16 = 2,
    INT16 = 3,
    UINT32 = 4,
    INT32 = 5,
    FLOAT32 = 6,
    BOOL = 7,
    STRING = 8,
    ARRAY = 9,
    UINT64 = 10,
    INT64 = 11,
    FLOAT64 = 12
};

class Reader {
public:
    explicit Reader(const std::string& path) : in(path, std::ios::binary) {}

    bool good() const { return static_cast<bool>(in); }

    template <typename T>
    bool readPod(T& out) {
        in.read(reinterpret_cast<char*>(&out), sizeof(T));
        return static_cast<bool>(in);
    }

    bool readString(std::string& out) {
        uint64_t n = 0;
        if (!readPod(n)) return false;
        if (n > 16ull * 1024ull * 1024ull) return false;
        out.assign(static_cast<size_t>(n), '\0');
        if (n > 0) {
            in.read(out.data(), static_cast<std::streamsize>(n));
        }
        return static_cast<bool>(in);
    }

    bool skipValue(GgufType type);

private:
    std::ifstream in;
};

bool Reader::skipValue(GgufType type) {
    switch (type) {
        case GgufType::UINT8:
        case GgufType::INT8:
        case GgufType::BOOL: {
            uint8_t v{};
            return readPod(v);
        }
        case GgufType::UINT16:
        case GgufType::INT16: {
            uint16_t v{};
            return readPod(v);
        }
        case GgufType::UINT32:
        case GgufType::INT32:
        case GgufType::FLOAT32: {
            uint32_t v{};
            return readPod(v);
        }
        case GgufType::UINT64:
        case GgufType::INT64:
        case GgufType::FLOAT64: {
            uint64_t v{};
            return readPod(v);
        }
        case GgufType::STRING: {
            std::string s;
            return readString(s);
        }
        case GgufType::ARRAY: {
            uint32_t elemType = 0;
            uint64_t n = 0;
            if (!readPod(elemType) || !readPod(n)) return false;
            if (n > 10ull * 1000ull * 1000ull) return false;
            for (uint64_t i = 0; i < n; ++i) {
                if (!skipValue(static_cast<GgufType>(elemType))) return false;
            }
            return true;
        }
        default:
            return false;
    }
}

std::string jsonEscape(const std::string& in) {
    std::string out;
    out.reserve(in.size() + 8);
    for (unsigned char c : in) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (c < 0x20) {
                    char buf[8];
                    std::snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out += static_cast<char>(c);
                }
        }
    }
    return out;
}

std::string valueToString(Reader& r, GgufType type, bool& ok) {
    ok = true;
    switch (type) {
        case GgufType::UINT8: {
            uint8_t v{};
            ok = r.readPod(v);
            return ok ? std::to_string(v) : "";
        }
        case GgufType::INT8: {
            int8_t v{};
            ok = r.readPod(v);
            return ok ? std::to_string(v) : "";
        }
        case GgufType::UINT16: {
            uint16_t v{};
            ok = r.readPod(v);
            return ok ? std::to_string(v) : "";
        }
        case GgufType::INT16: {
            int16_t v{};
            ok = r.readPod(v);
            return ok ? std::to_string(v) : "";
        }
        case GgufType::UINT32: {
            uint32_t v{};
            ok = r.readPod(v);
            return ok ? std::to_string(v) : "";
        }
        case GgufType::INT32: {
            int32_t v{};
            ok = r.readPod(v);
            return ok ? std::to_string(v) : "";
        }
        case GgufType::FLOAT32: {
            float v{};
            ok = r.readPod(v);
            return ok ? std::to_string(v) : "";
        }
        case GgufType::BOOL: {
            uint8_t v{};
            ok = r.readPod(v);
            return ok ? (v ? "true" : "false") : "";
        }
        case GgufType::STRING: {
            std::string s;
            ok = r.readString(s);
            return s;
        }
        case GgufType::UINT64: {
            uint64_t v{};
            ok = r.readPod(v);
            return ok ? std::to_string(v) : "";
        }
        case GgufType::INT64: {
            int64_t v{};
            ok = r.readPod(v);
            return ok ? std::to_string(v) : "";
        }
        case GgufType::FLOAT64: {
            double v{};
            ok = r.readPod(v);
            return ok ? std::to_string(v) : "";
        }
        case GgufType::ARRAY: {
            uint32_t elemType = 0;
            uint64_t n = 0;
            if (!r.readPod(elemType) || !r.readPod(n)) {
                ok = false;
                return "";
            }
            // Skip array contents; chat templates are strings, not arrays.
            for (uint64_t i = 0; i < n; ++i) {
                if (!r.skipValue(static_cast<GgufType>(elemType))) {
                    ok = false;
                    return "";
                }
            }
            return "[array:" + std::to_string(n) + "]";
        }
        default:
            ok = false;
            return "";
    }
}

}  // namespace

GgufInfo readGguf(const std::string& path) {
    GgufInfo info;
    Reader r(path);
    if (!r.good()) {
        info.error = "Unable to open file";
        return info;
    }
    char magic[4];
    if (!r.readPod(magic) || std::memcmp(magic, "GGUF", 4) != 0) {
        info.error = "Not a GGUF file";
        return info;
    }
    if (!r.readPod(info.version)) {
        info.error = "Truncated header";
        return info;
    }
    if (!r.readPod(info.tensorCount) || !r.readPod(info.kvCount)) {
        info.error = "Truncated counts";
        return info;
    }
    if (info.kvCount > 4096) {
        info.error = "Unreasonable metadata count";
        return info;
    }
    for (uint64_t i = 0; i < info.kvCount; ++i) {
        std::string key;
        uint32_t typeRaw = 0;
        if (!r.readString(key) || !r.readPod(typeRaw)) {
            info.error = "Truncated metadata key";
            return info;
        }
        bool ok = false;
        std::string value = valueToString(r, static_cast<GgufType>(typeRaw), ok);
        if (!ok) {
            info.error = "Truncated metadata value for " + key;
            return info;
        }
        GgufKv kv{key, std::to_string(typeRaw), value};
        if (info.kvs.size() < 256) info.kvs.push_back(kv);

        auto setIf = [&](const char* suffix, auto setter) {
            if (key.size() >= std::strlen(suffix) &&
                key.compare(key.size() - std::strlen(suffix), std::strlen(suffix), suffix) == 0) {
                setter(value);
            }
        };
        if (key == "general.architecture") info.architecture = value;
        if (key == "general.name") info.name = value;
        if (key == "general.quantization_version") {
            // ignore
        }
        if (key.find("file_type") != std::string::npos) info.quantization = value;
        setIf(".context_length", [&](const std::string& v) {
            try { info.contextLength = std::stoll(v); } catch (...) {}
        });
        setIf(".embedding_length", [&](const std::string& v) {
            try { info.embeddingLength = std::stoll(v); } catch (...) {}
        });
        setIf(".block_count", [&](const std::string& v) {
            try { info.blockCount = std::stoll(v); } catch (...) {}
        });
        setIf(".attention.head_count", [&](const std::string& v) {
            try { info.headCount = std::stoll(v); } catch (...) {}
        });
        if (key == "tokenizer.chat_template") info.chatTemplate = value;
    }
    info.ok = true;
    return info;
}

std::string ggufInfoToJson(const GgufInfo& info) {
    std::ostringstream o;
    o << "{"
      << "\"ok\":" << (info.ok ? "true" : "false") << ","
      << "\"error\":\"" << jsonEscape(info.error) << "\","
      << "\"version\":" << info.version << ","
      << "\"tensorCount\":" << info.tensorCount << ","
      << "\"kvCount\":" << info.kvCount << ","
      << "\"architecture\":\"" << jsonEscape(info.architecture) << "\","
      << "\"name\":\"" << jsonEscape(info.name) << "\","
      << "\"contextLength\":" << info.contextLength << ","
      << "\"embeddingLength\":" << info.embeddingLength << ","
      << "\"blockCount\":" << info.blockCount << ","
      << "\"headCount\":" << info.headCount << ","
      << "\"quantization\":\"" << jsonEscape(info.quantization) << "\","
      << "\"chatTemplate\":\"" << jsonEscape(info.chatTemplate) << "\""
      << "}";
    return o.str();
}

}  // namespace oxygen
