// filerift_jni.cpp — JNI bridge between the launcher and the ported
// native FileRift engine + POD loader (app/src/main/cpp/lawncher/).
//
// Raijin's request: "pod viewer + filerift native support".
//   - decode(path|bytes, filetype)  -> readable FileRift markup text
//   - recode(text, filetype)        -> binary protobuf bytes
//   - extractLua(bytes)             -> embedded Lua source from .scl/.scene
//   - podSummary(bytes)             -> POD model metadata (viewer backend)
//
// Exposed to Java as net.kiwi.lawncher.filerift.Filerift.

#include <jni.h>
#include <string>
#include <vector>

#include "../tools/filerift.h"
#include "../tools/pod_loader.h"
#include "../platform/pvr_loader.h"
#include "../platform/pod_render.h"
#include "../platform/pod_gl_renderer.h"

// ─── helpers ────────────────────────────────────────────────────────────

static std::string jstring_to_std(JNIEnv* env, jstring jstr) {
    if (!jstr) return "";
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    if (!chars) return "";
    std::string out(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return out;
}

static std::vector<uint8_t> jbytearray_to_vec(JNIEnv* env, jbyteArray arr) {
    std::vector<uint8_t> out;
    if (!arr) return out;
    jsize len = env->GetArrayLength(arr);
    if (len <= 0) return out;
    out.resize(static_cast<size_t>(len));
    env->GetByteArrayRegion(arr, 0, len, reinterpret_cast<jbyte*>(out.data()));
    return out;
}

static jbyteArray vec_to_jbytearray(JNIEnv* env, const std::string& bytes) {
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(bytes.size()));
    if (arr) {
        env->SetByteArrayRegion(arr, 0, static_cast<jsize>(bytes.size()),
                                reinterpret_cast<const jbyte*>(bytes.data()));
    }
    return arr;
}

// ─── decode ─────────────────────────────────────────────────────────────

extern "C" JNIEXPORT jstring JNICALL
Java_net_kiwi_lawncher_filerift_Filerift_decode(JNIEnv* env, jclass,
                                              jbyteArray data, jstring filetype) {
    try {
        std::vector<uint8_t> bytes = jbytearray_to_vec(env, data);
        std::string type = jstring_to_std(env, filetype);
        if (bytes.empty() || type.empty()) return nullptr;

        std::string binary(reinterpret_cast<const char*>(bytes.data()), bytes.size());
        std::string text = filerift::decode_protobuf(binary, type);
        return env->NewStringUTF(text.c_str());
    } catch (const std::exception& e) {
        return env->NewStringUTF(("// decode error: " + std::string(e.what())).c_str());
    } catch (...) {
        return env->NewStringUTF("// decode error: unknown");
    }
}

// ─── recode ─────────────────────────────────────────────────────────────

extern "C" JNIEXPORT jbyteArray JNICALL
Java_net_kiwi_lawncher_filerift_Filerift_recode(JNIEnv* env, jclass,
                                              jstring text, jstring filetype) {
    try {
        std::string markup = jstring_to_std(env, text);
        std::string type = jstring_to_std(env, filetype);
        if (markup.empty() || type.empty()) return nullptr;

        std::string binary = filerift::recode_markup(markup, type);
        return vec_to_jbytearray(env, binary);
    } catch (const std::exception&) {
        // Return null so the Java side treats this as a failure and never
        // overwrites the original binary file with an error message.
        return nullptr;
    } catch (...) {
        return nullptr;
    }
}

// ─── extractLua ─────────────────────────────────────────────────────────

extern "C" JNIEXPORT jstring JNICALL
Java_net_kiwi_lawncher_filerift_Filerift_extractLua(JNIEnv* env, jclass,
                                                  jbyteArray data) {
    try {
        std::vector<uint8_t> bytes = jbytearray_to_vec(env, data);
        if (bytes.empty()) return nullptr;

        std::string binary(reinterpret_cast<const char*>(bytes.data()), bytes.size());
        std::string lua = filerift::extract_lua_generic(binary);
        return env->NewStringUTF(lua.c_str());
    } catch (const std::exception& e) {
        return env->NewStringUTF(("-- lua extract error: " + std::string(e.what())).c_str());
    } catch (...) {
        return env->NewStringUTF("-- lua extract error: unknown");
    }
}

// ─── decodeTexture ───────────────────────────────────────────────────────
// Decodes a .pvr / .tex texture buffer to RGBA8888 and returns it packed as
// [width, height, r,g,b,a, r,g,b,a, ...] in a jbyteArray (w/h as int16 in the
// first 4 bytes, then one byte per RGBA channel). Returns null on failure.
// Used by the PVR / texpng viewer.

extern "C" JNIEXPORT jbyteArray JNICALL
Java_net_kiwi_lawncher_filerift_Filerift_decodeTexture(JNIEnv* env, jclass,
                                                     jbyteArray data) {
    try {
        std::vector<uint8_t> bytes = jbytearray_to_vec(env, data);
        if (bytes.empty()) return nullptr;

        std::vector<uint8_t> rgba;
        int w = 0, h = 0;
        if (!pvr_decode_to_rgba(bytes.data(), bytes.size(), rgba, w, h)) return nullptr;
        if (w <= 0 || h <= 0 || rgba.size() != (size_t)w * h * 4) return nullptr;

        // cap extremely large textures to avoid OOM on old devices
        if ((size_t)w * h > 2048 * 2048) return nullptr;

        size_t total = 4 + rgba.size();
        jbyteArray out = env->NewByteArray((jsize)total);
        if (!out) return nullptr;

        std::vector<jbyte> packed;
        packed.reserve(total);
        packed.push_back((jbyte)(w & 0xFF));
        packed.push_back((jbyte)((w >> 8) & 0xFF));
        packed.push_back((jbyte)(h & 0xFF));
        packed.push_back((jbyte)((h >> 8) & 0xFF));
        for (uint8_t b : rgba) packed.push_back((jbyte)b);
        env->SetByteArrayRegion(out, 0, (jsize)total, packed.data());
        return out;
    } catch (...) {
        return nullptr;
    }
}

// ─── renderPod ───────────────────────────────────────────────────────────
// Renders a POD model with the software renderer into RGBA, returned packed
// as [width, height, r,g,b,a, ...] in a jbyteArray (w/h as int16 in the first
// 4 bytes, then one byte per RGBA channel). Returns null on failure.
// Used by the POD viewer.

extern "C" JNIEXPORT jbyteArray JNICALL
Java_net_kiwi_lawncher_filerift_Filerift_renderPod(JNIEnv* env, jclass,
                                                 jbyteArray data,
                                                 jint width, jint height,
                                                 jfloat rotY, jfloat rotX,
                                                 jfloat zoom, jboolean wireframe) {
    try {
        std::vector<uint8_t> bytes = jbytearray_to_vec(env, data);
        if (bytes.empty() || width <= 0 || height <= 0) return nullptr;

        std::vector<uint8_t> rgba;
        if (!podview::render_pod(bytes.data(), bytes.size(), width, height,
                                 rotY, rotX, zoom, wireframe == JNI_TRUE, rgba)) {
            return nullptr;
        }

        size_t total = 4 + rgba.size();
        jbyteArray out = env->NewByteArray((jsize)total);
        if (!out) return nullptr;

        std::vector<jbyte> packed;
        packed.reserve(total);
        packed.push_back((jbyte)(width & 0xFF));
        packed.push_back((jbyte)((width >> 8) & 0xFF));
        packed.push_back((jbyte)(height & 0xFF));
        packed.push_back((jbyte)((height >> 8) & 0xFF));
        for (uint8_t b : rgba) packed.push_back((jbyte)b);
        env->SetByteArrayRegion(out, 0, (jsize)total, packed.data());
        return out;
    } catch (...) {
        return nullptr;
    }
}

// ─── podInfo ─────────────────────────────────────────────────────────────
// One-line human-readable summary of a POD model for viewer titles.

extern "C" JNIEXPORT jstring JNICALL
Java_net_kiwi_lawncher_filerift_Filerift_podInfo(JNIEnv* env, jclass,
                                               jbyteArray data) {
    try {
        std::vector<uint8_t> bytes = jbytearray_to_vec(env, data);
        if (bytes.empty()) return nullptr;
        return env->NewStringUTF(podview::pod_info_line(bytes.data(), bytes.size()).c_str());
    } catch (...) {
        return nullptr;
    }
}

// ─── OpenGL POD viewer (GLSurfaceView backend) ───────────────────────────
// These must be called from the GL thread (with a current EGL context).
// podGLInit  -> compile shaders
// podGLLoad  -> parse POD + upload meshes/textures, returns handle
// podGLRender-> draw one frame into the current framebuffer
// podGLFree  -> release GPU resources
// podGLInfo / podGLFrameCount -> metadata

extern "C" JNIEXPORT jboolean JNICALL
Java_net_kiwi_lawncher_filerift_Filerift_podGLInit(JNIEnv*, jclass) {
    return podgl::init() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_net_kiwi_lawncher_filerift_Filerift_podGLLoad(JNIEnv* env, jclass,
                                                 jbyteArray data, jstring baseDir) {
    try {
        std::vector<uint8_t> bytes = jbytearray_to_vec(env, data);
        if (bytes.empty()) return -1;
        std::string dir = jstring_to_std(env, baseDir);
        return (jint)podgl::load(bytes.data(), bytes.size(), dir);
    } catch (...) {
        return -1;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_net_kiwi_lawncher_filerift_Filerift_podGLRender(JNIEnv*, jclass,
                                                    jint handle, jint w, jint h,
                                                    jfloat yaw, jfloat pitch, jfloat dist,
                                                    jfloat frame, jboolean wireframe,
                                                    jboolean showGrid, jboolean autoRotate) {
    podgl::render(handle, w, h, yaw, pitch, dist, frame,
                  wireframe == JNI_TRUE, showGrid == JNI_TRUE, autoRotate == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_net_kiwi_lawncher_filerift_Filerift_podGLFree(JNIEnv*, jclass, jint handle) {
    podgl::free_model(handle);
}

extern "C" JNIEXPORT jstring JNICALL
Java_net_kiwi_lawncher_filerift_Filerift_podGLInfo(JNIEnv* env, jclass, jint handle) {
    return env->NewStringUTF(podgl::info(handle).c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_net_kiwi_lawncher_filerift_Filerift_podGLFrameCount(JNIEnv*, jclass, jint handle) {
    return podgl::frame_count(handle);
}

// ─── podSummary ─────────────────────────────────────────────────────────

extern "C" JNIEXPORT jstring JNICALL
Java_net_kiwi_lawncher_filerift_Filerift_podSummary(JNIEnv* env, jclass,
                                                  jbyteArray data) {
    try {
        std::vector<uint8_t> bytes = jbytearray_to_vec(env, data);
        if (bytes.empty()) return env->NewStringUTF("// empty POD data");

        av::PODModel model = av::pod_parse(bytes.data(), bytes.size());

        std::string out = "// POD model\n";
        out += "// version: " + model.version + "\n";
        out += "// meshes: " + std::to_string(model.meshes.size()) + "\n";
        out += "// nodes: " + std::to_string(model.nodes.size()) + "\n";
        out += "// materials: " + std::to_string(model.materials.size()) + "\n";
        out += "// frames: " + std::to_string(model.num_frames) + "\n";
        out += "// fps: " + std::to_string((int)model.fps) + "\n";
        out += "// total vertices: " + std::to_string(model.total_vertices) + "\n";
        out += "// total faces: " + std::to_string(model.total_faces) + "\n";
        out += "// bounding radius: " + std::to_string(model.radius) + "\n\n";

        for (size_t i = 0; i < model.meshes.size(); ++i) {
            const av::PODMesh& m = model.meshes[i];
            out += "Mesh " + std::to_string(i) + ":\n";
            out += "  vertices: " + std::to_string(m.num_vertices) + "\n";
            out += "  faces: " + std::to_string(m.num_faces) + "\n";
            out += "  indices: " + std::to_string(m.indices.size()) + "\n";
            out += "  bones_per_vertex: " + std::to_string(m.bones_per_vertex) + "\n";
            char aabb[128];
            snprintf(aabb, sizeof(aabb),
                     "  aabb: [%.2f, %.2f, %.2f] -> [%.2f, %.2f, %.2f]\n",
                     m.min_x, m.min_y, m.min_z, m.max_x, m.max_y, m.max_z);
            out += aabb;
            out += "\n";
        }

        for (size_t i = 0; i < model.materials.size(); ++i) {
            const av::PODMaterial& mat = model.materials[i];
            out += "Material " + std::to_string(i) + ": " + mat.name + "\n";
            if (mat.diffuse_texture_index >= 0 &&
                mat.diffuse_texture_index < (int)model.texture_filenames.size()) {
                out += "  texture: " + model.texture_filenames[mat.diffuse_texture_index] + "\n";
            }
        }

        return env->NewStringUTF(out.c_str());
    } catch (const std::exception& e) {
        return env->NewStringUTF(("// pod error: " + std::string(e.what())).c_str());
    } catch (...) {
        return env->NewStringUTF("// pod error: unknown");
    }
}
