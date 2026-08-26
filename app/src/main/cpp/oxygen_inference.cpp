#include "oxygen_inference.h"

#include <algorithm>
#include <chrono>
#include <mutex>
#include <unordered_map>
#include <vector>

#if defined(OXYGEN_HAS_LLAMA) && OXYGEN_HAS_LLAMA
#if defined(__has_include)
#if __has_include("llama.h")
#include "llama.h"
#define OXYGEN_LLAMA_INCLUDED 1
#elif __has_include(<llama.h>)
#include <llama.h>
#define OXYGEN_LLAMA_INCLUDED 1
#endif
#endif
#ifndef OXYGEN_LLAMA_INCLUDED
#define OXYGEN_LLAMA_INCLUDED 0
#endif
#else
#define OXYGEN_LLAMA_INCLUDED 0
#endif

namespace oxygen {
namespace {

#if OXYGEN_LLAMA_INCLUDED

struct Session {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    std::string path;
    int nCtx = 0;
    int nThreads = 0;
    std::mutex generationMu;
};

class LlamaBackend final : public InferenceBackend {
public:
    LlamaBackend() {
        llama_backend_init();
    }

    ~LlamaBackend() override {
        std::lock_guard<std::mutex> lock(mu);
        for (auto& kv : sessions) {
            freeSession(kv.second);
        }
        sessions.clear();
        llama_backend_free();
    }

    bool available() const override { return true; }

    std::string load(const std::string& path, const LoadParams& params, std::string& error) override {
        llama_model_params mparams = llama_model_default_params();
        mparams.n_gpu_layers = params.nGpuLayers;
        mparams.use_mmap = params.useMmap;
        mparams.use_mlock = params.useMlock;

        llama_model* model = llama_model_load_from_file(path.c_str(), mparams);
        if (!model) {
            error = "Failed to load GGUF model";
            return {};
        }

        llama_context_params cparams = llama_context_default_params();
        cparams.n_ctx = params.nCtx;
        cparams.n_batch = params.nBatch;
        cparams.n_threads = params.nThreads;
        cparams.n_threads_batch = params.nThreads;
        if (params.yarnOrigCtx > 0) {
#ifdef LLAMA_ROPE_SCALING_TYPE_YARN
            cparams.rope_scaling_type = LLAMA_ROPE_SCALING_TYPE_YARN;
#endif
            cparams.yarn_ext_factor = params.yarnExtFactor;
            cparams.yarn_attn_factor = params.yarnAttnFactor;
            cparams.yarn_orig_ctx = params.yarnOrigCtx;
        }
        if (params.ropeFreqBase > 0.f) {
            cparams.rope_freq_base = params.ropeFreqBase;
        }

        llama_context* ctx = llama_init_from_model(model, cparams);
        if (!ctx) {
            llama_model_free(model);
            error = "Failed to create llama context";
            return {};
        }

        auto session = std::make_unique<Session>();
        session->model = model;
        session->ctx = ctx;
        session->path = path;
        session->nCtx = params.nCtx;
        session->nThreads = params.nThreads;

        std::string handle = "mdl-" + std::to_string(++seq);
        std::lock_guard<std::mutex> lock(mu);
        sessions[handle] = std::move(session);
        return handle;
    }

    bool unload(const std::string& handle, std::string& error) override {
        std::unique_ptr<Session> victim;
        {
            std::lock_guard<std::mutex> lock(mu);
            auto it = sessions.find(handle);
            if (it == sessions.end()) {
                error = "Unknown model handle";
                return false;
            }
            victim = std::move(it->second);
            sessions.erase(it);
        }
        std::lock_guard<std::mutex> generationLock(victim->generationMu);
        freeSession(victim);
        return true;
    }

    bool generate(
        const std::string& handle,
        const std::string& prompt,
        const GenParams& params,
        TokenSink& sink,
        std::atomic<bool>& cancelFlag
    ) override {
        Session* session = nullptr;
        {
            std::lock_guard<std::mutex> lock(mu);
            auto it = sessions.find(handle);
            if (it == sessions.end()) {
                sink.onError("Unknown model handle");
                sink.onDone(false);
                return false;
            }
            session = it->second.get();
        }

        std::unique_lock<std::mutex> generationLock(session->generationMu, std::try_to_lock);
        if (!generationLock.owns_lock()) {
            sink.onError("Model is already generating");
            sink.onDone(false);
            return false;
        }

        if (params.maxTokens <= 0 || params.maxTokens > session->nCtx) {
            sink.onError("Invalid generation token budget");
            sink.onDone(false);
            return false;
        }

        const llama_vocab* vocab = llama_model_get_vocab(session->model);
        std::vector<llama_token> tokens(std::max<size_t>(prompt.size() + 8, 32));
        int n = llama_tokenize(
            vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
            tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
        if (n < 0) {
            tokens.resize(static_cast<size_t>(-n));
            n = llama_tokenize(
                vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
        }
        if (n <= 0 || n > session->nCtx) {
            sink.onError("Tokenization failed or prompt exceeds context");
            sink.onDone(false);
            return false;
        }
        tokens.resize(static_cast<size_t>(n));

        llama_batch batch = llama_batch_get_one(tokens.data(), n);
        if (llama_decode(session->ctx, batch) != 0) {
            sink.onError("Prompt decode failed");
            sink.onDone(false);
            return false;
        }

        auto sparams = llama_sampler_chain_default_params();
        llama_sampler* smpl = llama_sampler_chain_init(sparams);
        if (!smpl) {
            sink.onError("Failed to initialize sampler");
            sink.onDone(false);
            return false;
        }
        auto add = [&](llama_sampler* sampler) -> bool {
            if (!sampler) return false;
            llama_sampler_chain_add(smpl, sampler);
            return true;
        };
        if (!add(llama_sampler_init_penalties(64, params.repeatPenalty, 0, 0)) ||
            (params.topK > 0 && !add(llama_sampler_init_top_k(params.topK))) ||
            (params.minP > 0 && !add(llama_sampler_init_min_p(params.minP, 1))) ||
            !add(llama_sampler_init_top_p(params.topP, 1)) ||
            !add(llama_sampler_init_temp(params.temperature))) {
            llama_sampler_free(smpl);
            sink.onError("Failed to initialize sampler chain");
            sink.onDone(false);
            return false;
        }
        uint32_t seed = params.seed < 0 ? LLAMA_DEFAULT_SEED : static_cast<uint32_t>(params.seed);
        if (!add(llama_sampler_init_dist(seed))) {
            llama_sampler_free(smpl);
            sink.onError("Failed to initialize random sampler");
            sink.onDone(false);
            return false;
        }

        auto started = std::chrono::steady_clock::now();
        int generated = 0;
        bool cancelled = false;
        std::string acc;
        const int remainingBudget = std::max(0, session->nCtx - n - 1);
        const int tokenBudget = std::min(params.maxTokens, remainingBudget);

        for (int i = 0; i < tokenBudget; ++i) {
            if (cancelFlag.load()) {
                cancelled = true;
                break;
            }
            llama_token id = llama_sampler_sample(smpl, session->ctx, -1);
            if (llama_vocab_is_eog(vocab, id)) break;
            llama_sampler_accept(smpl, id);

            char buf[256];
            int nPiece = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
            std::string piece;
            if (nPiece > 0) piece.assign(buf, buf + nPiece);
            acc += piece;
            bool stopHit = false;
            for (const auto& stop : params.stop) {
                if (!stop.empty() && acc.size() >= stop.size() &&
                    acc.compare(acc.size() - stop.size(), stop.size(), stop) == 0) {
                    stopHit = true;
                    break;
                }
            }
            if (stopHit) break;
            if (!piece.empty() && !sink.onToken(piece)) {
                cancelled = true;
                break;
            }
            generated++;
            llama_batch next = llama_batch_get_one(&id, 1);
            if (llama_decode(session->ctx, next) != 0) {
                sink.onError("Decode failed");
                llama_sampler_free(smpl);
                sink.onDone(false);
                return false;
            }
        }

        auto elapsed = std::chrono::duration<double>(std::chrono::steady_clock::now() - started).count();
        double tps = elapsed > 0 ? generated / elapsed : 0.0;
        sink.onMetrics(n, generated, tps);
        sink.onDone(cancelled);
        llama_sampler_free(smpl);
        return !cancelled;
    }

    std::string status(const std::string& handle) const override {
        std::lock_guard<std::mutex> lock(mu);
        auto it = sessions.find(handle);
        if (it == sessions.end()) return "missing";
        return "loaded ctx=" + std::to_string(it->second->nCtx);
    }

private:
    static void freeSession(std::unique_ptr<Session>& s) {
        if (!s) return;
        if (s->ctx) llama_free(s->ctx);
        if (s->model) llama_model_free(s->model);
        s.reset();
    }

    mutable std::mutex mu;
    std::unordered_map<std::string, std::unique_ptr<Session>> sessions;
    std::atomic<int> seq{0};
};

#endif  // OXYGEN_LLAMA_INCLUDED

class MissingBackend final : public InferenceBackend {
public:
    bool available() const override { return false; }
    std::string load(const std::string&, const LoadParams&, std::string& error) override {
        error = "llama.cpp was not compiled into this build";
        return {};
    }
    bool unload(const std::string&, std::string& error) override {
        error = "llama.cpp was not compiled into this build";
        return false;
    }
    bool generate(const std::string&, const std::string&, const GenParams&, TokenSink& sink,
                  std::atomic<bool>&) override {
        sink.onError("llama.cpp was not compiled into this build");
        sink.onDone(false);
        return false;
    }
    std::string status(const std::string&) const override { return "unavailable"; }
};

}  // namespace

std::unique_ptr<InferenceBackend> createBackend() {
#if OXYGEN_LLAMA_INCLUDED
    return std::make_unique<LlamaBackend>();
#else
    return std::make_unique<MissingBackend>();
#endif
}

}  // namespace oxygen
