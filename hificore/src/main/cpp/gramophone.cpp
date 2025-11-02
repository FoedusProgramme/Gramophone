/*
 *     Copyright (C) 2011 The Android Open Source Project
 *                   2025 nift4
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#define LOG_TAG "AudioTrackHalInfo(JNI)"

#include <jni.h>
#include "android_linker_ns.h"
#include "audio-legacy.h"
#include <dlfcn.h>
#include <android/log_macros.h>
#include <cstdlib>
#include <string>
#include <vector>
#include <unistd.h>
#include <thread>
#include <dlfunc.h>
#include "helpers.h"

static bool init_done = false;
static jmethodID nativeGetFlags = nullptr;
void *libaudioclient_handle = nullptr;
void* libpermission_handle = nullptr;
void* libandroid_runtime_handle = nullptr;
void* libutils_handle = nullptr;
void* libavenhancements_handle = nullptr;
void* libbinder_handle = nullptr;
typedef int audio_io_handle_t;

typedef audio_io_handle_t(*ZNK7android10AudioTrack9getOutputEv_t)(void *);

static ZNK7android10AudioTrack9getOutputEv_t ZNK7android10AudioTrack9getOutputEv = nullptr;

typedef uint32_t(*ZNK7android10AudioTrack16getHalSampleRateEv_t)(void *);

static ZNK7android10AudioTrack16getHalSampleRateEv_t ZNK7android10AudioTrack16getHalSampleRateEv = nullptr;

typedef uint32_t(*ZNK7android10AudioTrack18getHalChannelCountEv_t)(void *);

static ZNK7android10AudioTrack18getHalChannelCountEv_t ZNK7android10AudioTrack18getHalChannelCountEv = nullptr;

typedef uint32_t(*ZNK7android10AudioTrack12getHalFormatEv_t)(void *);

static ZNK7android10AudioTrack12getHalFormatEv_t ZNK7android10AudioTrack12getHalFormatEv = nullptr;
typedef int32_t status_t;

typedef status_t(*ZN7android11AudioSystem12getAudioPortEP13audio_port_v7_t)(void *port);

static ZN7android11AudioSystem12getAudioPortEP13audio_port_v7_t ZN7android11AudioSystem12getAudioPortEP13audio_port_v7 = nullptr;

typedef uint32_t(*ZNK7android10AudioTrack4dumpEiRKNS_6VectorINS_8String16EEE_t)(void *, int,
                                                                                void *);

static ZNK7android10AudioTrack4dumpEiRKNS_6VectorINS_8String16EEE_t ZNK7android10AudioTrack4dumpEiRKNS_6VectorINS_8String16EEE = nullptr;

typedef status_t(*ZN7android11AudioSystem14listAudioPortsE17audio_port_role_t17audio_port_type_tPjP13audio_port_v7S3__t)(
        LEGACY_audio_port_role_t, LEGACY_audio_port_type_t, unsigned int *, void *, unsigned int *);

static ZN7android11AudioSystem14listAudioPortsE17audio_port_role_t17audio_port_type_tPjP13audio_port_v7S3__t ZN7android11AudioSystem14listAudioPortsE17audio_port_role_t17audio_port_type_tPjP13audio_port_v7S3_ = nullptr;
static intptr_t gIoHandleOffset = 0;
static intptr_t gIoHandle2Offset = 0;
static intptr_t gTrackFlagsOffset = 0;

typedef bool(*ZN7android18ExtendedMediaUtils26AudioTrackIsTrackOffloadedEi_t)(void* thisptr, uint32_t output);
static ZN7android18ExtendedMediaUtils26AudioTrackIsTrackOffloadedEi_t ZN7android18ExtendedMediaUtils26AudioTrackIsTrackOffloadedEi = nullptr;

typedef int32_t(*ZN7android11AudioEffect10getConfigsEP17audio_config_baseS2__t)(void* thisptr, void* c1, void* c2);
static ZN7android11AudioEffect10getConfigsEP17audio_config_baseS2__t ZN7android11AudioEffect10getConfigsEP17audio_config_baseS2_ = nullptr;

bool initLib(JNIEnv *env) {
    if (init_done)
        return true;
    if (android_get_device_api_level() < 24) {
        if (!libaudioclient_handle) {
            libaudioclient_handle = dlopen("libmedia.so", RTLD_GLOBAL);
            if (libaudioclient_handle == nullptr) {
                ALOGE("dlopen returned nullptr for libmedia.so: %s", dlerror());
                return false;
            }
        }
        if (!libutils_handle) {
            libutils_handle = dlopen("libutils.so", RTLD_GLOBAL);
            if (libutils_handle == nullptr) {
                ALOGE("dlopen returned nullptr for libutils.so: %s", dlerror());
                return false;
            }
        }
        if (!libbinder_handle) {
            libbinder_handle = dlopen("libbinder.so", RTLD_GLOBAL);
            if (libbinder_handle == nullptr) {
                ALOGE("dlopen returned nullptr for libbinder.so: %s", dlerror());
                return false;
            }
        }
        init_done = true;
        return true;
    }
    linkernsbypass_load(env);
    if (android_get_device_api_level() < 26) {
        if (!libaudioclient_handle) {
            libaudioclient_handle = dlfunc_dlopen(env, "libmedia.so", RTLD_GLOBAL);
            if (libaudioclient_handle == nullptr) {
                ALOGE("dlopen returned nullptr for libmedia.so: %s", dlerror());
                return false;
            }
        }
        if (!libutils_handle) {
            libutils_handle = dlfunc_dlopen(env, "libutils.so", RTLD_GLOBAL);
            if (libutils_handle == nullptr) {
                ALOGE("dlopen returned nullptr for libutils.so: %s", dlerror());
                return false;
            }
        }
        if (!libavenhancements_handle) {
            libavenhancements_handle = dlfunc_dlopen(env, "libavenhancements.so", RTLD_GLOBAL);
            if (libavenhancements_handle == nullptr) {
                ALOGI("dlopen returned nullptr for libavenhancements.so: %s", dlerror());
                // this lib is optional
            }
        }
        if (!libbinder_handle) {
            libbinder_handle = dlfunc_dlopen(env, "libbinder.so", RTLD_GLOBAL);
            if (libbinder_handle == nullptr) {
                ALOGE("dlopen returned nullptr for libbinder.so: %s", dlerror());
                return false;
            }
        }
        init_done = true;
        return true;
    }
    if (!linkernsbypass_load_status()) {
        ALOGE("linker namespace bypass init failed");
        return false;
    }
    android_namespace_t *ns = android_create_namespace_escape("default_copy", nullptr, nullptr,
                                                              ANDROID_NAMESPACE_TYPE_SHARED,
                                                              nullptr, nullptr);
    if (!libaudioclient_handle) {
        libaudioclient_handle = linkernsbypass_namespace_dlopen("libaudioclient.so", RTLD_GLOBAL, ns);
        if (libaudioclient_handle == nullptr) {
            ALOGE("dlopen returned nullptr for libaudioclient.so: %s", dlerror());
            return false;
        }
    }
    if (!libutils_handle) {
        libutils_handle = linkernsbypass_namespace_dlopen("libutils.so", RTLD_GLOBAL, ns);
        if (libutils_handle == nullptr) {
            ALOGE("dlopen returned nullptr for libutils.so: %s", dlerror());
            return false;
        }
    }
    if (android_get_device_api_level() >= 31 && !libpermission_handle) {
        libpermission_handle = linkernsbypass_namespace_dlopen("libpermission.so", RTLD_GLOBAL, ns);
        if (libpermission_handle == nullptr) {
            ALOGE("dlopen returned nullptr for libpermission.so: %s", dlerror());
            return false;
        }
    }
    if (android_get_device_api_level() >= 31 && !libandroid_runtime_handle) {
        libandroid_runtime_handle = linkernsbypass_namespace_dlopen("libandroid_runtime.so", RTLD_GLOBAL, ns);
        if (libandroid_runtime_handle == nullptr) {
            ALOGE("dlopen returned nullptr for libandroid_runtime.so: %s", dlerror());
            return false;
        }
    }
    if (!libbinder_handle) {
        libbinder_handle = linkernsbypass_namespace_dlopen("libbinder.so", RTLD_GLOBAL, ns);
        if (libbinder_handle == nullptr) {
            ALOGE("dlopen returned nullptr for libbinder.so: %s", dlerror());
            return false;
        }
    }
    init_done = true;
    return true;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_nift4_gramophone_hificore_AudioTrackHiddenApi_getHalSampleRateInternal(
        JNIEnv *env, jobject, jlong audioTrack) {
    if (!initLib(env))
        return 0;
    DLSYM_OR_RETURN(libaudioclient, ZNK7android10AudioTrack16getHalSampleRateEv, 0)
    return (int32_t) ZNK7android10AudioTrack16getHalSampleRateEv((void *) audioTrack);
}

extern "C" JNIEXPORT jint JNICALL
Java_org_nift4_gramophone_hificore_AudioTrackHiddenApi_getHalChannelCountInternal(
        JNIEnv *env, jobject, jlong audioTrack) {
    if (!initLib(env))
        return 0;
    DLSYM_OR_RETURN(libaudioclient, ZNK7android10AudioTrack18getHalChannelCountEv, 0)
    return (int32_t) ZNK7android10AudioTrack18getHalChannelCountEv((void *) audioTrack);
}

extern "C" JNIEXPORT jint JNICALL
Java_org_nift4_gramophone_hificore_AudioTrackHiddenApi_getHalFormatInternal(
        JNIEnv *env, jobject, jlong audioTrack) {
    if (!initLib(env))
        return 0;
    DLSYM_OR_RETURN(libaudioclient, ZNK7android10AudioTrack12getHalFormatEv, 0)
    return (int32_t) ZNK7android10AudioTrack12getHalFormatEv((void *) audioTrack);
}

extern "C" JNIEXPORT jint JNICALL
Java_org_nift4_gramophone_hificore_AudioTrackHiddenApi_getOutputInternal(
        JNIEnv *env, jobject, jlong audioTrack) {
    if (!initLib(env))
        return 0;
    DLSYM_OR_RETURN(libaudioclient, ZNK7android10AudioTrack9getOutputEv, 0)
    return ZNK7android10AudioTrack9getOutputEv((void *) audioTrack);
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_nift4_gramophone_hificore_AudioTrackHiddenApi_dumpInternal(
        JNIEnv *env, jobject, jlong audioTrack) {
    if (!initLib(env))
        return nullptr;
    DLSYM_OR_RETURN(libaudioclient, ZNK7android10AudioTrack4dumpEiRKNS_6VectorINS_8String16EEE, nullptr)
    int pipe_fds[2];
    if (pipe(pipe_fds) == -1) {
        ALOGE("pipe() syscall failed");
        return nullptr;
    }

    std::string result;
    std::thread reader_thread([&] {
        char buffer[128];
        ssize_t bytes_read;
        while ((bytes_read = read(pipe_fds[0], buffer, sizeof(buffer) - 1)) > 0) {
            buffer[bytes_read] = '\0';
            result += buffer;
        }
        close(pipe_fds[0]);
    });

    // last argument is not allowed to be null, but where will we get a vector?
    ZNK7android10AudioTrack4dumpEiRKNS_6VectorINS_8String16EEE((void *) audioTrack, pipe_fds[1],
                                                               (void *) 0xdeadbaad);
    close(pipe_fds[1]);

    reader_thread.join();
    return env->NewStringUTF(result.c_str());
}

struct audio_gain_config {
    [[maybe_unused]] int index;             /* index of the corresponding audio_gain in the
                                                                audio_port gains[] table */
    [[maybe_unused]] /*audio_gain_mode_t*/uint32_t mode;              /* mode requested for this command */
    [[maybe_unused]] /*audio_channel_mask_t*/uint32_t channel_mask;      /* channels which gain value follows.
                                                                            N/A in joint mode */

    // note this "8" is not FCC_8, so it won't need to be changed for > 8 channels
    [[maybe_unused]] int values[sizeof(/*audio_channel_mask_t*/uint32_t) * 8]; /* gain values in millibels
													                                               for each channel ordered from LSb to MSb in
													                                               channel mask. The number of values is 1 in joint
													                                               mode or __builtin_popcount(channel_mask) */
    [[maybe_unused]] unsigned int ramp_duration_ms; /* ramp duration in ms */
};

extern "C"
JNIEXPORT jintArray JNICALL
Java_org_nift4_gramophone_hificore_AudioSystemHiddenApi_findAfFlagsForPortInternal(
        JNIEnv *env, jobject, jint id, jint io) {
    if (!initLib(env))
        return nullptr;
    jint out[6] = { 0, 0, 0, 0, 0, 0 };
    if (android_get_device_api_level() >= 28) {
        DLSYM_OR_ELSE(libaudioclient, ZN7android11AudioSystem12getAudioPortEP13audio_port_v7) {
            ZN7android11AudioSystem12getAudioPortEP13audio_port_v7 =
                    (ZN7android11AudioSystem12getAudioPortEP13audio_port_v7_t)
                    dlsym(libaudioclient_handle, "_ZN7android11AudioSystem12getAudioPortEP10audio_port");
            if (!ZN7android11AudioSystem12getAudioPortEP13audio_port_v7) {
                ALOGE("dlsym returned nullptr for _ZN7android11AudioSystem12getAudioPortEP10audio_port: %s",
                                    dlerror());
                return nullptr;
            }
        }
#define BUFFER_SIZE 114000
        auto buffer = (uint8_t *) calloc(1, BUFFER_SIZE); // should be plenty
        *((int * /*audio_port_handle_t*/) buffer) = id;
        ZN7android11AudioSystem12getAudioPortEP13audio_port_v7(buffer);
        uint8_t *pos = buffer;
        if (gIoHandleOffset == 0) {
            int i = 1; // skip over port.ext.mix.handle, we want port.active_config.ext.mix.handle
            pos += BUFFER_SIZE;
            while (buffer < pos) {
                pos -= sizeof(unsigned int) / sizeof(uint8_t);
                if (buffer < pos && *((unsigned int *) pos) == io) {
                    if (i-- == 1) {
						gIoHandle2Offset = buffer < pos ? pos - buffer : 0;
                    } else
                        break;
                }
            }
            gIoHandleOffset = buffer < pos ? pos - buffer : 0;
        } else {
            pos += gIoHandleOffset;
        }
        if (gIoHandleOffset == 0 || gIoHandleOffset >= gIoHandle2Offset) {
            ALOGE("bad gIoHandleOffset(%d) gIoHandle2Offset(%d) (BUFFER_SIZE(%d) id(%d) io(%d))",
                  gIoHandleOffset, gIoHandle2Offset, BUFFER_SIZE, id, io);
            gIoHandleOffset = 0;
            free(buffer);
            return nullptr;
        }
        if (buffer >= pos) {
            ALOGE("buffer(%p) >= pos(%p) (BUFFER_SIZE(%d) id(%d) io(%d))",
                  buffer, pos, BUFFER_SIZE, id, io);
            gIoHandleOffset = 0;
            free(buffer);
            return nullptr;
        }
	    uint8_t *maxPos;
	    if (android_get_device_api_level() < 33) {
		    maxPos = buffer + gIoHandle2Offset + sizeof(uint32_t) / sizeof(uint8_t);
	    } else {
			maxPos = pos;
		}
	    if (maxPos >= buffer + BUFFER_SIZE) {
		    ALOGE("maxPos(%p) >= buffer(%p) + BUFFER_SIZE(%d) (id(%d) io(%d))",
		          maxPos, buffer, BUFFER_SIZE, id, io);
		    gIoHandleOffset = 0;
		    free(buffer);
		    return nullptr;
	    }
#undef BUFFER_SIZE
	    if (android_get_device_api_level() < 33) { // not populated by AudioAidlConversion on T+
		    out[5] = (int32_t) (*((uint32_t *) maxPos)); // port.ext.mix.latency_class
	    }
        /*
         * unsigned int             sample_rate;       <--- we want to go here
         * audio_channel_mask_t     channel_mask;
         * audio_format_t           format;
         * struct audio_gain_config gain;
         * union audio_io_flags     flags;  (only >=R)
         * audio_module_handle_t    hw_module;
         * audio_io_handle_t        handle;            <--- we are here
         */
        pos -= sizeof(uint32_t) / sizeof(uint8_t); // audio_io_handle_t (handle)
        out[4] = (int32_t) (*((uint32_t *) pos));
        if (android_get_device_api_level() >= 30) {
            pos -= sizeof(uint32_t) / sizeof(uint8_t); // union audio_io_flags (flags)
            // R added flags field to struct, but it is only populated since T.
            out[3] = (int32_t) (*((uint32_t *) pos));
        }
        pos -= sizeof(struct audio_gain_config) / sizeof(uint8_t); // audio_gain_config (gain)
        pos -= sizeof(uint32_t) / sizeof(uint8_t); // audio_format_t (format)
        out[2] = (int32_t) (*((uint32_t *) pos));
        pos -= sizeof(uint32_t) / sizeof(uint8_t); // audio_channel_mask_t (channel_mask)
        out[1] = (int32_t) (*((uint32_t *) pos));
        pos -= sizeof(unsigned int) / sizeof(uint8_t); // unsigned int (sample_rate)
        out[0] = (int32_t) (*((uint32_t *) pos));
        free(buffer);
    } else {
        DLSYM_OR_ELSE(libaudioclient, ZN7android11AudioSystem14listAudioPortsE17audio_port_role_t17audio_port_type_tPjP13audio_port_v7S3_) {
            ZN7android11AudioSystem14listAudioPortsE17audio_port_role_t17audio_port_type_tPjP13audio_port_v7S3_ =
                    (ZN7android11AudioSystem14listAudioPortsE17audio_port_role_t17audio_port_type_tPjP13audio_port_v7S3__t)
                    dlsym(libaudioclient_handle, "_ZN7android11AudioSystem14listAudioPortsE17audio_port_role_t17audio_port_type_tPjP10audio_portS3_");
            if (!ZN7android11AudioSystem14listAudioPortsE17audio_port_role_t17audio_port_type_tPjP13audio_port_v7S3_) {
                ALOGE("dlsym returned nullptr for ZN7android11AudioSystem14listAudioPortsE17"
                      "audio_port_role_t17audio_port_type_tPjP13audio_port_v7S3_: %s",
                      dlerror());
                return nullptr;
            }
        }
        // based on AOSP code
        const bool oreo = android_get_device_api_level() >= 26;
        status_t status;
        unsigned int generation1 = 0;
        unsigned int generation = 0;
        unsigned int numPorts = 0;
        void* nPorts = nullptr;
        int attempts = 5;
        // get the port count and all the ports until they both return the same generation
        do {
            if (attempts-- < 0) {
                ALOGE("AudioSystem::listAudioPorts no attempts left");
                if (nPorts != nullptr) {
                    if (oreo)
                        delete (std::vector<audio_port_oreo>*)nPorts;
                    else
                        delete (std::vector<audio_port_legacy>*)nPorts;
                }
                return nullptr;
            }

            numPorts = 0;
            status = ZN7android11AudioSystem14listAudioPortsE17audio_port_role_t17audio_port_type_tPjP13audio_port_v7S3_(
                    LEGACY_AUDIO_PORT_ROLE_SOURCE, LEGACY_AUDIO_PORT_TYPE_MIX, &numPorts,
                    nullptr, &generation1);
            if (status != 0) {
                ALOGE("AudioSystem::listAudioPorts error %d", status);
                if (nPorts != nullptr) {
                    if (oreo)
                        delete (std::vector<audio_port_oreo>*)nPorts;
                    else
                        delete (std::vector<audio_port_legacy>*)nPorts;
                }
                return nullptr;
            }
            if (numPorts == 0) {
                ALOGE("AudioSystem::listAudioPorts found no ports");
                if (nPorts != nullptr) {
                    if (oreo)
                        delete (std::vector<audio_port_oreo>*)nPorts;
                    else
                        delete (std::vector<audio_port_legacy>*)nPorts;
                }
                return nullptr;
            }
            // Tuck on double the space to prevent heap corruption if OEM made the audio_port bigger
            if (nPorts == nullptr) {
                if (oreo)
                    nPorts = new std::vector<audio_port_oreo>(numPorts * 2);
                else
                    nPorts = new std::vector<audio_port_legacy>(numPorts * 2);
            } else {
                if (oreo)
                    ((std::vector<audio_port_oreo> *) nPorts)->resize(numPorts * 2);
                else
                    ((std::vector<audio_port_legacy>*)nPorts)->resize(numPorts * 2);
            }
            status = ZN7android11AudioSystem14listAudioPortsE17audio_port_role_t17audio_port_type_tPjP13audio_port_v7S3_(
                    LEGACY_AUDIO_PORT_ROLE_SOURCE, LEGACY_AUDIO_PORT_TYPE_MIX, &numPorts,
                    oreo ? (void*)&((*(std::vector<audio_port_oreo>*)nPorts)[0]) :
                    (void*)&((*(std::vector<audio_port_legacy>*)nPorts)[0]), &generation);
        } while (generation1 != generation && status == 0);

        int i = 0;
        bool found = false;
        if (oreo) {
            for (auto port: *(std::vector<audio_port_oreo>*)nPorts) {
                if (i++ == numPorts) break; // needed because vector size > numPorts
                if (port.id == id && port.active_config.ext.mix.handle == io && port.ext.mix.handle == io) {
                    out[0] = (int32_t) port.active_config.sample_rate;
                    out[1] = (int32_t) port.active_config.format;
                    out[2] = (int32_t) port.active_config.channel_mask;
                    // out[3] / port.active_config.flags missing in action
                    out[4] = (int32_t) port.active_config.ext.mix.hw_module;
                    out[5] = (int32_t) port.ext.mix.latency_class;
                    found = true;
                    break;
                }
            }
            delete (std::vector<audio_port_oreo>*)nPorts;
        } else {
            for (auto port: *(std::vector<audio_port_legacy>*)nPorts) {
                if (i++ == numPorts) break; // needed because vector size > numPorts
                if (port.id == id && port.active_config.ext.mix.handle == io && port.ext.mix.handle == io) {
                    out[0] = (int32_t) port.active_config.sample_rate;
                    out[1] = (int32_t) port.active_config.format;
                    out[2] = (int32_t) port.active_config.channel_mask;
                    // out[3] / port.active_config.flags missing in action
                    out[4] = (int32_t) port.active_config.ext.mix.hw_module;
                    out[5] = (int32_t) port.ext.mix.latency_class;
                    found = true;
                    break;
                }
            }
            delete (std::vector<audio_port_legacy>*)nPorts;
        }
        if (!found) return nullptr;
    }
    jintArray theOut = env->NewIntArray(sizeof(out)/sizeof(out[0]));
    if (theOut == nullptr) {
        return nullptr;
    }
    env->SetIntArrayRegion(theOut, 0, sizeof(out)/sizeof(out[0]), &out[0]);
    return theOut;
}

struct audio_track_partial { // use struct for automatic alignment handling
    uint32_t mAfLatency;
    size_t mAfFrameCount;
    uint32_t mAfSampleRate;
    [[maybe_unused]] uint32_t mAfChannelCount;
    [[maybe_unused]] uint32_t mAfFormat;
    uint32_t mAfTrackFlags;
    uint32_t mFormat;
};

extern "C"
JNIEXPORT jint JNICALL
Java_org_nift4_gramophone_hificore_AudioTrackHiddenApi_findAfTrackFlagsInternal(
        JNIEnv*, jobject, jlong in_pointer, jint in_af_latency,
        jlong in_af_frame_count, jint in_af_sample_rate, jint in_latency, jint in_format) {
    if (android_get_device_api_level() < 34)
        return INT32_MIN;
    auto pointer = (uint8_t *) in_pointer;
    uint32_t mLatency = in_latency;
    uint32_t mAfLatency = in_af_latency;
    size_t mAfFrameCount = in_af_frame_count;
    uint32_t mAfSampleRate = in_af_sample_rate;
    uint32_t mFormat = in_format;
    uint8_t *pos = pointer;
    if (gTrackFlagsOffset == INT32_MIN) {
        return INT32_MIN;
    } else if (gTrackFlagsOffset == 0) {
        // arbitrary approximation
#define BUFFER_SIZE 800
        while (pos - pointer < BUFFER_SIZE) {
            pos += sizeof(uint32_t) / sizeof(uint8_t);
            if (pos - pointer < BUFFER_SIZE && *((uint32_t *) pos) == mLatency) {
                ALOGE("pos(%p) pointer(%p) pos-pointer(%d) BUFFER_SIZE(%d) reached mLatency(%d) "
                      "(mAfLatency(%d) mAfFrameCount(%d) mAfSampleRate(%d) mFormat(%d))",
                      pos, pointer, (int) (pos - pointer), BUFFER_SIZE, mLatency, mAfLatency,
                      (int) mAfFrameCount, mAfSampleRate, mFormat);
                return INT32_MAX;
            }
            if (pos - pointer < BUFFER_SIZE && *((uint32_t *) pos) == mAfLatency) {
                auto structptr = (audio_track_partial *) pos;
                ALOGD("pos(%p) pointer(%p) pos-pointer(%d) BUFFER_SIZE(%d) found mAfLatency(%d) "
                      "(mLatency(%d) mAfFrameCount(%d) mAfSampleRate(%d) mFormat(%d))",
                      pos, pointer, (int) (pos - pointer), BUFFER_SIZE, mAfLatency,
                      mLatency, (int) mAfFrameCount, mAfSampleRate, mFormat);
                pos = (uint8_t *) &structptr->mFormat + sizeof(uint32_t) / sizeof(uint8_t) - 1;
                if (pos - pointer >= BUFFER_SIZE || pos - pointer <= 0) break;
                pos = (uint8_t *) &structptr->mAfTrackFlags;
                if (structptr->mAfFrameCount != mAfFrameCount) {
                    ALOGE("pos(%p) pointer(%p) pos-pointer(%d) BUFFER_SIZE(%d) wrong mAfFrameCount"
                          "(%d) v(%d) (mAfLatency(%d) mLatency(%d) mAfSampleRate(%d) mFormat(%d))",
                          pos, pointer, (int) (pos - pointer), BUFFER_SIZE, (int) mAfFrameCount,
                          (int) structptr->mAfFrameCount, mAfLatency, mLatency, mAfSampleRate,
                          mFormat);
                    pos = (uint8_t *) &structptr->mAfLatency + sizeof(uint32_t) / sizeof(uint8_t);
                    continue;
                }
                if (structptr->mAfSampleRate != mAfSampleRate) {
                    ALOGE("pos(%p) pointer(%p) pos-pointer(%d) BUFFER_SIZE(%d) wrong mAfSampleRate"
                          "(%d) v(%d) (mAfLatency(%d) mLatency(%d) mAfFrameCount(%d) mFormat(%d))",
                          pos, pointer, (int) (pos - pointer), BUFFER_SIZE, mAfSampleRate,
                          structptr->mAfSampleRate, mAfLatency, mLatency, (int) mAfFrameCount,
                          mFormat);
                    pos = (uint8_t *) &structptr->mAfLatency + sizeof(uint32_t) / sizeof(uint8_t);
                    continue;
                }
                if (structptr->mFormat != mFormat) {
                    if (android_get_device_api_level() == 34 && *((int32_t *) (&structptr->mFormat))
                                                                /* mOriginalStreamType */ ==
                                                                -1 /* AUDIO_STREAM_DEFAULT */) {
                        ALOGI("pos(%p) pointer(%p) pos-pointer(%d) BUFFER_SIZE(%d) wrong mFormat"
                              "(%d) v(%d) (mAfLatency(%d) mLatency(%d) mAfFrameCount(%d) "
                              "mAfSampleRate(%d)), assume qpr0/qpr1",
                              pos, pointer, (int) (pos - pointer), BUFFER_SIZE, mFormat,
                              structptr->mFormat, mAfLatency, mLatency, (int) mAfFrameCount,
                              mAfSampleRate);
                        gTrackFlagsOffset = INT32_MIN;
                        return INT32_MIN;
                    }
                    ALOGE("pos(%p) pointer(%p) pos-pointer(%d) BUFFER_SIZE(%d) wrong mFormat(%d) "
                          "v(%d) (mAfLatency(%d) mLatency(%d) mAfFrameCount(%d) mAfSampleRate(%d))",
                          pos, pointer, (int) (pos - pointer), BUFFER_SIZE, mFormat,
                          structptr->mFormat, mAfLatency, mLatency, (int) mAfFrameCount,
                          mAfSampleRate);
                    pos = (uint8_t *) &structptr->mAfLatency + sizeof(uint32_t) / sizeof(uint8_t);
                    continue;
                }
                break;
            }
        }
        if (pos - pointer >= BUFFER_SIZE || pos - pointer <= 0) {
            ALOGE("pos(%p) pointer(%p) pos-pointer(%d) BUFFER_SIZE(%d) pcf (mLatency(%d) "
                  "mAfLatency(%d) mAfFrameCount(%d) mAfSampleRate(%d))",
                  pos, pointer, (int) (pos - pointer), BUFFER_SIZE, mLatency, mAfLatency,
                  (int) mAfFrameCount, mAfSampleRate);
            return INT32_MIN;
        }
        gTrackFlagsOffset = pos - pointer;
#undef BUFFER_SIZE
    } else {
        pos += gTrackFlagsOffset;
    }
    return (int32_t) (*((uint32_t * /*audio_output_flags_t*/) pos));
}

extern "C"
JNIEXPORT jint JNICALL
Java_org_nift4_gramophone_hificore_AudioTrackHiddenApi_getFlagsInternal(JNIEnv *env, jobject,
                                                                        jobject audio_track,
                                                                        jlong audio_track_ptr) {
    if (android_get_device_api_level() >= 26) {
        if (audio_track == nullptr) {
            ALOGE("flagsFromOffset: O+ but audio_track is null");
            return INT32_MIN;
        }
        if (!nativeGetFlags) {
            jclass at = env->GetObjectClass(audio_track);
            nativeGetFlags = env->GetMethodID(at, "native_get_flags", "()I");
            env->DeleteLocalRef(at);
            if (nativeGetFlags == nullptr) {
                ALOGE("getProxy: didn't find android/media/AudioTrack.native_get_flags()I");
                return INT32_MIN;
            }
        }
        return env->CallIntMethod(audio_track, nativeGetFlags);
    }
    size_t extra;
    switch (android_get_device_api_level()) {
#if 0
        case 27:
#ifdef __LP64__
            return (int32_t)*(uint32_t*)((uintptr_t)audio_track_ptr + 0x338); // aarch64, x86_64
#elif defined(i386)
            return (int32_t)*(uint32_t*)((uintptr_t)audio_track_ptr + 0x2cc);
#else
            return (int32_t)*(uint32_t*)((uintptr_t)audio_track_ptr + 0x2d8);
#endif
        case 26:
#ifdef __LP64__
            return (int32_t)*(uint32_t*)((uintptr_t)audio_track_ptr + 0x330); // aarch64, x86_64
#elif defined(i386)
            return (int32_t)*(uint32_t*)((uintptr_t)audio_track_ptr + 0x2c4);
#else
            return (int32_t)*(uint32_t*)((uintptr_t)audio_track_ptr + 0x2d0);
#endif
#endif
        case 25:
        case 24:
#ifdef i386
            return (int32_t)*(uint32_t*)((uintptr_t)audio_track_ptr + 0x23c);
#elif defined(__x86_64)
            return (int32_t)*(uint32_t*)((uintptr_t)audio_track_ptr + 0x2a0);
#else
        {
#ifdef __LP64__
            auto result = (int32_t)*(uint32_t*)((uintptr_t)audio_track_ptr + 0x2a0);
#else
            auto result = (int32_t)*(uint32_t*)((uintptr_t)audio_track_ptr + 0x248);
#endif
            if (libavenhancements_handle) {
                DLSYM_OR_ELSE(libavenhancements, ZN7android18ExtendedMediaUtils26AudioTrackIsTrackOffloadedEi) {
                    ALOGE("dlsym ZN7android18ExtendedMediaUtils26AudioTrackIsTrackOffloadedEi failed: %s", dlerror());
                }
            }
            if (ZN7android18ExtendedMediaUtils26AudioTrackIsTrackOffloadedEi) {
                uint32_t output = ZNK7android10AudioTrack9getOutputEv((void*) audio_track_ptr);
                bool isDirectPcm = ZN7android18ExtendedMediaUtils26AudioTrackIsTrackOffloadedEi((void*) 0xcafebabe, output);
                if ((result & 0x11) == 0x1 && !isDirectPcm) {
                    result &= ~0x1;
                } else if (!(result & 0x11) && isDirectPcm) {
                    result |= 0x1; // TODO: should this live here or add separate method for server flags?
                }
            }
            return result;
        }
#endif
        case 23:
#ifdef __LP64__
            return (int32_t)*(uint32_t*)((uintptr_t)audio_track_ptr + 0x280); // aarch64, x86_64
#elif defined(i386)
            return (int32_t)*(uint32_t*)((uintptr_t)audio_track_ptr + 0x218);
#else
            return (int32_t)*(uint32_t*)((uintptr_t)audio_track_ptr + 0x220);
#endif
        case 22:
            extra =
#ifdef __ARM_ARCH_7A__
                    /* QCOM_DIRECTTRACK (BOARD_USES_LEGACY_ALSA_AUDIO), only for MSM8x60 in CM12.x */
                    dlsym(libaudioclient_handle, "_ZN7android10AudioTrack6notifyEi") ? 0x20 /* 0x208 */ :
#endif
                    (dlsym(libaudioclient_handle, "_ZN7android10AudioTrack28initializeTrackOffloadParamsEv")
                     #if defined(__LP64__)
                     ? 0x20 /* 0x248 */ : 0x0);
                    // edge case: couldn't find any CM12.1 x86_64 build
                     #else
                     ? 0x18 /* armv7: 0x200, x86: 0x1f8 */ : 0x0);
#endif
            break;
        case 21:
            extra =
#ifdef __ARM_ARCH_7A__
                    /* QCOM_DIRECTTRACK (BOARD_USES_LEGACY_ALSA_AUDIO), only for MSM8x60 in CM12.x */
                dlsym(libaudioclient_handle, "_ZN7android10AudioTrack6notifyEi") ? 0x8 /* 0x1f0 */ :
#endif
                    (0);
            break;
        default:
            return INT32_MAX;
    }
#ifdef __LP64__
    return (int32_t)*(uint32_t*)((uintptr_t)audio_track_ptr + 0x228 + extra); // aarch64, x86_64
#elif defined(i386)
    return (int32_t)*(uint32_t*)((uintptr_t)audio_track_ptr + 0x1e0 + extra);
#else
    return (int32_t)*(uint32_t*)((uintptr_t)audio_track_ptr + 0x1e8 + extra);
#endif
}

struct audio_config_base {
	uint32_t sample_rate;
	uint32_t channel_mask;
	uint32_t format;
};

extern "C"
JNIEXPORT jint JNICALL
Java_org_nift4_gramophone_hificore_ReflectionAudioEffect_00024Companion_getEffectConfigs(JNIEnv *env, jobject,
                                                                        jlong ptr, jintArray out) {
	if (!initLib(env))
		return 1;
	audio_config_base c1 = {};
	audio_config_base c2 = {};
	DLSYM_OR_RETURN(libaudioclient, ZN7android11AudioEffect10getConfigsEP17audio_config_baseS2_, 1)
	int32_t ret = ZN7android11AudioEffect10getConfigsEP17audio_config_baseS2_((void*) ptr, &c1, &c2);
	env->SetIntArrayRegion(out, 0, sizeof(audio_config_base) / sizeof(int32_t), (int32_t*)&c1);
	env->SetIntArrayRegion(out, sizeof(audio_config_base) / sizeof(int32_t),
						   sizeof(audio_config_base) / sizeof(int32_t), (int32_t*)&c2);
	return ret;
}