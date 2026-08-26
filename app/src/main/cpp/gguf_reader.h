#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace oxygen {

struct GgufKv {
    std::string key;
    std::string type;
    std::string value;
};

struct GgufInfo {
    bool ok = false;
    std::string error;
    uint32_t version = 0;
    uint64_t tensorCount = 0;
    uint64_t kvCount = 0;
    std::string architecture;
    std::string name;
    int64_t contextLength = 0;
    int64_t embeddingLength = 0;
    int64_t blockCount = 0;
    int64_t headCount = 0;
    std::string chatTemplate;
    std::string quantization;
    std::vector<GgufKv> kvs;
};

GgufInfo readGguf(const std::string& path);

std::string ggufInfoToJson(const GgufInfo& info);

}  // namespace oxygen
