/**
 * Copyright (c) 2017 m2049r
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <inttypes.h>
#include <cstring>
#include <mutex>
#include "anonero.h"
#include "wallet/api/wallet.h"
#include "string_tools.h"

//TODO explicit casting jlong, jint, jboolean to avoid warnings

#ifdef __cplusplus
extern "C"
{
#endif

#define LOG_TAG "WalletNDK"
#ifdef __ANDROID__
#include <android/log.h>
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG,__VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG  , LOG_TAG,__VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO   , LOG_TAG,__VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN   , LOG_TAG,__VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR  , LOG_TAG,__VA_ARGS__)
#else
// Desktop has no logcat; same five macros over stderr.
#include <stdio.h>
#define ANON_LOG(lvl, ...) do { \
        fprintf(stderr, "%s/" LOG_TAG ": ", lvl); \
        fprintf(stderr, __VA_ARGS__); \
        fputc('\n', stderr); \
    } while (0)
#define LOGV(...) ANON_LOG("V", __VA_ARGS__)
#define LOGD(...) ANON_LOG("D", __VA_ARGS__)
#define LOGI(...) ANON_LOG("I", __VA_ARGS__)
#define LOGW(...) ANON_LOG("W", __VA_ARGS__)
#define LOGE(...) ANON_LOG("E", __VA_ARGS__)
#endif

// Android's jni.h declares AttachCurrentThread(JNIEnv**); the desktop JDK's takes void**.
#ifdef __ANDROID__
#define ANON_ATTACH_ARG(e) (e)
#else
#define ANON_ATTACH_ARG(e) reinterpret_cast<void **>(e)
#endif

// android.util.Pair is framework-only. extract_pair() just reads its first/second
// Object fields, so the desktop source set supplies a class of the same shape.
#ifdef __ANDROID__
#define ANON_PAIR_CLASS "android/util/Pair"
#else
#define ANON_PAIR_CLASS "io/anonero/model/Pair"
#endif

static JavaVM *cachedJVM;
static jclass class_ArrayList;
static jclass class_WalletListener;
static jclass class_TransactionInfo;
static jclass class_Transfer;
static jclass class_Ledger;
static jclass class_WalletStatus;
static jclass class_CoinsInfo;
static jclass class_Pair;

std::mutex _listenerMutex;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *jvm, void *reserved) {
    cachedJVM = jvm;
    LOGI("JNI_OnLoad");
    JNIEnv *jenv;
    if (jvm->GetEnv(reinterpret_cast<void **>(&jenv), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    //LOGI("JNI_OnLoad ok");

    class_ArrayList = static_cast<jclass>(jenv->NewGlobalRef(
            jenv->FindClass("java/util/ArrayList")));
    class_TransactionInfo = static_cast<jclass>(jenv->NewGlobalRef(
            jenv->FindClass("io/anonero/model/TransactionInfo")));
    class_Transfer = static_cast<jclass>(jenv->NewGlobalRef(
            jenv->FindClass("io/anonero/model/Transfer")));
    class_WalletListener = static_cast<jclass>(jenv->NewGlobalRef(
            jenv->FindClass("io/anonero/model/WalletListener")));
//    class_Ledger = static_cast<jclass>(jenv->NewGlobalRef(
//            jenv->FindClass("io/anonero/wallet/ledger/Ledger")));
    class_WalletStatus = static_cast<jclass>(jenv->NewGlobalRef(
            jenv->FindClass("io/anonero/model/Wallet$Status")));
    class_CoinsInfo = static_cast<jclass>(jenv->NewGlobalRef(
            jenv->FindClass("io/anonero/model/CoinsInfo")));
    class_Pair = static_cast<jclass>(jenv->NewGlobalRef(
            jenv->FindClass(ANON_PAIR_CLASS)));
    return JNI_VERSION_1_6;
}
#ifdef __cplusplus
}
#endif

int attachJVM(JNIEnv **jenv) {
    int envStat = cachedJVM->GetEnv((void **) jenv, JNI_VERSION_1_6);
    if (envStat == JNI_EDETACHED) {
        if (cachedJVM->AttachCurrentThread(ANON_ATTACH_ARG(jenv), nullptr) != 0) {
            LOGE("Failed to attach");
            return JNI_ERR;
        }
    } else if (envStat == JNI_EVERSION) {
        LOGE("GetEnv: version not supported");
        return JNI_ERR;
    }
    //LOGI("envStat=%i", envStat);
    return envStat;
}

void detachJVM(JNIEnv *jenv, int envStat) {
    //LOGI("envStat=%i", envStat);
    if (jenv->ExceptionCheck()) {
        jenv->ExceptionDescribe();
    }

    if (envStat == JNI_EDETACHED) {
        cachedJVM->DetachCurrentThread();
    }
}

struct MyWalletListener : Monero::WalletListener {
    jobject jlistener;

    MyWalletListener(JNIEnv *env, jobject aListener) {
        LOGD("Created Listener");
        jlistener = env->NewGlobalRef(aListener);;
    }

    ~MyWalletListener() {
        LOGD("Destroyed Listener");
    };

    void deleteGlobalJavaRef(JNIEnv *env) {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        env->DeleteGlobalRef(jlistener);
        jlistener = nullptr;
    }

    /**
 * @brief updated  - generic callback, called when any event (sent/received/block reveived/etc) happened with the wallet;
 */
    void updated() {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        if (jlistener == nullptr) return;
        LOGD("updated");
        JNIEnv *jenv;
        int envStat = attachJVM(&jenv);
        if (envStat == JNI_ERR) return;

        jmethodID listenerClass_updated = jenv->GetMethodID(class_WalletListener, "updated", "()V");
        jenv->CallVoidMethod(jlistener, listenerClass_updated);

        detachJVM(jenv, envStat);
    }


    /**
     * @brief moneySpent - called when money spent
     * @param txId       - transaction id
     * @param amount     - amount
     */
    void moneySpent(const std::string &txId, uint64_t amount) {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        if (jlistener == nullptr) return;
        LOGD("moneySpent %"
                     PRIu64, amount);
    }

    /**
     * @brief moneyReceived - called when money received
     * @param txId          - transaction id
     * @param amount        - amount
     */
    void moneyReceived(const std::string &txId, uint64_t amount) {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        if (jlistener == nullptr) return;
        LOGD("moneyReceived %"
                     PRIu64, amount);
    }

    /**
     * @brief unconfirmedMoneyReceived - called when payment arrived in tx pool
     * @param txId          - transaction id
     * @param amount        - amount
     */
    void unconfirmedMoneyReceived(const std::string &txId, uint64_t amount) {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        if (jlistener == nullptr) return;
        LOGD("unconfirmedMoneyReceived %"
                     PRIu64, amount);
        JNIEnv *jenv;
        int envStat = attachJVM(&jenv);
        if (envStat == JNI_ERR) return;

        jlong amountLong = static_cast<jlong>(amount);
        jstring jstring1 = jenv->NewStringUTF(txId.c_str());

        jmethodID listenerClass_newBlock = jenv->GetMethodID(class_WalletListener,
                                                             "unconfirmedMoneyReceived",
                                                             "(Ljava/lang/String;J)V");
        jenv->CallVoidMethod(jlistener, listenerClass_newBlock, jstring1, amountLong);

        detachJVM(jenv, envStat);
    }

    /**
     * @brief newBlock      - called when new block received
     * @param height        - block height
     */
    void newBlock(uint64_t height) {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        if (jlistener == nullptr) return;
        //LOGD("newBlock");
        JNIEnv *jenv;
        int envStat = attachJVM(&jenv);
        if (envStat == JNI_ERR) return;

        jlong h = static_cast<jlong>(height);
        jmethodID listenerClass_newBlock = jenv->GetMethodID(class_WalletListener, "newBlock",
                                                             "(J)V");
        jenv->CallVoidMethod(jlistener, listenerClass_newBlock, h);

        detachJVM(jenv, envStat);
    }

/**
 * @brief refreshed - called when wallet refreshed by background thread or explicitly refreshed by calling "refresh" synchronously
 */
    void refreshed() {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        if (jlistener == nullptr) return;
        LOGD("refreshed");
        JNIEnv *jenv;

        int envStat = attachJVM(&jenv);
        if (envStat == JNI_ERR) return;

        jmethodID listenerClass_refreshed = jenv->GetMethodID(class_WalletListener, "refreshed",
                                                              "()V");
        jenv->CallVoidMethod(jlistener, listenerClass_refreshed);
        detachJVM(jenv, envStat);
    }
};


//// helper methods
std::vector<std::string> java2cpp(JNIEnv *env, jobject arrayList) {

    jmethodID java_util_ArrayList_size = env->GetMethodID(class_ArrayList, "size", "()I");
    jmethodID java_util_ArrayList_get = env->GetMethodID(class_ArrayList, "get",
                                                         "(I)Ljava/lang/Object;");

    jint len = env->CallIntMethod(arrayList, java_util_ArrayList_size);
    std::vector<std::string> result;
    result.reserve(len);
    for (jint i = 0; i < len; i++) {
        jstring element = static_cast<jstring>(env->CallObjectMethod(arrayList,
                                                                     java_util_ArrayList_get, i));
        const char *pchars = env->GetStringUTFChars(element, nullptr);
        result.emplace_back(pchars);
        env->ReleaseStringUTFChars(element, pchars);
        env->DeleteLocalRef(element);
    }
    return result;
}

jlong getElement(JNIEnv *env, jlongArray arr_j, int element) {
    jlong result;
    env->GetLongArrayRegion(arr_j, element, 1, &result);
    return result;
}

std::vector<std::uint64_t> java2cpp_long(JNIEnv *env, jlongArray longArray) {
    jint len = env->GetArrayLength(longArray);
    std::vector<std::uint64_t> result;
    result.reserve(len);
    for (jint i = 0; i < len; i++) {
        jlong amount = getElement(env, longArray, i);
        result.emplace_back(amount);
    }
    return result;
}

std::set<std::string> java2cpp_set(JNIEnv *env, jobject arrayList) {

    jmethodID java_util_ArrayList_size = env->GetMethodID(class_ArrayList, "size", "()I");
    jmethodID java_util_ArrayList_get = env->GetMethodID(class_ArrayList, "get",
                                                         "(I)Ljava/lang/Object;");

    jint len = env->CallIntMethod(arrayList, java_util_ArrayList_size);
    std::set<std::string> result;
    for (jint i = 0; i < len; i++) {
        jstring element = static_cast<jstring>(env->CallObjectMethod(arrayList,
                                                                     java_util_ArrayList_get, i));
        const char *pchars = env->GetStringUTFChars(element, nullptr);
        result.emplace(pchars);
        env->ReleaseStringUTFChars(element, pchars);
        env->DeleteLocalRef(element);
    }
    return result;
}

std::pair<std::string, uint64_t> extract_pair(JNIEnv *env, jobject p) {
    jfieldID first = env->GetFieldID(class_Pair, "first", "Ljava/lang/Object;");
    jfieldID second = env->GetFieldID(class_Pair, "second", "Ljava/lang/Object;");
    jstring string = static_cast<jstring>(env->GetObjectField(p, first));
    std::string converted_string = env->GetStringUTFChars(string, nullptr);
    uint64_t value = reinterpret_cast<uint64_t>(env->GetObjectField(p, second));
    auto pair = std::make_pair(converted_string, value);
    return pair;
}


std::vector<std::pair<std::string, uint64_t>> java2cpp_pairvector(JNIEnv *env, jobject arrayList) {

    jmethodID java_util_ArrayList_size = env->GetMethodID(class_ArrayList, "size", "()I");
    jmethodID java_util_ArrayList_get = env->GetMethodID(class_ArrayList, "get",
                                                         "(I)Ljava/lang/Object;");

    jint len = env->CallIntMethod(arrayList, java_util_ArrayList_size);
    std::vector<std::pair<std::string, uint64_t>> result;

    for (jint i = 0; i < len; i++) {
        jobject element = static_cast<jobject>(env->CallObjectMethod(arrayList,
                                                                     java_util_ArrayList_get, i));
        auto pair = extract_pair(env, element);
        result.emplace_back(pair);
    }
    return result;
}

jobject cpp2java(JNIEnv *env, const std::vector<std::string> &vector) {

    jmethodID java_util_ArrayList_ = env->GetMethodID(class_ArrayList, "<init>", "(I)V");
    jmethodID java_util_ArrayList_add = env->GetMethodID(class_ArrayList, "add",
                                                         "(Ljava/lang/Object;)Z");

    jobject result = env->NewObject(class_ArrayList, java_util_ArrayList_,
                                    static_cast<jint> (vector.size()));
    for (const std::string &s: vector) {
        jstring element = env->NewStringUTF(s.c_str());
        env->CallBooleanMethod(result, java_util_ArrayList_add, element);
        env->DeleteLocalRef(element);
    }
    return result;
}

/// end helpers

#ifdef __cplusplus
extern "C"
{
#endif


/**********************************/
/********** WalletManager *********/
/**********************************/
JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_createWalletJ(JNIEnv *env, jobject instance,
                                                  jstring path,
                                                  jstring password,
                                                  jstring passpharse,
                                                  jstring language,
                                                  jint networkType) {
    std::string seed_words;
    std::string err;
    bool _polyseedCreate = Monero::Wallet::createPolyseed(seed_words, err);
    if (!_polyseedCreate) {
        LOGE("createWalletJ(): createPolyseed failed: %s", err.c_str());
        return 0;
    }

    const char *_path = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    const char *_passpharse = env->GetStringUTFChars(passpharse, nullptr);
    const char *_language = env->GetStringUTFChars(language, nullptr);
    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);
    Monero::Wallet *wallet = nullptr;

    try {
        wallet =
                Monero::WalletManagerFactory::getWalletManager()->createWalletFromPolyseed(
                        std::string(_path),
                        std::string(_password),
                        _networkType,
                        seed_words,
                        std::string(_passpharse), true);

        if (wallet != nullptr) {
            bool setupStatus = wallet->setupBackgroundSync(
                    Monero::Wallet::BackgroundSync_ReusePassword,
                    std::string(_password), {});
            LOGD("createWalletJ(): setupBackgroundSync(): %s",
                 setupStatus ? "success" : "failure");
        } else {
            LOGE("createWalletJ(): createWalletFromPolyseed returned null");
        }
    } catch (const std::exception &e) {
        LOGE("createWalletJ(): exception: %s", e.what());
        wallet = nullptr;
    } catch (...) {
        LOGE("createWalletJ(): unknown exception");
        wallet = nullptr;
    }

    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    env->ReleaseStringUTFChars(passpharse, _passpharse);
    env->ReleaseStringUTFChars(language, _language);
    return reinterpret_cast<jlong>(wallet);
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_openWalletJ(JNIEnv *env, jobject instance,
                                                jstring path, jstring password,
                                                jint networkType, jboolean viewOnly) {
    LOGD("openWalletJ(): start");
    const char *_path = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);
    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->openWallet(
                    std::string(_path),
                    std::string(_password),
                    _networkType);

    if (!viewOnly) {
        // setup background sync
        bool setupStatus = wallet->setupBackgroundSync(Monero::Wallet::BackgroundSync_ReusePassword,
                                                       std::string(_password), {});
        if (setupStatus == true) {
            LOGD("openWalletJ(): setupBackgroundSync(): success!");
        } else {
            LOGD("openWalletJ(): setupBackgroundSync(): failure!");
        }
    }
    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    return reinterpret_cast<jlong>(wallet);
}


JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_recoveryWalletJ(JNIEnv *env, jobject instance,
                                                    jstring path, jstring password,
                                                    jstring mnemonic, jstring offset,
                                                    jint networkType,
                                                    jlong restoreHeight) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    const char *_mnemonic = env->GetStringUTFChars(mnemonic, nullptr);
    const char *_offset = env->GetStringUTFChars(offset, nullptr);

    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);
    Monero::Wallet *wallet = nullptr;

    try {
        wallet = Monero::WalletManagerFactory::getWalletManager()->recoveryWallet(
                std::string(_path),
                std::string(_password),
                std::string(_mnemonic),
                _networkType,
                static_cast<uint64_t>(restoreHeight),
                1,
                std::string(_offset));

        if (wallet != nullptr) {
            const bool setupStatus = wallet->setupBackgroundSync(
                    Monero::Wallet::BackgroundSync_ReusePassword,
                    std::string(_password), {});
            LOGD("recoveryWalletJ(): setupBackgroundSync(): %s",
                 setupStatus ? "success" : "failure");
        } else {
            LOGE("recoveryWalletJ(): recoveryWallet returned null");
        }
    } catch (const std::exception &e) {
        LOGE("recoveryWalletJ(): exception: %s", e.what());
        wallet = nullptr;
    } catch (...) {
        LOGE("recoveryWalletJ(): unknown exception");
        wallet = nullptr;
    }

    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    env->ReleaseStringUTFChars(mnemonic, _mnemonic);
    env->ReleaseStringUTFChars(offset, _offset);
    return reinterpret_cast<jlong>(wallet);
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_recoveryWalletPolyseedJ(JNIEnv *env, jobject instance,
                                                            jstring path, jstring password,
                                                            jstring mnemonic, jstring offset,
                                                            jint networkType) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    const char *_mnemonic = env->GetStringUTFChars(mnemonic, nullptr);
    const char *_offset = env->GetStringUTFChars(offset, nullptr);

    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);
    Monero::Wallet *wallet = nullptr;

    try {
        wallet = Monero::WalletManagerFactory::getWalletManager()->createWalletFromPolyseed(
                std::string(_path),
                std::string(_password),
                _networkType,
                std::string(_mnemonic),
                std::string(_offset),
                true);

        if (wallet != nullptr) {
            const bool setupStatus = wallet->setupBackgroundSync(
                    Monero::Wallet::BackgroundSync_ReusePassword,
                    std::string(_password), {});
            LOGD("recoveryWalletPolyseedJ(): setupBackgroundSync(): %s",
                 setupStatus ? "success" : "failure");
        } else {
            LOGE("recoveryWalletPolyseedJ(): createWalletFromPolyseed returned null");
        }
    } catch (const std::exception &e) {
        LOGE("recoveryWalletPolyseedJ(): exception: %s", e.what());
        wallet = nullptr;
    } catch (...) {
        LOGE("recoveryWalletPolyseedJ(): unknown exception");
        wallet = nullptr;
    }

    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    env->ReleaseStringUTFChars(mnemonic, _mnemonic);
    env->ReleaseStringUTFChars(offset, _offset);
    return reinterpret_cast<jlong>(wallet);
}

#ifdef __cplusplus
}
#endif
