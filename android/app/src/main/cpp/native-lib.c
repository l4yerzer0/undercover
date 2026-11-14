// JNI bridge to the vendored ByeDPI core.
//
// Adapted from ByeByeDPI (https://github.com/romanvht/ByeByeDPI, GPL-3.0),
// app/src/main/cpp/native-lib.c. Only the JNI symbol names differ: they are
// bound to com.undercover.vpn.byedpi.ByeDpiProxy.

#include <string.h>

#include <jni.h>
#include <getopt.h>
#include <signal.h>
#include <setjmp.h>
#include <stdlib.h>

#include "byedpi/error.h"
#include "main.h"

extern int server_fd;
static int g_proxy_running = 0;

// Socket protection bridge. ByeDPI dials out with plain sockets from inside this
// process; without VpnService.protect() they would re-enter the tunnel, and
// sing-box would route them straight back into ByeDPI.
//
// The class reference has to be resolved in JNI_OnLoad: FindClass from a thread
// attached later runs under the system class loader and cannot see app classes.
static JavaVM *g_vm = NULL;
static jclass g_protector_class = NULL;
static jmethodID g_protect_method = NULL;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, __attribute__((unused)) void *reserved) {
    JNIEnv *env = NULL;
    g_vm = vm;

    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass local = (*env)->FindClass(env, "com/undercover/vpn/byedpi/ByeDpiSocketProtector");
    if (local == NULL) {
        (*env)->ExceptionClear(env);
        LOG(LOG_S, "protector class not found, sockets will not be protected");
        return JNI_VERSION_1_6;
    }

    g_protector_class = (*env)->NewGlobalRef(env, local);
    g_protect_method = (*env)->GetStaticMethodID(env, g_protector_class, "protectSocket", "(I)Z");
    (*env)->DeleteLocalRef(env, local);

    if (g_protect_method == NULL) {
        (*env)->ExceptionClear(env);
        LOG(LOG_S, "protectSocket method not found");
    }

    return JNI_VERSION_1_6;
}

int byedpi_protect_fd(int fd) {
    if (fd < 0 || g_vm == NULL || g_protector_class == NULL || g_protect_method == NULL) {
        return 0;
    }

    JNIEnv *env = NULL;
    int attached = 0;
    jint status = (*g_vm)->GetEnv(g_vm, (void **) &env, JNI_VERSION_1_6);

    if (status == JNI_EDETACHED) {
        if ((*g_vm)->AttachCurrentThread(g_vm, &env, NULL) != JNI_OK) {
            LOG(LOG_S, "failed to attach thread for protect(fd=%d)", fd);
            return 0;
        }
        attached = 1;
    } else if (status != JNI_OK) {
        return 0;
    }

    jboolean ok = (*env)->CallStaticBooleanMethod(env, g_protector_class, g_protect_method, (jint) fd);

    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        ok = JNI_FALSE;
    }

    if (attached) {
        (*g_vm)->DetachCurrentThread(g_vm);
    }

    if (ok != JNI_TRUE) {
        LOG(LOG_S, "protect failed for fd=%d", fd);
    }

    return ok == JNI_TRUE;
}

struct params default_params = {
        .await_int = 10,
        .ipv6 = 1,
        .resolve = 1,
        .udp = 1,
        .max_open = 512,
        .bfsize = 16384,
        .baddr = {
            .in6 = { .sin6_family = AF_INET6 }
        },
        .laddr = {
            .in = { .sin_family = AF_INET }
        },
        .debug = 0
};

static void reset_params(void) {
    clear_params(NULL, NULL);
    params = default_params;
}

JNIEXPORT jint JNICALL
Java_com_undercover_vpn_byedpi_ByeDpiProxy_jniStartProxy(JNIEnv *env, __attribute__((unused)) jobject thiz, jobjectArray args) {
    if (g_proxy_running) {
        LOG(LOG_S, "proxy already running");
        return -1;
    }

    int argc = (*env)->GetArrayLength(env, args);
    char **argv = calloc(argc, sizeof(char *));

    if (!argv) {
        LOG(LOG_S, "failed to allocate memory for argv");
        return -1;
    }

    for (int i = 0; i < argc; i++) {
        jstring arg = (jstring) (*env)->GetObjectArrayElement(env, args, i);

        if (!arg) {
            argv[i] = NULL;
            continue;
        }

        const char *arg_str = (*env)->GetStringUTFChars(env, arg, 0);
        argv[i] = arg_str ? strdup(arg_str) : NULL;

        if (arg_str) (*env)->ReleaseStringUTFChars(env, arg, arg_str);

        (*env)->DeleteLocalRef(env, arg);
    }

    LOG(LOG_S, "starting proxy with %d args", argc);
    reset_params();
    g_proxy_running = 1;
    optind = 1;

    int result = main(argc, argv);

    LOG(LOG_S, "proxy return code %d", result);
    g_proxy_running = 0;

    for (int i = 0; i < argc; i++) free(argv[i]);
    free(argv);

    return result;
}

JNIEXPORT jint JNICALL
Java_com_undercover_vpn_byedpi_ByeDpiProxy_jniStopProxy(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jobject thiz) {
    LOG(LOG_S, "send shutdown to proxy");

    if (!g_proxy_running) {
        LOG(LOG_S, "proxy is not running");
        return -1;
    }

    shutdown(server_fd, SHUT_RDWR);
    g_proxy_running = 0;

    return 0;
}

JNIEXPORT jint JNICALL
Java_com_undercover_vpn_byedpi_ByeDpiProxy_jniForceClose(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jobject thiz) {
    LOG(LOG_S, "closing server socket (fd: %d)", server_fd);

    if (close(server_fd) == -1) {
        LOG(LOG_S, "failed to close server socket (fd: %d)", server_fd);
        return -1;
    }

    LOG(LOG_S, "proxy socket force close");
    g_proxy_running = 0;

    return 0;
}
