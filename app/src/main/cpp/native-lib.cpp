#include <jni.h>
#include <mbedtls/ctr_drbg.h>
#include <mbedtls/entropy.h>
#include <mbedtls/net_sockets.h>
#include <mbedtls/ssl.h>
#include <mbedtls/x509_crt.h>
#include <algorithm>
#include <arpa/inet.h>
#include <cerrno>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <fcntl.h>
#include <limits>
#include <memory>
#include <mutex>
#include <netdb.h>
#include <poll.h>
#include <string>
#include <sys/socket.h>
#include <thread>
#include <unistd.h>
#include <utility>
#include <vector>

extern "C" JNIEXPORT jstring JNICALL
Java_com_dramathon_dramathonplus_sign_Signer_getPart1(JNIEnv* env, jobject /* this */) {
    std::string part1 = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC9Q4Y5QX5j08HrnbY3irfKdkEllAU2OORnAjlXDyCzcm2Z6ZRrGvtTZUAMelfU5PWS6XGEm3d4kJEKbXi4Crl8o2E/E3YJPk1lQD1d0JTdrvZleETN1ViHZFSQwS3L94Woh0E3TPebaEYq88eExvKu1tDdjSoFjBbgMezySnas5Nc2xF28";
    return env->NewStringUTF(part1.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_dramathon_dramathonplus_sign_Signer_getPart2(JNIEnv* env, jobject /* this */) {
    // Decoded part 2 from Signer.kt
    std::string part2 = "XhPuC8m15u+dectsrJl+ALGcTDX3Lv3FURuwV/dN7WMEkgcseIKVMdJxzUB0PeSqCNftfxmdBV/U4yXFRxPhnSFSXCrkj6uJjickiYq1pQ1aZfrQe1eLD3MB2hKq7crhMcA3kpggQlnmy1wRR4BAttmSU4fPb/yF8D3hAgMBAAECggEBAJdru6p5RLZ3h/GLF2rud8bqv4piF51e/RWQyPFnMAGBrkByiYT7bFI3cnvJMhYpLHRigqjWfUofV3thRDDym54lVLtTRZ91khRMxgwVwdRuk8Fw7JNFenOwCJxbgdlq6iuAMuQclwll7qWUrm8DgMvzH93xf8o6X171cp4Sh0og1Ra7E9GZ37dzBlX2aJBK8VBfctZntuDP";
    return env->NewStringUTF(part2.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_dramathon_dramathonplus_sign_Signer_getPart3(JNIEnv* env, jobject /* this */) {
    std::string part3 = "x52e71nafqfbjXxZuEtpu92oJd6A9mWbd0BZTk72ZHUmDcKcqjfcEH19SWOphMJFYkxU5FRoIEr3/zisyTO4Mt33ZmwELOrY9PdlyAAyed7ZoH+hlTr7c025QROvb2LmqgRiUT56tMECgYEA+jH5m6iMRK6XjiBhSUnlr3DzRybwlQrtIj5sZprWe2my5uYHG3jbViYIO7GtQvMTnDrBCxNhuM6dPrL0cRnbsp/iBMXe3pyjT/aWveBkn4R+UpBsnbtDn28r1MZpCDtr5UNc0TPj4KFJvjnV/e8oGoyYEroECqcw1LqNOGDiLhkCgYEAwaemNePYrXW+MVX/hatfLQ96tpxwf7yuHdENZ2q5AFw73GJWYvC8VY+TcoKPAmeoCUMltI3TrS6K5Q/GoLd5K2BsoJrSxQNQFd3ehWAtdOuPDvQ5rn/2fsvgvc3rOvJh7uNnwEZCI/45WQg+UFWref4PPc+ArNtp9Xj2y7LndwkCgYARojIQeXmhYZjG6JtSugWZLuHGkwUDzChYcIPd";
    return env->NewStringUTF(part3.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_dramathon_dramathonplus_sign_Signer_getPart4(JNIEnv* env, jobject /* this */) {
    std::string part4 = "W25gdluokG/RzNvQn4+W/XfTryQjr7RpXm1VxCIrCBvYWNU2KrSYV4XUtL+B5ERNj6In6AOrOAifuVITy5cQQQeoD+AT4YKKMBkQfO2gnZzqb8+ox130e+3K/mufoqJPZeyrCQKBgC2fobjwhQvYwYY+DIUharri+rYrBRYTDbJYnh/PNOaw1CmHwXJt5PEDcml3+NlIMn58I1X2U/hpDrAIl3MlxpZBkVYFI8LmlOeR7ereTddN59ZOE4jY/OnCfqA480Jf+FKfoMHby5lPO5OOLaAfjtae1FhrmpUe3EfIx9wVuhKBAoGBAPFzHKQZbGhkqmyPW2ctTEIWLdUHyO37fm8dj1WjN4wjRAI4ohNiKQJRh3QE11E1PzBTl9lZVWT8QtEsSjnrA/tpGr378fcUT7WGBgTmBRaAnv1P1n/Tp0TSvh5XpIhhMuxcitIgrhYMIG3GbP9JNAarxO/qPW6Gi0xWaF7il7Or";
    return env->NewStringUTF(part4.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_dramathon_dramathonplus_decrypt_VideoDecryptor_getMasterKey(JNIEnv* env, jobject /* this */) {
    std::string masterKey = "6eba09062155dcd2";
    return env->NewStringUTF(masterKey.c_str());
}

// ReelShort request-signing material. Kept in the native library so the
// runtime configuration fetched from the Realtime Database only has to carry
// the per-user session data (uid/session/devId/...).
extern "C" JNIEXPORT jstring JNICALL
Java_com_dramathon_dramathonplus_client_NativeReelShortSecretProvider_getNativeSignSecretKey(JNIEnv* env, jobject /* this */) {
    std::string signSecretKey = "zj8N6zKEdrK8d1MxwHSvExdgQ868q1yT";
    return env->NewStringUTF(signSecretKey.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_dramathon_dramathonplus_client_NativeReelShortSecretProvider_getNativeAesKey(JNIEnv* env, jobject /* this */) {
    std::string aesKey = "VvRSNGFynLBW7aCP";
    return env->NewStringUTF(aesKey.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_dramathon_dramathonplus_client_NativeReelShortSecretProvider_getNativeAesIv(JNIEnv* env, jobject /* this */) {
    std::string aesIv = "gLn8sxqpzyNjehDP";
    return env->NewStringUTF(aesIv.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_dramathon_dramathonplus_client_NativeReelShortSecretProvider_getNativeApkSignatureHash(JNIEnv* env, jobject /* this */) {
    std::string apkSignatureHash = "32A8B9DC48FDD1F64A1C9DFBF07F87F9";
    return env->NewStringUTF(apkSignatureHash.c_str());
}

namespace {

constexpr char kFailureMessage[] = "ReelShort native request failed";
constexpr std::size_t kMaxEndpointBytes = 4096;
constexpr std::size_t kMaxTokenBytes = 16384;
constexpr std::size_t kMaxCertificateBytes = 1024 * 1024;
constexpr std::size_t kMaxCertificateArrayBytes = 4 * 1024 * 1024;
constexpr std::size_t kMaxHeaderBytes = 16 * 1024;
constexpr std::size_t kMaxResponseBytes = 64 * 1024;
constexpr std::size_t kMaxWireBytes = kMaxHeaderBytes + kMaxResponseBytes + 4096;
constexpr std::size_t kMaxChunkLineBytes = 128;
constexpr std::size_t kMaxHeaderLines = 128;
constexpr int kDnsTimeoutMs = 5000;
constexpr int kConnectTimeoutMs = 10000;
constexpr int kHandshakeTimeoutMs = 15000;
constexpr int kWriteTimeoutMs = 10000;
constexpr int kReadTimeoutMs = 20000;

void throwIllegalState(JNIEnv* env) {
    if (env == nullptr) {
        return;
    }
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    jclass exceptionClass = env->FindClass("java/lang/IllegalStateException");
    if (exceptionClass == nullptr) {
        return;
    }
    env->ThrowNew(exceptionClass, kFailureMessage);
    env->DeleteLocalRef(exceptionClass);
}

jstring failure(JNIEnv* env) {
    throwIllegalState(env);
    return nullptr;
}

char lowerAscii(char value) {
    if (value >= 'A' && value <= 'Z') {
        return static_cast<char>(value - 'A' + 'a');
    }
    return value;
}

std::string lowerAscii(std::string value) {
    for (char& character : value) {
        character = lowerAscii(character);
    }
    return value;
}

bool isHex(char value) {
    return (value >= '0' && value <= '9') ||
        (value >= 'a' && value <= 'f') ||
        (value >= 'A' && value <= 'F');
}

bool isAsciiDigit(char value) {
    return value >= '0' && value <= '9';
}

bool isHeaderToken(char value) {
    unsigned char character = static_cast<unsigned char>(value);
    if ((character >= 'a' && character <= 'z') ||
        (character >= 'A' && character <= 'Z') ||
        (character >= '0' && character <= '9')) {
        return true;
    }
    switch (character) {
        case '!':
        case '#':
        case '$':
        case '%':
        case '&':
        case '\'':
        case '*':
        case '+':
        case '-':
        case '.':
        case '^':
        case '_':
        case '`':
        case '|':
        case '~':
            return true;
        default:
            return false;
    }
}

std::string trimOws(const std::string& value) {
    std::size_t first = 0;
    while (first < value.size() && (value[first] == ' ' || value[first] == '\t')) {
        ++first;
    }
    std::size_t last = value.size();
    while (last > first && (value[last - 1] == ' ' || value[last - 1] == '\t')) {
        --last;
    }
    return value.substr(first, last - first);
}

bool isSafeUrlCharacter(unsigned char value) {
    return value > 0x20 && value < 0x7f;
}

bool isSafePath(const std::string& path) {
    for (std::size_t index = 0; index < path.size(); ++index) {
        unsigned char character = static_cast<unsigned char>(path[index]);
        if (!isSafeUrlCharacter(character) || character == '\\') {
            return false;
        }
        if (character == '%') {
            if (index + 2 >= path.size() || !isHex(path[index + 1]) || !isHex(path[index + 2])) {
                return false;
            }
        }
    }
    return true;
}

bool isAsciiLetter(char value);

bool isValidDnsHost(const std::string& host) {
    if (host.empty() || host.size() > 253) {
        return false;
    }
    std::size_t labelStart = 0;
    for (std::size_t index = 0; index <= host.size(); ++index) {
        if (index != host.size() && host[index] != '.') {
            continue;
        }
        std::size_t labelLength = index - labelStart;
        if (labelLength == 0 || labelLength > 63) {
            return false;
        }
        char first = host[labelStart];
        char last = host[index - 1];
        if (!isAsciiDigit(first) && !isAsciiLetter(first)) {
            return false;
        }
        if (!isAsciiDigit(last) && !isAsciiLetter(last)) {
            return false;
        }
        for (std::size_t position = labelStart; position < index; ++position) {
            char character = host[position];
            if (!isAsciiDigit(character) && !isAsciiLetter(character) && character != '-') {
                return false;
            }
        }
        labelStart = index + 1;
    }
    return true;
}

bool isAsciiLetter(char value) {
    return (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z');
}

bool parsePort(const std::string& value, int& port) {
    if (value.empty() || value.size() > 5) {
        return false;
    }
    int parsed = 0;
    for (char character : value) {
        if (!isAsciiDigit(character)) {
            return false;
        }
        parsed = parsed * 10 + (character - '0');
    }
    if (parsed < 1 || parsed > 65535) {
        return false;
    }
    port = parsed;
    return true;
}

struct Endpoint {
    std::string host;
    std::string hostHeader;
    std::string path;
    int port = 443;
    bool isIpLiteral = false;
};

bool parseEndpoint(const std::string& endpoint, Endpoint& parsed) {
    if (endpoint.empty() || endpoint.size() > kMaxEndpointBytes) {
        return false;
    }
    for (unsigned char character : endpoint) {
        if (!isSafeUrlCharacter(character)) {
            return false;
        }
    }
    const std::string scheme = "https://";
    if (endpoint.size() < scheme.size()) {
        return false;
    }
    for (std::size_t index = 0; index < scheme.size(); ++index) {
        if (lowerAscii(endpoint[index]) != scheme[index]) {
            return false;
        }
    }
    if (endpoint.find('#', scheme.size()) != std::string::npos) {
        return false;
    }
    std::size_t authorityEnd = endpoint.find_first_of("/?", scheme.size());
    if (authorityEnd == std::string::npos) {
        authorityEnd = endpoint.size();
    }
    if (authorityEnd == scheme.size()) {
        return false;
    }
    std::string authority = endpoint.substr(scheme.size(), authorityEnd - scheme.size());
    if (authority.empty() || authority.size() > 512 || authority.find('@') != std::string::npos ||
        authority.find('\\') != std::string::npos) {
        return false;
    }

    std::string host;
    std::string portText;
    bool hasPort = false;
    bool isIpLiteral = false;
    if (!authority.empty() && authority.front() == '[') {
        std::size_t closingBracket = authority.find(']');
        if (closingBracket == std::string::npos || closingBracket == 1) {
            return false;
        }
        host = authority.substr(1, closingBracket - 1);
        if (host.find_first_of("[]@\\") != std::string::npos) {
            return false;
        }
        in6_addr address{};
        if (inet_pton(AF_INET6, host.c_str(), &address) != 1) {
            return false;
        }
        isIpLiteral = true;
        if (closingBracket + 1 < authority.size()) {
            if (authority[closingBracket + 1] != ':') {
                return false;
            }
            hasPort = true;
            portText = authority.substr(closingBracket + 2);
        }
    } else {
        if (authority.find_first_of("[]") != std::string::npos) {
            return false;
        }
        std::size_t colon = authority.rfind(':');
        if (colon != std::string::npos) {
            if (authority.find(':') != colon) {
                return false;
            }
            host = authority.substr(0, colon);
            hasPort = true;
            portText = authority.substr(colon + 1);
        } else {
            host = authority;
        }
        if (host.empty() || !isValidDnsHost(host)) {
            return false;
        }
        in_addr address{};
        isIpLiteral = inet_pton(AF_INET, host.c_str(), &address) == 1;
    }

    int port = 443;
    if (hasPort && !parsePort(portText, port)) {
        return false;
    }
    host = lowerAscii(std::move(host));
    if (host.size() > 255) {
        return false;
    }

    std::string path = endpoint.substr(authorityEnd);
    if (path.empty()) {
        path = "/";
    } else if (path.front() == '?') {
        path = "/" + path;
    }
    if (path.empty() || path.front() != '/' || !isSafePath(path)) {
        return false;
    }

    parsed.host = std::move(host);
    parsed.port = port;
    parsed.isIpLiteral = isIpLiteral;
    parsed.hostHeader = parsed.isIpLiteral && parsed.host.find(':') != std::string::npos
        ? "[" + parsed.host + "]"
        : parsed.host;
    if (parsed.port != 443) {
        parsed.hostHeader += ":" + std::to_string(parsed.port);
    }
    parsed.path = std::move(path);
    return true;
}

class JniChars {
public:
    JniChars(JNIEnv* env, jstring value) : env_(env), value_(value) {
        if (env_ != nullptr && value_ != nullptr) {
            length_ = env_->GetStringUTFLength(value_);
            if (length_ >= 0) {
                characters_ = env_->GetStringUTFChars(value_, nullptr);
            }
        }
    }

    ~JniChars() {
        if (characters_ != nullptr) {
            env_->ReleaseStringUTFChars(value_, characters_);
        }
    }

    JniChars(const JniChars&) = delete;
    JniChars& operator=(const JniChars&) = delete;

    const char* get() const {
        return characters_;
    }

    jsize length() const {
        return length_;
    }

private:
    JNIEnv* env_ = nullptr;
    jstring value_ = nullptr;
    const char* characters_ = nullptr;
    jsize length_ = -1;
};

bool readJniString(JNIEnv* env, jstring value, std::size_t maxLength, std::string& result) {
    if (env == nullptr || value == nullptr) {
        return false;
    }
    JniChars characters(env, value);
    if (characters.get() == nullptr || characters.length() < 0 ||
        static_cast<std::size_t>(characters.length()) > maxLength) {
        return false;
    }
    result.assign(characters.get(), static_cast<std::size_t>(characters.length()));
    return result.find('\0') == std::string::npos;
}

bool isValidAppCheckToken(const std::string& token) {
    if (token.empty() || token.size() > kMaxTokenBytes) {
        return false;
    }
    for (unsigned char character : token) {
        if (character < 0x21 || character > 0x7e) {
            return false;
        }
    }
    return true;
}

class MbedTlsState {
public:
    MbedTlsState() {
        mbedtls_ssl_init(&ssl);
        mbedtls_ssl_config_init(&config);
        mbedtls_x509_crt_init(&roots);
        mbedtls_ctr_drbg_init(&ctrDrbg);
        mbedtls_entropy_init(&entropy);
    }

    ~MbedTlsState() {
        mbedtls_ssl_free(&ssl);
        mbedtls_ssl_config_free(&config);
        mbedtls_x509_crt_free(&roots);
        mbedtls_ctr_drbg_free(&ctrDrbg);
        mbedtls_entropy_free(&entropy);
    }

    MbedTlsState(const MbedTlsState&) = delete;
    MbedTlsState& operator=(const MbedTlsState&) = delete;

    bool initialize() {
        if (mbedtls_ctr_drbg_seed(&ctrDrbg, mbedtls_entropy_func, &entropy, nullptr, 0) != 0) {
            return false;
        }
        // mbedtls_ssl_config_init() only zeroes the struct. Without loading a preset,
        // conf->ciphersuite_list (and curves/sig_algs/content lengths) stay NULL, and
        // ssl_write_client_hello_cipher_suites() dereferences the cipher list while
        // building the ClientHello, crashing the process instead of failing cleanly.
        if (mbedtls_ssl_config_defaults(&config, MBEDTLS_SSL_IS_CLIENT,
                                       MBEDTLS_SSL_TRANSPORT_STREAM,
                                       MBEDTLS_SSL_PRESET_DEFAULT) != 0) {
            return false;
        }
        mbedtls_ssl_conf_authmode(&config, MBEDTLS_SSL_VERIFY_REQUIRED);
        mbedtls_ssl_conf_rng(&config, mbedtls_ctr_drbg_random, &ctrDrbg);
        mbedtls_ssl_conf_ca_chain(&config, &roots, nullptr);
        mbedtls_ssl_conf_min_tls_version(&config, MBEDTLS_SSL_VERSION_TLS1_2);
        mbedtls_ssl_conf_max_tls_version(&config, MBEDTLS_SSL_VERSION_TLS1_3);
        mbedtls_ssl_conf_read_timeout(&config, static_cast<std::uint32_t>(kReadTimeoutMs));
        return mbedtls_ssl_setup(&ssl, &config) == 0;
    }

    mbedtls_ssl_context ssl;
    mbedtls_ssl_config config;
    mbedtls_x509_crt roots;
    mbedtls_ctr_drbg_context ctrDrbg;
    mbedtls_entropy_context entropy;
};

bool loadCertificates(JNIEnv* env, jobjectArray certificateArray, mbedtls_x509_crt* roots) {
    if (env == nullptr || certificateArray == nullptr || roots == nullptr) {
        return false;
    }
    jsize certificateCount = env->GetArrayLength(certificateArray);
    if (certificateCount <= 0 || certificateCount > 1024 || env->ExceptionCheck()) {
        return false;
    }
    std::size_t totalLength = 0;
    for (jsize index = 0; index < certificateCount; ++index) {
        jbyteArray certificate = static_cast<jbyteArray>(env->GetObjectArrayElement(certificateArray, index));
        if (env->ExceptionCheck() || certificate == nullptr) {
            if (certificate != nullptr) {
                env->DeleteLocalRef(certificate);
            }
            return false;
        }
        jsize certificateLength = env->GetArrayLength(certificate);
        if (certificateLength <= 0 || static_cast<std::size_t>(certificateLength) > kMaxCertificateBytes ||
            totalLength > kMaxCertificateArrayBytes - static_cast<std::size_t>(certificateLength) ||
            env->ExceptionCheck()) {
            env->DeleteLocalRef(certificate);
            return false;
        }
        std::vector<unsigned char> certificateData(static_cast<std::size_t>(certificateLength));
        env->GetByteArrayRegion(certificate, 0, certificateLength,
                                reinterpret_cast<jbyte*>(certificateData.data()));
        env->DeleteLocalRef(certificate);
        if (env->ExceptionCheck() ||
            mbedtls_x509_crt_parse_der(roots, certificateData.data(), certificateData.size()) != 0) {
            return false;
        }
        totalLength += static_cast<std::size_t>(certificateLength);
    }
    return totalLength != 0;
}

class SocketFd {
public:
    SocketFd() = default;

    explicit SocketFd(int fd) : fd_(fd) {
    }

    ~SocketFd() {
        close();
    }

    SocketFd(const SocketFd&) = delete;
    SocketFd& operator=(const SocketFd&) = delete;

    SocketFd(SocketFd&& other) noexcept : fd_(other.fd_) {
        other.fd_ = -1;
    }

    SocketFd& operator=(SocketFd&& other) noexcept {
        if (this != &other) {
            close();
            fd_ = other.fd_;
            other.fd_ = -1;
        }
        return *this;
    }

    int get() const {
        return fd_;
    }

    void reset(int fd) {
        close();
        fd_ = fd;
    }

private:
    void close() {
        if (fd_ >= 0) {
            ::close(fd_);
            fd_ = -1;
        }
    }

    int fd_ = -1;
};

class AddrInfoHandle {
public:
    explicit AddrInfoHandle(addrinfo* value) : value_(value) {
    }

    ~AddrInfoHandle() {
        if (value_ != nullptr) {
            freeaddrinfo(value_);
        }
    }

    AddrInfoHandle(const AddrInfoHandle&) = delete;
    AddrInfoHandle& operator=(const AddrInfoHandle&) = delete;

    addrinfo* get() const {
        return value_;
    }

private:
    addrinfo* value_;
};

enum class WaitResult {
    kReady,
    kTimeout,
    kError
};

WaitResult waitForSocket(int fd, short events, std::chrono::steady_clock::time_point deadline) {
    for (;;) {
        auto now = std::chrono::steady_clock::now();
        if (now >= deadline) {
            return WaitResult::kTimeout;
        }
        auto remaining = std::chrono::duration_cast<std::chrono::microseconds>(deadline - now).count();
        if (remaining <= 0) {
            remaining = 1;
        }
        std::int64_t milliseconds = (remaining + 999) / 1000;
        if (milliseconds > std::numeric_limits<int>::max()) {
            milliseconds = std::numeric_limits<int>::max();
        }
        pollfd descriptor{};
        descriptor.fd = fd;
        descriptor.events = events;
        int result = ::poll(&descriptor, 1, static_cast<int>(milliseconds));
        if (result > 0) {
            if ((descriptor.revents & POLLNVAL) != 0) {
                return WaitResult::kError;
            }
            return WaitResult::kReady;
        }
        if (result == 0) {
            return WaitResult::kTimeout;
        }
        if (errno != EINTR) {
            return WaitResult::kError;
        }
    }
}

struct ResolveState {
    std::mutex mutex;
    std::condition_variable condition;
    bool done = false;
    bool abandoned = false;
    int error = EAI_FAIL;
    addrinfo* addresses = nullptr;
};

void resolveWorker(const std::shared_ptr<ResolveState>& state, const std::string& host,
                   const std::string& service, int flags) noexcept {
    addrinfo* addresses = nullptr;
    int error = EAI_FAIL;
    try {
        addrinfo hints{};
        hints.ai_family = AF_UNSPEC;
        hints.ai_socktype = SOCK_STREAM;
        hints.ai_flags = flags;
        error = getaddrinfo(host.c_str(), service.c_str(), &hints, &addresses);
    } catch (...) {
        error = EAI_FAIL;
    }
    bool transferred = false;
    {
        std::lock_guard<std::mutex> lock(state->mutex);
        state->done = true;
        if (!state->abandoned) {
            state->error = error;
            state->addresses = addresses;
            transferred = true;
        }
    }
    state->condition.notify_one();
    if (!transferred && addresses != nullptr) {
        freeaddrinfo(addresses);
    }
}

bool resolveHost(const Endpoint& endpoint, addrinfo*& addresses) {
    addresses = nullptr;
    const std::string service = std::to_string(endpoint.port);
    int flags = endpoint.isIpLiteral ? AI_NUMERICHOST : 0;
    auto state = std::make_shared<ResolveState>();
    std::thread worker(resolveWorker, state, endpoint.host, service, flags);
    std::unique_lock<std::mutex> lock(state->mutex);
    const auto deadline = std::chrono::steady_clock::now() +
        std::chrono::milliseconds(kDnsTimeoutMs);
    while (!state->done) {
        if (state->condition.wait_until(lock, deadline) == std::cv_status::timeout && !state->done) {
            state->abandoned = true;
            lock.unlock();
            worker.detach();
            return false;
        }
    }
    int error = state->error;
    addresses = state->addresses;
    state->addresses = nullptr;
    lock.unlock();
    worker.join();
    if (error != 0) {
        if (addresses != nullptr) {
            freeaddrinfo(addresses);
            addresses = nullptr;
        }
        return false;
    }
    return addresses != nullptr;
}

bool setNonBlocking(int fd) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags < 0 || fcntl(fd, F_SETFL, flags | O_NONBLOCK) != 0) {
        return false;
    }
    int descriptorFlags = fcntl(fd, F_GETFD, 0);
    return descriptorFlags >= 0 && fcntl(fd, F_SETFD, descriptorFlags | FD_CLOEXEC) == 0;
}

bool connectOne(const addrinfo* address, std::chrono::steady_clock::time_point deadline, SocketFd& socket) {
    int fd = ::socket(address->ai_family, SOCK_STREAM, IPPROTO_TCP);
    if (fd < 0) {
        return false;
    }
    SocketFd candidate(fd);
    if (!setNonBlocking(fd)) {
        return false;
    }
    int result = ::connect(fd, address->ai_addr, address->ai_addrlen);
    if (result != 0 && errno != EINPROGRESS && errno != EINTR) {
        return false;
    }
    if (result != 0) {
        WaitResult waitResult = waitForSocket(fd, POLLOUT, deadline);
        if (waitResult != WaitResult::kReady) {
            return false;
        }
    }
    int socketError = 0;
    socklen_t socketErrorLength = sizeof(socketError);
    if (getsockopt(fd, SOL_SOCKET, SO_ERROR, &socketError, &socketErrorLength) != 0 || socketError != 0) {
        return false;
    }
    socket = std::move(candidate);
    return true;
}

bool connectHost(const Endpoint& endpoint, SocketFd& socket) {
    addrinfo* rawAddresses = nullptr;
    if (!resolveHost(endpoint, rawAddresses)) {
        return false;
    }
    AddrInfoHandle addresses(rawAddresses);
    const auto deadline = std::chrono::steady_clock::now() +
        std::chrono::milliseconds(kConnectTimeoutMs);
    for (const addrinfo* address = addresses.get(); address != nullptr; address = address->ai_next) {
        if (connectOne(address, deadline, socket)) {
            return true;
        }
        if (std::chrono::steady_clock::now() >= deadline) {
            break;
        }
    }
    return false;
}

struct BioContext {
    int fd = -1;
    std::chrono::steady_clock::time_point deadline;
};

int bioSend(void* context, const unsigned char* buffer, std::size_t length) {
    BioContext* bio = static_cast<BioContext*>(context);
    if (bio == nullptr || bio->fd < 0 || (buffer == nullptr && length != 0)) {
        return MBEDTLS_ERR_NET_SEND_FAILED;
    }
    if (length == 0) {
        return 0;
    }
    for (;;) {
        ssize_t result = ::send(bio->fd, buffer, length, MSG_NOSIGNAL);
        if (result > 0) {
            return static_cast<int>(result);
        }
        if (result == 0) {
            return MBEDTLS_ERR_NET_SEND_FAILED;
        }
        if (errno == EINTR) {
            continue;
        }
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            WaitResult waitResult = waitForSocket(bio->fd, POLLOUT, bio->deadline);
            if (waitResult == WaitResult::kReady) {
                continue;
            }
            return waitResult == WaitResult::kTimeout ? MBEDTLS_ERR_SSL_TIMEOUT : MBEDTLS_ERR_NET_SEND_FAILED;
        }
        return MBEDTLS_ERR_NET_SEND_FAILED;
    }
}

int bioReceive(BioContext* bio, unsigned char* buffer, std::size_t length,
               std::chrono::steady_clock::time_point deadline) {
    if (bio == nullptr || bio->fd < 0 || (buffer == nullptr && length != 0)) {
        return MBEDTLS_ERR_NET_RECV_FAILED;
    }
    if (length == 0) {
        return 0;
    }
    for (;;) {
        ssize_t result = ::recv(bio->fd, buffer, length, 0);
        if (result > 0) {
            return static_cast<int>(result);
        }
        if (result == 0) {
            return 0;
        }
        if (errno == EINTR) {
            continue;
        }
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            WaitResult waitResult = waitForSocket(bio->fd, POLLIN, deadline);
            if (waitResult == WaitResult::kReady) {
                continue;
            }
            return waitResult == WaitResult::kTimeout ? MBEDTLS_ERR_SSL_TIMEOUT : MBEDTLS_ERR_NET_RECV_FAILED;
        }
        return MBEDTLS_ERR_NET_RECV_FAILED;
    }
}

int bioReceiveTimeout(void* context, unsigned char* buffer, std::size_t length, std::uint32_t timeout) {
    BioContext* bio = static_cast<BioContext*>(context);
    if (bio == nullptr) {
        return MBEDTLS_ERR_NET_RECV_FAILED;
    }
    auto deadline = bio->deadline;
    if (timeout > 0) {
        auto requested = std::chrono::steady_clock::now() + std::chrono::milliseconds(timeout);
        if (requested < deadline) {
            deadline = requested;
        }
    }
    return bioReceive(bio, buffer, length, deadline);
}

bool writeTlsData(mbedtls_ssl_context* ssl, const std::string& request, BioContext& bio) {
    std::size_t offset = 0;
    while (offset < request.size()) {
        int result = mbedtls_ssl_write(ssl,
                                       reinterpret_cast<const unsigned char*>(request.data() + offset),
                                       request.size() - offset);
        if (result > 0) {
            offset += static_cast<std::size_t>(result);
            continue;
        }
        if (result == MBEDTLS_ERR_SSL_WANT_READ || result == MBEDTLS_ERR_SSL_WANT_WRITE) {
            if (std::chrono::steady_clock::now() >= bio.deadline) {
                return false;
            }
            continue;
        }
        return false;
    }
    return true;
}

enum class ParseResult {
    kNeedMore,
    kComplete,
    kError
};

std::size_t findCrlf(const std::vector<unsigned char>& buffer, std::size_t start) {
    if (start >= buffer.size()) {
        return std::string::npos;
    }
    for (std::size_t index = start; index + 1 < buffer.size(); ++index) {
        if (buffer[index] == '\r' && buffer[index + 1] == '\n') {
            return index;
        }
    }
    return std::string::npos;
}

std::size_t findHeaderEnd(const std::vector<unsigned char>& buffer) {
    if (buffer.size() < 4) {
        return std::string::npos;
    }
    for (std::size_t index = 0; index + 3 < buffer.size(); ++index) {
        if (buffer[index] == '\r' && buffer[index + 1] == '\n' &&
            buffer[index + 2] == '\r' && buffer[index + 3] == '\n') {
            return index + 4;
        }
    }
    return std::string::npos;
}

class HttpResponseParser {
public:
    bool append(const unsigned char* data, std::size_t length) {
        if (failed_ || (data == nullptr && length != 0)) {
            failed_ = true;
            return false;
        }
        if (length > kMaxWireBytes - buffer_.size()) {
            failed_ = true;
            return false;
        }
        if (length != 0) {
            buffer_.insert(buffer_.end(), data, data + length);
        }
        return true;
    }

    ParseResult consume(bool eof) {
        if (failed_) {
            return ParseResult::kError;
        }
        if (complete_) {
            return ParseResult::kComplete;
        }
        if (!headersParsed_) {
            std::size_t headerEnd = findHeaderEnd(buffer_);
            if (headerEnd == std::string::npos) {
                if (buffer_.size() > kMaxHeaderBytes) {
                    failed_ = true;
                    return ParseResult::kError;
                }
                return eof ? ParseResult::kError : ParseResult::kNeedMore;
            }
            if (headerEnd > kMaxHeaderBytes || !parseHeaders(headerEnd)) {
                failed_ = true;
                return ParseResult::kError;
            }
            position_ = headerEnd;
            headersParsed_ = true;
        }

        if (framing_ == Framing::kLength) {
            while (body_.size() < contentLength_) {
                std::size_t available = buffer_.size() - position_;
                if (available == 0) {
                    return eof ? ParseResult::kError : ParseResult::kNeedMore;
                }
                std::size_t amount = std::min(available, contentLength_ - body_.size());
                if (!appendBody(buffer_.data() + position_, amount)) {
                    failed_ = true;
                    return ParseResult::kError;
                }
                position_ += amount;
            }
            complete_ = true;
            return ParseResult::kComplete;
        }

        if (framing_ == Framing::kChunked) {
            for (;;) {
                switch (chunkState_) {
                    case ChunkState::kSize: {
                        std::size_t lineEnd = findCrlf(buffer_, position_);
                        if (lineEnd == std::string::npos) {
                            if (buffer_.size() - position_ > kMaxChunkLineBytes) {
                                failed_ = true;
                                return ParseResult::kError;
                            }
                            return eof ? ParseResult::kError : ParseResult::kNeedMore;
                        }
                        if (lineEnd - position_ > kMaxChunkLineBytes ||
                            !parseChunkSize(buffer_.data() + position_, lineEnd - position_, chunkRemaining_)) {
                            failed_ = true;
                            return ParseResult::kError;
                        }
                        position_ = lineEnd + 2;
                        chunkState_ = chunkRemaining_ == 0 ? ChunkState::kTrailers : ChunkState::kData;
                        break;
                    }
                    case ChunkState::kData: {
                        std::size_t available = buffer_.size() - position_;
                        if (available == 0) {
                            return eof ? ParseResult::kError : ParseResult::kNeedMore;
                        }
                        std::size_t amount = std::min(available, chunkRemaining_);
                        if (!appendBody(buffer_.data() + position_, amount)) {
                            failed_ = true;
                            return ParseResult::kError;
                        }
                        position_ += amount;
                        chunkRemaining_ -= amount;
                        if (chunkRemaining_ == 0) {
                            chunkState_ = ChunkState::kDataEnd;
                        }
                        break;
                    }
                    case ChunkState::kDataEnd: {
                        if (buffer_.size() - position_ < 2) {
                            return eof ? ParseResult::kError : ParseResult::kNeedMore;
                        }
                        if (buffer_[position_] != '\r' || buffer_[position_ + 1] != '\n') {
                            failed_ = true;
                            return ParseResult::kError;
                        }
                        position_ += 2;
                        chunkState_ = ChunkState::kSize;
                        break;
                    }
                    case ChunkState::kTrailers: {
                        std::size_t lineEnd = findCrlf(buffer_, position_);
                        if (lineEnd == std::string::npos) {
                            if (buffer_.size() - position_ > kMaxHeaderBytes) {
                                failed_ = true;
                                return ParseResult::kError;
                            }
                            return eof ? ParseResult::kError : ParseResult::kNeedMore;
                        }
                        if (lineEnd == position_) {
                            complete_ = true;
                            return ParseResult::kComplete;
                        }
                        std::string trailer(reinterpret_cast<const char*>(buffer_.data() + position_),
                                           lineEnd - position_);
                        if (!validHeaderLine(trailer)) {
                            failed_ = true;
                            return ParseResult::kError;
                        }
                        trailerBytes_ += lineEnd - position_ + 2;
                        if (trailerBytes_ > kMaxHeaderBytes) {
                            failed_ = true;
                            return ParseResult::kError;
                        }
                        position_ = lineEnd + 2;
                        break;
                    }
                }
            }
        }

        failed_ = true;
        return ParseResult::kError;
    }

    std::string takeBody() {
        return std::move(body_);
    }

private:
    enum class Framing {
        kNone,
        kLength,
        kChunked
    };

    enum class ChunkState {
        kSize,
        kData,
        kDataEnd,
        kTrailers
    };

    bool parseStatusLine(const std::string& line, int& minorVersion) const {
        if (line.size() < 12 || line.compare(0, 7, "HTTP/1.") != 0 ||
            (line[7] != '0' && line[7] != '1') || line[8] != ' ' ||
            !isAsciiDigit(line[9]) || !isAsciiDigit(line[10]) || !isAsciiDigit(line[11])) {
            return false;
        }
        if (line.size() > 12 && line[12] != ' ') {
            return false;
        }
        minorVersion = line[7] - '0';
        return line[9] == '2' && line[10] == '0' && line[11] == '0';
    }

    static bool validHeaderLine(const std::string& line) {
        std::size_t colon = line.find(':');
        if (colon == std::string::npos || colon == 0) {
            return false;
        }
        for (std::size_t index = 0; index < colon; ++index) {
            if (!isHeaderToken(line[index])) {
                return false;
            }
        }
        for (std::size_t index = colon + 1; index < line.size(); ++index) {
            unsigned char character = static_cast<unsigned char>(line[index]);
            if ((character < 0x20 && character != '\t') || character == 0x7f) {
                return false;
            }
        }
        return true;
    }

    static bool parseContentLength(const std::string& value, std::size_t& length) {
        std::string trimmed = trimOws(value);
        if (trimmed.empty()) {
            return false;
        }
        std::size_t parsed = 0;
        for (char character : trimmed) {
            if (!isAsciiDigit(character)) {
                return false;
            }
            std::size_t digit = static_cast<std::size_t>(character - '0');
            if (parsed > (kMaxResponseBytes - digit) / 10) {
                return false;
            }
            parsed = parsed * 10 + digit;
        }
        length = parsed;
        return true;
    }

    static bool parseTransferEncoding(const std::string& value) {
        std::size_t start = 0;
        while (start <= value.size()) {
            std::size_t comma = value.find(',', start);
            std::string token = trimOws(value.substr(start, comma == std::string::npos ? std::string::npos : comma - start));
            if (lowerAscii(std::move(token)) != "chunked") {
                return false;
            }
            if (comma == std::string::npos) {
                return true;
            }
            start = comma + 1;
        }
        return false;
    }

    bool parseHeaders(std::size_t headerEnd) {
        std::size_t statusEnd = findCrlf(buffer_, 0);
        if (statusEnd == std::string::npos || statusEnd == 0) {
            return false;
        }
        std::string status(reinterpret_cast<const char*>(buffer_.data()), statusEnd);
        if (!parseStatusLine(status, httpMinorVersion_)) {
            return false;
        }
        std::size_t lineStart = statusEnd + 2;
        std::size_t lineCount = 0;
        bool hasContentLength = false;
        bool hasTransferEncoding = false;
        while (lineStart < headerEnd - 2) {
            std::size_t lineEnd = findCrlf(buffer_, lineStart);
            if (lineEnd == std::string::npos || lineEnd == lineStart || lineEnd > headerEnd ||
                ++lineCount > kMaxHeaderLines) {
                return false;
            }
            std::string line(reinterpret_cast<const char*>(buffer_.data() + lineStart),
                             lineEnd - lineStart);
            if (!validHeaderLine(line)) {
                return false;
            }
            std::size_t colon = line.find(':');
            std::string name = lowerAscii(line.substr(0, colon));
            std::string value = trimOws(line.substr(colon + 1));
            if (name == "content-length") {
                if (hasContentLength || !parseContentLength(value, contentLength_)) {
                    return false;
                }
                hasContentLength = true;
            } else if (name == "transfer-encoding") {
                if (hasTransferEncoding || !parseTransferEncoding(value)) {
                    return false;
                }
                hasTransferEncoding = true;
            }
            lineStart = lineEnd + 2;
        }
        if ((lineStart != headerEnd && lineStart != headerEnd - 2) ||
            (hasContentLength && hasTransferEncoding)) {
            return false;
        }
        if (hasTransferEncoding) {
            if (httpMinorVersion_ != 1) {
                return false;
            }
            framing_ = Framing::kChunked;
            chunkState_ = ChunkState::kSize;
        } else if (hasContentLength) {
            framing_ = Framing::kLength;
        } else {
            return false;
        }
        return true;
    }

    static bool parseChunkSize(const unsigned char* data, std::size_t length, std::size_t& chunkSize) {
        if (data == nullptr || length == 0 || length > kMaxChunkLineBytes) {
            return false;
        }
        std::size_t extension = 0;
        while (extension < length && data[extension] != ';') {
            ++extension;
        }
        std::size_t first = 0;
        while (first < extension && (data[first] == ' ' || data[first] == '\t')) {
            ++first;
        }
        std::size_t last = extension;
        while (last > first && (data[last - 1] == ' ' || data[last - 1] == '\t')) {
            --last;
        }
        if (first == last) {
            return false;
        }
        std::size_t parsed = 0;
        for (std::size_t index = first; index < last; ++index) {
            char character = static_cast<char>(data[index]);
            if (!isHex(character)) {
                return false;
            }
            std::size_t digit = character <= '9' ? static_cast<std::size_t>(character - '0') :
                static_cast<std::size_t>((lowerAscii(character) >= 'a' ? lowerAscii(character) - 'a' + 10 :
                    lowerAscii(character) - 'A' + 10));
            if (parsed > (kMaxResponseBytes - digit) / 16) {
                return false;
            }
            parsed = parsed * 16 + digit;
        }
        if (parsed > kMaxResponseBytes) {
            return false;
        }
        chunkSize = parsed;
        return true;
    }

    bool appendBody(const unsigned char* data, std::size_t length) {
        if (length > kMaxResponseBytes - body_.size()) {
            return false;
        }
        body_.append(reinterpret_cast<const char*>(data), length);
        return true;
    }

    std::vector<unsigned char> buffer_;
    std::string body_;
    std::size_t position_ = 0;
    std::size_t contentLength_ = 0;
    std::size_t chunkRemaining_ = 0;
    std::size_t trailerBytes_ = 0;
    int httpMinorVersion_ = 1;
    bool headersParsed_ = false;
    bool complete_ = false;
    bool failed_ = false;
    Framing framing_ = Framing::kNone;
    ChunkState chunkState_ = ChunkState::kSize;
};

bool readHttpResponse(mbedtls_ssl_context* ssl, BioContext& bio, std::string& body) {
    HttpResponseParser parser;
    for (;;) {
        ParseResult result = parser.consume(false);
        if (result == ParseResult::kComplete) {
            body = parser.takeBody();
            return true;
        }
        if (result == ParseResult::kError || std::chrono::steady_clock::now() >= bio.deadline) {
            return false;
        }
        unsigned char buffer[4096];
        int readResult;
        do {
            readResult = mbedtls_ssl_read(ssl, buffer, sizeof(buffer));
            if (readResult == MBEDTLS_ERR_SSL_WANT_READ || readResult == MBEDTLS_ERR_SSL_WANT_WRITE) {
                if (std::chrono::steady_clock::now() >= bio.deadline) {
                    return false;
                }
            }
        } while (readResult == MBEDTLS_ERR_SSL_WANT_READ || readResult == MBEDTLS_ERR_SSL_WANT_WRITE);
        if (readResult == 0) {
            result = parser.consume(true);
            if (result == ParseResult::kComplete) {
                body = parser.takeBody();
                return true;
            }
            return false;
        }
        if (readResult < 0 || !parser.append(buffer, static_cast<std::size_t>(readResult))) {
            return false;
        }
    }
}

bool decodeUtf8(const std::string& input, std::u16string& output) {
    output.clear();
    output.reserve(input.size());
    std::size_t index = 0;
    while (index < input.size()) {
        unsigned char first = static_cast<unsigned char>(input[index++]);
        std::uint32_t codePoint = 0;
        if (first <= 0x7f) {
            codePoint = first;
        } else if (first >= 0xc2 && first <= 0xdf) {
            if (index >= input.size()) {
                return false;
            }
            unsigned char second = static_cast<unsigned char>(input[index++]);
            if ((second & 0xc0) != 0x80) {
                return false;
            }
            codePoint = static_cast<std::uint32_t>(first & 0x1f) << 6 |
                static_cast<std::uint32_t>(second & 0x3f);
        } else if (first >= 0xe0 && first <= 0xef) {
            if (index + 1 >= input.size()) {
                return false;
            }
            unsigned char second = static_cast<unsigned char>(input[index++]);
            unsigned char third = static_cast<unsigned char>(input[index++]);
            if ((second & 0xc0) != 0x80 || (third & 0xc0) != 0x80 ||
                (first == 0xe0 && second < 0xa0) || (first == 0xed && second >= 0xa0)) {
                return false;
            }
            codePoint = static_cast<std::uint32_t>(first & 0x0f) << 12 |
                static_cast<std::uint32_t>(second & 0x3f) << 6 |
                static_cast<std::uint32_t>(third & 0x3f);
        } else if (first >= 0xf0 && first <= 0xf4) {
            if (index + 2 >= input.size()) {
                return false;
            }
            unsigned char second = static_cast<unsigned char>(input[index++]);
            unsigned char third = static_cast<unsigned char>(input[index++]);
            unsigned char fourth = static_cast<unsigned char>(input[index++]);
            if ((second & 0xc0) != 0x80 || (third & 0xc0) != 0x80 || (fourth & 0xc0) != 0x80 ||
                (first == 0xf0 && second < 0x90) || (first == 0xf4 && second >= 0x90)) {
                return false;
            }
            codePoint = static_cast<std::uint32_t>(first & 0x07) << 18 |
                static_cast<std::uint32_t>(second & 0x3f) << 12 |
                static_cast<std::uint32_t>(third & 0x3f) << 6 |
                static_cast<std::uint32_t>(fourth & 0x3f);
        } else {
            return false;
        }
        if (codePoint > 0x10ffff || (codePoint >= 0xd800 && codePoint <= 0xdfff)) {
            return false;
        }
        if (codePoint <= 0xffff) {
            output.push_back(static_cast<char16_t>(codePoint));
        } else {
            codePoint -= 0x10000;
            output.push_back(static_cast<char16_t>(0xd800 + (codePoint >> 10)));
            output.push_back(static_cast<char16_t>(0xdc00 + (codePoint & 0x3ff)));
        }
    }
    return true;
}

jstring newJavaString(JNIEnv* env, const std::string& response) {
    std::u16string utf16;
    if (!decodeUtf8(response, utf16) || utf16.size() > static_cast<std::size_t>(std::numeric_limits<jsize>::max())) {
        return nullptr;
    }
    return env->NewString(reinterpret_cast<const jchar*>(utf16.data()), static_cast<jsize>(utf16.size()));
}

bool makeRequest(const Endpoint& endpoint, const std::string& token, std::string& request) {
    if (!isValidAppCheckToken(token)) {
        return false;
    }
    request.clear();
    request.reserve(endpoint.path.size() + token.size() + 256);
    request += "GET ";
    request += endpoint.path;
    request += " HTTP/1.1\r\nHost: ";
    request += endpoint.hostHeader;
    request += "\r\nConnection: close\r\nUser-Agent: ReelShortNative/1.0\r\nAccept: application/json\r\nAccept-Encoding: identity\r\nX-Firebase-AppCheck: ";
    request += token;
    request += "\r\n\r\n";
    return true;
}

}

extern "C" JNIEXPORT jstring JNICALL
Java_com_dramathon_dramathonplus_client_NativeReelShortSecretProvider_fetchJson(
    JNIEnv* env,
    jobject,
    jstring endpoint,
    jobjectArray caCertificates,
    jstring appCheckToken) {
    if (env == nullptr) {
        return nullptr;
    }
    try {
        std::string endpointText;
        std::string token;
        if (!readJniString(env, endpoint, kMaxEndpointBytes, endpointText) ||
            !readJniString(env, appCheckToken, kMaxTokenBytes, token) ||
            !isValidAppCheckToken(token)) {
            return failure(env);
        }
        Endpoint parsedEndpoint;
        if (!parseEndpoint(endpointText, parsedEndpoint)) {
            return failure(env);
        }
        std::string request;
        if (!makeRequest(parsedEndpoint, token, request)) {
            return failure(env);
        }

        SocketFd socket;
        MbedTlsState tls;
        if (!loadCertificates(env, caCertificates, &tls.roots) || !tls.initialize() ||
            mbedtls_ssl_set_hostname(&tls.ssl, parsedEndpoint.host.c_str()) != 0) {
            return failure(env);
        }
        if (!connectHost(parsedEndpoint, socket)) {
            return failure(env);
        }

        BioContext bio;
        bio.fd = socket.get();
        bio.deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(kHandshakeTimeoutMs);
        mbedtls_ssl_set_bio(&tls.ssl, &bio, bioSend, nullptr, bioReceiveTimeout);
        int handshakeResult;
        do {
            handshakeResult = mbedtls_ssl_handshake(&tls.ssl);
            if (handshakeResult == MBEDTLS_ERR_SSL_WANT_READ ||
                handshakeResult == MBEDTLS_ERR_SSL_WANT_WRITE) {
                if (std::chrono::steady_clock::now() >= bio.deadline) {
                    return failure(env);
                }
            }
        } while (handshakeResult == MBEDTLS_ERR_SSL_WANT_READ ||
                 handshakeResult == MBEDTLS_ERR_SSL_WANT_WRITE);
        if (handshakeResult != 0 || mbedtls_ssl_get_verify_result(&tls.ssl) != 0) {
            return failure(env);
        }

        bio.deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(kWriteTimeoutMs);
        if (!writeTlsData(&tls.ssl, request, bio)) {
            return failure(env);
        }
        bio.deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(kReadTimeoutMs);
        std::string response;
        if (!readHttpResponse(&tls.ssl, bio, response)) {
            return failure(env);
        }
        jstring result = newJavaString(env, response);
        if (result == nullptr) {
            return failure(env);
        }
        return result;
    } catch (...) {
        return failure(env);
    }
}
