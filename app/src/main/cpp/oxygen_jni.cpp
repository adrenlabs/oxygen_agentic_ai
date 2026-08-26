#include "gguf_reader.h"
#include "oxygen_inference.h"

#include <jni.h>

#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

namespace {

JavaVM* gVm = nullptr;
std::mutex gMu;
std::unique_ptr<oxygen::InferenceBackend> gBackend;
std::unordered_map<jlong, std::shared_ptr<std::atomic<bool>>> gCancels;
std::atomic<jlong> gCancelSeq{1};

std::string jstringToUtf8(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string out = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return out;
}

jstring utf8ToJstring(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

oxygen::InferenceBackend& backend() {
    std::lock_guard<std::mutex> lock(gMu);
    if (!gBackend) gBackend = oxygen::createBackend();
    return *gBackend;
}

class JniSink final : public oxygen::TokenSink {
public:
    JniSink(JNIEnv* env, jobject listener) : listenerGlobal(env->NewGlobalRef(listener)) {
        env->GetJavaVM(&vm);
        jclass cls = env->GetObjectClass(listener);
        tokenM = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)Z");
        metricsM = env->GetMethodID(cls, "onMetrics", "(IID)V");
        errorM = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
        doneM = env->GetMethodID(cls, "onDone", "(Z)V");
    }

    ~JniSink() override {
        JNIEnv* env = envAttach();
        if (env && listenerGlobal) env->DeleteGlobalRef(listenerGlobal);
        detach();
    }

    bool onToken(const std::string& token) override {
        JNIEnv* env = envAttach();
        if (!env) return false;
        jstring js = utf8ToJstring(env, token);
        jboolean keep = env->CallBooleanMethod(listenerGlobal, tokenM, js);
        env->DeleteLocalRef(js);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return false;
        }
        return keep == JNI_TRUE;
    }

    void onMetrics(int promptTokens, int generatedTokens, double tokensPerSecond) override {
        JNIEnv* env = envAttach();
        if (!env) return;
        env->CallVoidMethod(listenerGlobal, metricsM, promptTokens, generatedTokens, tokensPerSecond);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }

    void onError(const std::string& message) override {
        JNIEnv* env = envAttach();
        if (!env) return;
        jstring js = utf8ToJstring(env, message);
        env->CallVoidMethod(listenerGlobal, errorM, js);
        env->DeleteLocalRef(js);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }

    void onDone(bool cancelled) override {
        JNIEnv* env = envAttach();
        if (!env) return;
        env->CallVoidMethod(listenerGlobal, doneM, cancelled ? JNI_TRUE : JNI_FALSE);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }

private:
    JNIEnv* envAttach() {
        JNIEnv* env = nullptr;
        if (!vm) return nullptr;
        jint st = vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (st == JNI_OK) {
            attached = false;
            return env;
        }
        if (vm->AttachCurrentThread(&env, nullptr) == 0) {
            attached = true;
            return env;
        }
        return nullptr;
    }

    void detach() {
        if (attached && vm) vm->DetachCurrentThread();
        attached = false;
    }

    JavaVM* vm = nullptr;
    jobject listenerGlobal = nullptr;
    jmethodID tokenM = nullptr;
    jmethodID metricsM = nullptr;
    jmethodID errorM = nullptr;
    jmethodID doneM = nullptr;
    bool attached = false;
};

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    gVm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_oxygen_ai_inference_nativebridge_LlamaJni_nativeAvailable(JNIEnv*, jclass) {
    return backend().available() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_oxygen_ai_inference_nativebridge_LlamaJni_nativeReadGguf(JNIEnv* env, jclass, jstring path) {
    auto info = oxygen::readGguf(jstringToUtf8(env, path));
    return utf8ToJstring(env, oxygen::ggufInfoToJson(info));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_oxygen_ai_inference_nativebridge_LlamaJni_nativeLoad(
    JNIEnv* env,
    jclass,
    jstring path,
    jint nCtx,
    jint nThreads,
    jint nBatch,
    jint nGpuLayers,
    jboolean useMmap,
    jboolean useMlock,
    jfloat ropeFreqBase,
    jfloat yarnExtFactor,
    jfloat yarnAttnFactor,
    jint yarnOrigCtx
) {
    oxygen::LoadParams p;
    p.nCtx = nCtx;
    p.nThreads = nThreads;
    p.nBatch = nBatch;
    p.nGpuLayers = nGpuLayers;
    p.useMmap = useMmap == JNI_TRUE;
    p.useMlock = useMlock == JNI_TRUE;
    p.ropeFreqBase = ropeFreqBase;
    p.yarnExtFactor = yarnExtFactor;
    p.yarnAttnFactor = yarnAttnFactor;
    p.yarnOrigCtx = yarnOrigCtx;
    std::string error;
    std::string handle = backend().load(jstringToUtf8(env, path), p, error);
    if (handle.empty()) {
        return utf8ToJstring(env, std::string("ERR:") + error);
    }
    return utf8ToJstring(env, handle);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_oxygen_ai_inference_nativebridge_LlamaJni_nativeUnload(JNIEnv* env, jclass, jstring handle) {
    std::string error;
    return backend().unload(jstringToUtf8(env, handle), error) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_oxygen_ai_inference_nativebridge_LlamaJni_nativeCreateCancel(JNIEnv*, jclass) {
    jlong id = gCancelSeq.fetch_add(1);
    std::lock_guard<std::mutex> lock(gMu);
    gCancels[id] = std::make_shared<std::atomic<bool>>(false);
    return id;
}

extern "C" JNIEXPORT void JNICALL
Java_com_oxygen_ai_inference_nativebridge_LlamaJni_nativeCancel(JNIEnv*, jclass, jlong id) {
    std::lock_guard<std::mutex> lock(gMu);
    auto it = gCancels.find(id);
    if (it != gCancels.end()) it->second->store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_oxygen_ai_inference_nativebridge_LlamaJni_nativeReleaseCancel(JNIEnv*, jclass, jlong id) {
    std::lock_guard<std::mutex> lock(gMu);
    gCancels.erase(id);
}

extern "C" JNIEXPORT void JNICALL
Java_com_oxygen_ai_inference_nativebridge_LlamaJni_nativeGenerate(
    JNIEnv* env,
    jclass,
    jstring handle,
    jstring prompt,
    jint maxTokens,
    jfloat temperature,
    jint topK,
    jfloat topP,
    jfloat minP,
    jfloat repeatPenalty,
    jint seed,
    jobjectArray stopArray,
    jlong cancelId,
    jobject listener
) {
    oxygen::GenParams p;
    p.maxTokens = maxTokens;
    p.temperature = temperature;
    p.topK = topK;
    p.topP = topP;
    p.minP = minP;
    p.repeatPenalty = repeatPenalty;
    p.seed = seed;
    if (stopArray) {
        jsize n = env->GetArrayLength(stopArray);
        for (jsize i = 0; i < n; ++i) {
            auto js = reinterpret_cast<jstring>(env->GetObjectArrayElement(stopArray, i));
            p.stop.push_back(jstringToUtf8(env, js));
            if (js) env->DeleteLocalRef(js);
        }
    }
    std::shared_ptr<std::atomic<bool>> flag;
    {
        std::lock_guard<std::mutex> lock(gMu);
        auto it = gCancels.find(cancelId);
        if (it != gCancels.end()) flag = it->second;
    }
    if (!flag) flag = std::make_shared<std::atomic<bool>>(false);
    JniSink sink(env, listener);
    backend().generate(jstringToUtf8(env, handle), jstringToUtf8(env, prompt), p, sink, *flag);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_oxygen_ai_inference_nativebridge_LlamaJni_nativeStatus(JNIEnv* env, jclass, jstring handle) {
    return utf8ToJstring(env, backend().status(jstringToUtf8(env, handle)));
}
