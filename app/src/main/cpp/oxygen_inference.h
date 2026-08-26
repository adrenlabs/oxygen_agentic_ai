#pragma once

#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

namespace oxygen {

struct LoadParams {
    int nCtx = 4096;
    int nThreads = 4;
    int nBatch = 256;
    int nGpuLayers = 0;
    bool useMmap = true;
    bool useMlock = false;
    float ropeFreqBase = 0.f;
    float yarnExtFactor = -1.f;
    float yarnAttnFactor = 1.f;
    int yarnOrigCtx = 0;
};

struct GenParams {
    int maxTokens = 512;
    float temperature = 0.7f;
    int topK = 40;
    float topP = 0.9f;
    float minP = 0.05f;
    float repeatPenalty = 1.08f;
    int seed = -1;
    std::vector<std::string> stop;
};

class TokenSink {
public:
    virtual ~TokenSink() = default;
    virtual bool onToken(const std::string& token) = 0;
    virtual void onMetrics(int promptTokens, int generatedTokens, double tokensPerSecond) = 0;
    virtual void onError(const std::string& message) = 0;
    virtual void onDone(bool cancelled) = 0;
};

class InferenceBackend {
public:
    virtual ~InferenceBackend() = default;
    virtual bool available() const = 0;
    virtual std::string load(const std::string& path, const LoadParams& params, std::string& error) = 0;
    virtual bool unload(const std::string& handle, std::string& error) = 0;
    virtual bool generate(
        const std::string& handle,
        const std::string& prompt,
        const GenParams& params,
        TokenSink& sink,
        std::atomic<bool>& cancelFlag
    ) = 0;
    virtual std::string status(const std::string& handle) const = 0;
};

std::unique_ptr<InferenceBackend> createBackend();

}  // namespace oxygen
