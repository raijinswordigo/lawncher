/* pod_gl_renderer.cpp — OpenGL ES 3.0 POD model renderer for the Android launcher.
 *
 * Port of Ruby's POD viewer pipeline (SwordigoDesktop src/tools/av_renderer.cpp
 * + asset_viewer.cpp): GLSL shaders, orbit camera, node-matrix transforms,
 * CPU skinning, diffuse texture mapping, XZ grid.
 *
 * Uses the launcher's ported pod_loader (av::) for parsing + node matrices +
 * skinning, and pvr_loader for decoding diffuse textures.
 */

#include "pod_gl_renderer.h"

#include "tools/pod_loader.h"
#include "platform/pvr_loader.h"

#include <EGL/egl.h>
#include <GLES3/gl3.h>

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

namespace podgl {

// ─── Constants ──────────────────────────────────────────────────────────
    static constexpr float PI = 3.14159265358979323846f;
    static constexpr float DEG2RAD = PI / 180.0f;

    static constexpr int MAX_MODELS = 8;

// ─── GLSL 300 ES model shaders (ported from av_renderer.cpp MODEL_VS/FS) ──
    static const char* MODEL_VS = R"GLSL(
#version 300 es
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aNorm;
layout(location=2) in vec2 aUV;

uniform mat4 uMVP;
uniform mat4 uModel;
uniform mat3 uNormalMat;

out vec3 vNormal;
out vec3 vWorldPos;
out vec2 vUV;

void main() {
    vNormal   = normalize(uNormalMat * aNorm);
    vWorldPos = vec3(uModel * vec4(aPos, 1.0));
    vUV       = aUV;
    gl_Position = uMVP * vec4(aPos, 1.0);
}
)GLSL";

    static const char* MODEL_FS = R"GLSL(
#version 300 es
precision highp float;
precision highp int;

in vec3 vNormal;
in vec3 vWorldPos;
in vec2 vUV;

uniform sampler2D uTexture;
uniform int   uHasTexture;
uniform vec3  uLightDir;
uniform vec3  uLightColor;
uniform vec3  uAmbient;
uniform vec3  uAmbientGround;
uniform vec4  uMatColor;
uniform float uAlpha;
uniform int   uFlatShade;
uniform vec3  uFillColor;
uniform float uRimStrength;
uniform float uSpecStrength;
uniform vec3  uCamPos;

out vec4 FragColor;

void main() {
    vec4 base = (uHasTexture == 1) ? texture(uTexture, vUV) : uMatColor;
    vec3 N    = normalize(vNormal);

    if (uFlatShade == 1) {
        FragColor = vec4(base.rgb, base.a * uAlpha);
        return;
    }

    // Hemisphere ambient (sky/ground fill) with a hard floor.
    float hemi = clamp(N.y * 0.5 + 0.5, 0.0, 1.0);
    vec3  amb  = max(mix(uAmbientGround, uAmbient, hemi), vec3(0.07));

    vec3 V = normalize(uCamPos - vWorldPos + vec3(1e-4));

    // Key directional light (soft half-Lambert).
    vec3 keyDir = normalize(uLightDir);
    float ndl   = dot(N, keyDir);
    float wrap  = clamp((ndl + 0.22) / 1.22, 0.0, 1.0);
    vec3  key   = uLightColor * (wrap * wrap * 1.28);

    // Cool fill from the opposite side.
    vec3 fillDir = normalize(-keyDir * 0.55 + vec3(0.0, 0.5, 0.0));
    vec3 fill    = uFillColor * clamp(dot(N, fillDir), 0.0, 1.0) * 0.6;

    // Rim light (camera-opposed silhouette pop).
    float rim  = pow(1.0 - clamp(dot(N, V), 0.0, 1.0), 3.0) * uRimStrength;
    vec3  rimL = uLightColor * rim;

    // Subtle Blinn-Phong sheen.
    vec3  H    = normalize(keyDir + V);
    float spec = pow(max(dot(N, H), 0.0), 24.0) * uSpecStrength;

    vec3 lit = base.rgb * (amb + key + fill + rimL) + uLightColor * spec;

    FragColor = vec4(lit, base.a * uAlpha);
}
)GLSL";

// ─── Grid shader (XZ plane with fade) ───────────────────────────────────
    static const char* GRID_VS = R"GLSL(
#version 300 es
layout(location=0) in vec3 aPos;
uniform mat4 uMVP;
uniform mat4 uModel;
out vec3 vWorldPos;
void main() {
    vWorldPos   = vec3(uModel * vec4(aPos, 1.0));
    gl_Position = uMVP * vec4(aPos, 1.0);
}
)GLSL";

    static const char* GRID_FS = R"GLSL(
#version 300 es
precision highp float;
in vec3 vWorldPos;
uniform float uGridSize;
uniform vec4  uGridColor;
uniform float uScale;
out vec4 FragColor;

float distToLine(float pos, float step) {
    return abs(abs(pos) - round(pos / step) * step);
}

void main() {
    vec2 g = vWorldPos.xz;
    float dist = length(g);
    float fade = 1.0 - smoothstep(uGridSize * 0.55, uGridSize, dist);

    float s = max(uScale, 1e-4);
    float target = s * 80.0;
    float decade = pow(10.0, floor(log(target) / log(10.0)));
    float norm = target / decade;
    float major = (norm < 2.0 ? 2.0 : norm < 5.0 ? 5.0 : 10.0) * decade;
    float minor = major / 5.0;

    float pxMinor = min(distToLine(g.x, minor), distToLine(g.y, minor)) / s;
    float pxMajor = min(distToLine(g.x, major), distToLine(g.y, major)) / s;
    float minorA  = 1.0 - smoothstep(0.55, 1.35, pxMinor);
    float majorA  = 1.0 - smoothstep(0.55, 1.75, pxMajor);
    float line    = max(majorA, minorA * 0.45);

    float axX = 1.0 - smoothstep(0.6, 2.0, abs(g.x) / s);
    float axZ = 1.0 - smoothstep(0.6, 2.0, abs(g.y) / s);
    float axis = max(axX, axZ);

    vec3 col = uGridColor.rgb;
    vec3 axisCol = min(vec3(1.0), uGridColor.rgb * 1.35 + vec3(0.06));
    col = mix(col, axisCol, axis * 0.9);

    float alpha = uGridColor.a * max(line, axis) * fade;
    FragColor = vec4(col, alpha);
}
)GLSL";

// ─── Math (column-major, right-handed) ─────────────────────────────────
    static void mat4_identity(float m[16]) {
        std::memset(m, 0, 16 * sizeof(float));
        m[0] = m[5] = m[10] = m[15] = 1.0f;
    }

    static void mat4_multiply(float out[16], const float a[16], const float b[16]) {
        float tmp[16];
        for (int c = 0; c < 4; c++)
            for (int r = 0; r < 4; r++)
                tmp[c * 4 + r] = a[0 * 4 + r] * b[c * 4 + 0] +
                                 a[1 * 4 + r] * b[c * 4 + 1] +
                                 a[2 * 4 + r] * b[c * 4 + 2] +
                                 a[3 * 4 + r] * b[c * 4 + 3];
        std::memcpy(out, tmp, 16 * sizeof(float));
    }

    static void mat4_translate(float out[16], float tx, float ty, float tz) {
        mat4_identity(out);
        out[12] = tx; out[13] = ty; out[14] = tz;
    }

    static void mat4_perspective(float out[16], float fov_deg, float aspect,
                                 float near_p, float far_p) {
        std::memset(out, 0, 16 * sizeof(float));
        float f = 1.0f / std::tan(fov_deg * DEG2RAD * 0.5f);
        out[0]  = f / aspect;
        out[5]  = f;
        out[10] = (far_p + near_p) / (near_p - far_p);
        out[11] = -1.0f;
        out[14] = (2.0f * far_p * near_p) / (near_p - far_p);
    }

    static void mat4_look_at(float out[16], float ex, float ey, float ez,
                             float cx, float cy, float cz,
                             float ux, float uy, float uz) {
        float fx = cx - ex, fy = cy - ey, fz = cz - ez;
        float flen = std::sqrt(fx * fx + fy * fy + fz * fz);
        if (flen > 1e-8f) { fx /= flen; fy /= flen; fz /= flen; }
        float sx = fy * uz - fz * uy;
        float sy = fz * ux - fx * uz;
        float sz = fx * uy - fy * ux;
        float slen = std::sqrt(sx * sx + sy * sy + sz * sz);
        if (slen > 1e-8f) { sx /= slen; sy /= slen; sz /= slen; }
        float rx = sy * fz - sz * fy;
        float ry = sz * fx - sx * fz;
        float rz = sx * fy - sy * fx;
        mat4_identity(out);
        out[0] = sx; out[4] = sy; out[8]  = sz;
        out[1] = rx; out[5] = ry; out[9]  = rz;
        out[2] = -fx; out[6] = -fy; out[10] = -fz;
        out[12] = -(sx * ex + sy * ey + sz * ez);
        out[13] = -(rx * ex + ry * ey + rz * ez);
        out[14] =  (fx * ex + fy * ey + fz * ez);
    }

    static void mat4_normal_matrix(float out[9], const float m[16]) {
        float a00 = m[0], a01 = m[4], a02 = m[8];
        float a10 = m[1], a11 = m[5], a12 = m[9];
        float a20 = m[2], a21 = m[6], a22 = m[10];
        float c00 =  (a11 * a22 - a12 * a21);
        float c01 = -(a10 * a22 - a12 * a20);
        float c02 =  (a10 * a21 - a11 * a20);
        float c10 = -(a01 * a22 - a02 * a21);
        float c11 =  (a00 * a22 - a02 * a20);
        float c12 = -(a00 * a21 - a01 * a20);
        float c20 =  (a01 * a12 - a02 * a11);
        float c21 = -(a00 * a12 - a02 * a10);
        float c22 =  (a00 * a11 - a01 * a10);
        float det = a00 * c00 + a01 * c01 + a02 * c02;
        if (std::fabs(det) < 1e-12f) {
            std::memset(out, 0, 9 * sizeof(float));
            out[0] = out[4] = out[8] = 1.0f;
            return;
        }
        float inv = 1.0f / det;
        out[0] = c00 * inv; out[3] = c01 * inv; out[6] = c02 * inv;
        out[1] = c10 * inv; out[4] = c11 * inv; out[7] = c12 * inv;
        out[2] = c20 * inv; out[5] = c21 * inv; out[8] = c22 * inv;
    }

// ─── GL program helpers ────────────────────────────────────────────────
    static GLuint compile_shader(GLenum type, const char* src) {
        GLuint id = glCreateShader(type);
        glShaderSource(id, 1, &src, nullptr);
        glCompileShader(id);
        GLint ok = 0;
        glGetShaderiv(id, GL_COMPILE_STATUS, &ok);
        if (!ok) {
            char log[1024];
            glGetShaderInfoLog(id, sizeof(log), nullptr, log);
            fprintf(stderr, "[podgl] shader compile error: %s\n", log);
            glDeleteShader(id);
            return 0;
        }
        return id;
    }

    static GLuint link_program(GLuint vs, GLuint fs) {
        GLuint prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);
        GLint ok = 0;
        glGetProgramiv(prog, GL_LINK_STATUS, &ok);
        if (!ok) {
            char log[1024];
            glGetProgramInfoLog(prog, sizeof(log), nullptr, log);
            fprintf(stderr, "[podgl] program link error: %s\n", log);
            glDeleteProgram(prog);
            return 0;
        }
        return prog;
    }

// ─── State ─────────────────────────────────────────────────────────────
    static GLuint s_model_prog = 0;
    static GLuint s_grid_prog = 0;
    static GLint  s_mvp = -1, s_model = -1, s_nmat = -1, s_tex = -1, s_has_tex = -1,
        s_light_dir = -1, s_light_col = -1, s_ambient = -1, s_amb_ground = -1,
        s_mat_color = -1, s_alpha = -1, s_flat = -1, s_fill = -1,
        s_rim = -1, s_spec = -1, s_cam = -1;
    static GLint  s_g_mvp = -1, s_g_model = -1, s_g_size = -1, s_g_color = -1, s_g_scale = -1;

    static GLuint s_grid_vao = 0, s_grid_vbo = 0;

// ─── Per-model GPU state ───────────────────────────────────────────────
    struct GpuMesh {
        GLuint vao = 0, vbo = 0, ebo = 0;
        int index_count = 0;
        // Wireframe: a GL_LINES edge buffer (non-indexed draw of triangle edges).
        GLuint wire_vao = 0, wire_vbo = 0;
        int wire_vertex_count = 0;
        GLuint texture_id = 0;
        // CPU-side skin buffers (positions/normals after skin_mesh).
        std::vector<float> skin_pos, skin_nrm;
        int num_vertices = 0;
    };

    struct GpuModel {
        bool loaded = false;
        av::PODModel pod;
        std::vector<GpuMesh> meshes;      // indexed by mesh index
        std::vector<GLuint> textures;     // indexed by texture_filenames index
        std::string info_line;
        int node_count = 0;
    };

    static GpuModel s_models[MAX_MODELS];

// ─── Texture loading (decode PVR/tex/png → GL texture) ─────────────────
    static GLuint load_texture_file(const std::string& path) {
        FILE* f = fopen(path.c_str(), "rb");
        if (!f) return 0;
        fseek(f, 0, SEEK_END);
        long sz = ftell(f);
        fseek(f, 0, SEEK_SET);
        if (sz <= 0) { fclose(f); return 0; }
        std::vector<uint8_t> buf(sz);
        if (fread(buf.data(), 1, sz, f) != (size_t)sz) { fclose(f); return 0; }
        fclose(f);

        // Try PVR/tex decoder first (handles .pvr/.tex/.tex.png gz).
        std::vector<uint8_t> rgba;
        int w = 0, h = 0;
        if (pvr_decode_to_rgba(buf.data(), buf.size(), rgba, w, h) && w > 0 && h > 0) {
            GLuint tex = 0;
            glGenTextures(1, &tex);
            glBindTexture(GL_TEXTURE_2D, tex);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba.data());
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            return tex;
        }
        return 0;
    }

    static GLuint resolve_texture(const std::string& base_dir, const std::string& tex_name) {
        if (base_dir.empty() || tex_name.empty()) return 0;
        // candidate stems
        std::string stem = tex_name;
        // strip common extensions
        for (const char* ext : {".png", ".pvr", ".tex", ".gif", ".jpg", ".jpeg", ".bmp", ".webp"}) {
            std::string e(ext);
            if (stem.size() > e.size() && stem.compare(stem.size() - e.size(), e.size(), e) == 0) {
                stem = stem.substr(0, stem.size() - e.size());
                break;
            }
        }
        std::vector<std::string> candidates = {
            base_dir + "/" + tex_name,
            base_dir + "/" + stem + "_2x.tex.png",
            base_dir + "/" + stem + ".tex.png",
            base_dir + "/" + stem + "_2x.pvr",
            base_dir + "/" + stem + ".pvr",
            base_dir + "/" + stem + "_2x.tex",
            base_dir + "/" + stem + ".tex",
            base_dir + "/" + stem + "_2x.png",
            base_dir + "/" + stem + ".png",
        };
        for (const auto& c : candidates) {
            GLuint t = load_texture_file(c);
            if (t) return t;
        }
        return 0;
    }

// ─── Model load ────────────────────────────────────────────────────────
    static void upload_mesh(GpuMesh& gm, const av::PODMesh& m) {
        if (m.positions.empty()) return;

        int num_verts = (int)(m.positions.size() / 3);
        gm.num_vertices = num_verts;

        // Interleave pos3/nrm3/uv2 (8 floats). Missing streams default.
        std::vector<float> interleaved;
        interleaved.reserve((size_t)num_verts * 8);
        for (int i = 0; i < num_verts; ++i) {
            interleaved.push_back(m.positions[i * 3 + 0]);
            interleaved.push_back(m.positions[i * 3 + 1]);
            interleaved.push_back(m.positions[i * 3 + 2]);
            if (i * 3 + 2 < (int)m.normals.size()) {
                interleaved.push_back(m.normals[i * 3 + 0]);
                interleaved.push_back(m.normals[i * 3 + 1]);
                interleaved.push_back(m.normals[i * 3 + 2]);
            } else {
                interleaved.push_back(0); interleaved.push_back(1); interleaved.push_back(0);
            }
            if (i * 2 + 1 < (int)m.uvs.size()) {
                interleaved.push_back(m.uvs[i * 2 + 0]);
                interleaved.push_back(m.uvs[i * 2 + 1]);
            } else {
                interleaved.push_back(0); interleaved.push_back(0);
            }
        }

        glGenVertexArrays(1, &gm.vao);
        glGenBuffers(1, &gm.vbo);
        glGenBuffers(1, &gm.ebo);
        glBindVertexArray(gm.vao);
        glBindBuffer(GL_ARRAY_BUFFER, gm.vbo);
        glBufferData(GL_ARRAY_BUFFER, interleaved.size() * sizeof(float), interleaved.data(), GL_STATIC_DRAW);

        // indices (widen u16 -> u32 already done by pod_loader)
        gm.index_count = (int)m.indices.size();
        if (gm.index_count > 0) {
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, gm.ebo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, m.indices.size() * sizeof(uint32_t), m.indices.data(), GL_STATIC_DRAW);
        } else {
            // non-indexed: draw arrays
            gm.index_count = num_verts;
        }

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void*)0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void*)(3 * sizeof(float)));
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 2, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void*)(6 * sizeof(float)));
        glBindVertexArray(0);

        // Build a GL_LINES edge buffer for wireframe mode (GLES has no
        // glPolygonMode, so wireframe is drawn as separate line segments).
        if (gm.index_count > 0 && !m.indices.empty()) {
            std::vector<float> edges;
            auto push_edge = [&](uint32_t a, uint32_t b) {
                if (a >= (uint32_t)num_verts || b >= (uint32_t)num_verts) return;
                for (int k = 0; k < 3; ++k) {
                    edges.push_back(m.positions[a * 3 + k]);
                    edges.push_back(m.positions[b * 3 + k]);
                }
            };
            size_t n = m.indices.size();
            if (n % 3 == 0) {
                for (size_t i = 0; i + 2 < n; i += 3) {
                    push_edge(m.indices[i], m.indices[i + 1]);
                    push_edge(m.indices[i + 1], m.indices[i + 2]);
                    push_edge(m.indices[i + 2], m.indices[i]);
                }
            } else if (n % 4 == 0) {
                for (size_t i = 0; i + 3 < n; i += 4) {
                    push_edge(m.indices[i], m.indices[i + 1]);
                    push_edge(m.indices[i + 1], m.indices[i + 2]);
                    push_edge(m.indices[i + 2], m.indices[i + 3]);
                    push_edge(m.indices[i + 3], m.indices[i]);
                }
            }
            if (!edges.empty()) {
                glGenVertexArrays(1, &gm.wire_vao);
                glGenBuffers(1, &gm.wire_vbo);
                glBindVertexArray(gm.wire_vao);
                glBindBuffer(GL_ARRAY_BUFFER, gm.wire_vbo);
                glBufferData(GL_ARRAY_BUFFER, edges.size() * sizeof(float), edges.data(), GL_STATIC_DRAW);
                glEnableVertexAttribArray(0);
                glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 3 * sizeof(float), (void*)0);
                glBindVertexArray(0);
                gm.wire_vertex_count = (int)(edges.size() / 3);
            }
        }
    }

    static void update_mesh_vertices(GpuMesh& gm, const float* pos, const float* nrm, int num_verts) {
        if (!gm.vbo || num_verts <= 0) return;
        std::vector<float> interleaved;
        interleaved.reserve((size_t)num_verts * 8);
        // We don't retain UVs here; rebuild from skinned pos/nrm with UVs zeroed.
        // (For a viewer this is acceptable; full UV preservation would need the
        //  source mesh UVs cached on GpuMesh.)
        for (int i = 0; i < num_verts; ++i) {
            interleaved.push_back(pos[i * 3 + 0]);
            interleaved.push_back(pos[i * 3 + 1]);
            interleaved.push_back(pos[i * 3 + 2]);
            if (nrm && i * 3 + 2 < num_verts * 3) {
                interleaved.push_back(nrm[i * 3 + 0]);
                interleaved.push_back(nrm[i * 3 + 1]);
                interleaved.push_back(nrm[i * 3 + 2]);
            } else {
                interleaved.push_back(0); interleaved.push_back(1); interleaved.push_back(0);
            }
            interleaved.push_back(0); interleaved.push_back(0);
        }
        glBindBuffer(GL_ARRAY_BUFFER, gm.vbo);
        glBufferData(GL_ARRAY_BUFFER, interleaved.size() * sizeof(float), interleaved.data(), GL_DYNAMIC_DRAW);
    }

    ModelHandle load(const uint8_t* data, size_t size, const std::string& base_dir) {
        // find a free slot
        int slot = -1;
        for (int i = 0; i < MAX_MODELS; ++i) {
            if (!s_models[i].loaded) { slot = i; break; }
        }
        if (slot < 0) return -1;

        GpuModel& gm = s_models[slot];
        gm = GpuModel();
        gm.pod = av::pod_parse(data, size);
        if (gm.pod.meshes.empty()) return -1;

        gm.loaded = true;
        gm.node_count = gm.pod.num_mesh_nodes > 0 ? gm.pod.num_mesh_nodes : (int)gm.pod.nodes.size();

        // Load textures.
        gm.textures.resize(gm.pod.texture_filenames.size(), 0);
        for (size_t i = 0; i < gm.pod.texture_filenames.size(); ++i) {
            gm.textures[i] = resolve_texture(base_dir, gm.pod.texture_filenames[i]);
        }

        // Upload meshes.
        gm.meshes.resize(gm.pod.meshes.size());
        for (size_t i = 0; i < gm.pod.meshes.size(); ++i) {
            upload_mesh(gm.meshes[i], gm.pod.meshes[i]);
        }

        // Info line.
        char buf[128];
        snprintf(buf, sizeof(buf), "meshes %zu  verts %d  tris %d  anim %df",
                 gm.pod.meshes.size(), gm.pod.total_vertices, gm.pod.total_faces,
                 gm.pod.num_frames);
        gm.info_line = buf;

        return slot;
    }

    void free_model(ModelHandle h) {
        if (h < 0 || h >= MAX_MODELS) return;
        GpuModel& gm = s_models[h];
        if (!gm.loaded) return;
        for (auto& m : gm.meshes) {
            if (m.vao) glDeleteVertexArrays(1, &m.vao);
            if (m.vbo) glDeleteBuffers(1, &m.vbo);
            if (m.ebo) glDeleteBuffers(1, &m.ebo);
            if (m.wire_vao) glDeleteVertexArrays(1, &m.wire_vao);
            if (m.wire_vbo) glDeleteBuffers(1, &m.wire_vbo);
        }
        for (GLuint t : gm.textures) if (t) glDeleteTextures(1, &t);
        gm = GpuModel();
    }

// ─── Grid ──────────────────────────────────────────────────────────────
    static void ensure_grid() {
        if (s_grid_vao) return;
        // A large XZ quad; the shader fades it at the edges.
        float verts[] = { -1000, 0, -1000,  1000, 0, -1000,  1000, 0, 1000,  -1000, 0, 1000 };
        glGenVertexArrays(1, &s_grid_vao);
        glGenBuffers(1, &s_grid_vbo);
        glBindVertexArray(s_grid_vao);
        glBindBuffer(GL_ARRAY_BUFFER, s_grid_vbo);
        glBufferData(GL_ARRAY_BUFFER, sizeof(verts), verts, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 3 * sizeof(float), (void*)0);
        glBindVertexArray(0);
    }

// ─── Init ──────────────────────────────────────────────────────────────
// EGL context this cache was built against. GLSurfaceView hands out a
// brand-new context per view instance (no sharing across separate POD
// viewer dialogs), and GL object names — shader programs, VAOs, VBOs,
// textures — are only meaningful within the context that created them.
// Caching s_model_prog/s_grid_prog/s_models[] as plain statics meant that
// closing one viewer and opening another kept the OLD (now-dead) program
// ID around; init() saw it was non-zero and skipped recompiling in the
// new context, so the new model uploaded fine but drew with no valid
// shader bound — silently nothing. Comparing against the live context
// and resetting on change fixes both that and the second-order bug where
// closing a viewer never actually freed its GPU resources (the dismiss
// listener's queueEvent() often loses the race against the GL thread
// exiting when the dialog is dismissed, so podGLFree() never ran) — those
// stale slots are reclaimed here too since their handles are equally dead.
    static EGLContext s_egl_context = EGL_NO_CONTEXT;

    bool init() {
        EGLContext current = eglGetCurrentContext();
        if (current != s_egl_context) {
            s_egl_context = current;
            s_model_prog = 0;
            s_grid_prog = 0;
            s_grid_vao = 0;
            s_grid_vbo = 0;
            for (int i = 0; i < MAX_MODELS; ++i) s_models[i] = GpuModel();
        }

        if (s_model_prog) return true;

        GLuint vs = compile_shader(GL_VERTEX_SHADER, MODEL_VS);
        GLuint fs = compile_shader(GL_FRAGMENT_SHADER, MODEL_FS);
        s_model_prog = link_program(vs, fs);
        glDeleteShader(vs);
        glDeleteShader(fs);
        if (!s_model_prog) return false;

        s_mvp = glGetUniformLocation(s_model_prog, "uMVP");
        s_model = glGetUniformLocation(s_model_prog, "uModel");
        s_nmat = glGetUniformLocation(s_model_prog, "uNormalMat");
        s_tex = glGetUniformLocation(s_model_prog, "uTexture");
        s_has_tex = glGetUniformLocation(s_model_prog, "uHasTexture");
        s_light_dir = glGetUniformLocation(s_model_prog, "uLightDir");
        s_light_col = glGetUniformLocation(s_model_prog, "uLightColor");
        s_ambient = glGetUniformLocation(s_model_prog, "uAmbient");
        s_amb_ground = glGetUniformLocation(s_model_prog, "uAmbientGround");
        s_mat_color = glGetUniformLocation(s_model_prog, "uMatColor");
        s_alpha = glGetUniformLocation(s_model_prog, "uAlpha");
        s_flat = glGetUniformLocation(s_model_prog, "uFlatShade");
        s_fill = glGetUniformLocation(s_model_prog, "uFillColor");
        s_rim = glGetUniformLocation(s_model_prog, "uRimStrength");
        s_spec = glGetUniformLocation(s_model_prog, "uSpecStrength");
        s_cam = glGetUniformLocation(s_model_prog, "uCamPos");

        GLuint gvs = compile_shader(GL_VERTEX_SHADER, GRID_VS);
        GLuint gfs = compile_shader(GL_FRAGMENT_SHADER, GRID_FS);
        s_grid_prog = link_program(gvs, gfs);
        glDeleteShader(gvs);
        glDeleteShader(gfs);
        if (s_grid_prog) {
            s_g_mvp = glGetUniformLocation(s_grid_prog, "uMVP");
            s_g_model = glGetUniformLocation(s_grid_prog, "uModel");
            s_g_size = glGetUniformLocation(s_grid_prog, "uGridSize");
            s_g_color = glGetUniformLocation(s_grid_prog, "uGridColor");
            s_g_scale = glGetUniformLocation(s_grid_prog, "uScale");
        }

        ensure_grid();
        return true;
    }

// ─── Render ────────────────────────────────────────────────────────────
    void render(ModelHandle h, int w, int hpx,
                float yaw, float pitch, float dist,
                float frame, bool wireframe, bool show_grid, bool auto_rotate) {
        if (!s_model_prog || w <= 0 || hpx <= 0) return;

        // Previously this function bailed out here whenever the handle was
        // invalid or the model hadn't finished loading — which meant a failed
        // load (e.g. a corrupt/unsupported POD) produced a plain black surface
        // with no grid and no feedback at all. Now we still clear and draw the
        // grid so there's always something on screen; only the per-mesh draw
        // loop below is skipped when there's no model.
        bool have_model = (h >= 0 && h < MAX_MODELS && s_models[h].loaded);
        GpuModel* gm_ptr = have_model ? &s_models[h] : nullptr;

        // Lighting (Swordigo-faithful defaults from av_renderer.cpp).
        float light_dir[3] = { 0.35f, 0.85f, 0.39f };
        float light_col[3] = { 1.05f, 0.92f, 0.72f };
        float fill_col[3]  = { 0.30f, 0.38f, 0.55f };
        float ambient[3]   = { 0.19f, 0.20f, 0.25f };
        float amb_ground[3]= { 0.09f, 0.095f, 0.14f };
        float rim = 0.32f, spec = 0.06f;

        // Orbit camera (Ruby spherical convention).
        float yaw_rad = yaw * DEG2RAD, pitch_rad = pitch * DEG2RAD;
        float cos_p = std::cos(pitch_rad);
        float target[3] = { 0.0f, 0.0f, 0.0f };
        float radius = 1.0f;
        if (have_model) {
            target[0] = gm_ptr->pod.center_x;
            target[1] = gm_ptr->pod.center_y;
            target[2] = gm_ptr->pod.center_z;
            radius = std::max(gm_ptr->pod.radius, 0.01f);
        }
        // `dist` is a zoom multiplier relative to auto-fit (1.0 = default fit),
        // not an absolute world-space distance — keeps pinch-zoom consistent
        // across models of wildly different scale.
        float zoom = dist > 0.0f ? dist : 1.0f;
        float cam_dist = radius * 2.6f * zoom;

        float eye[3] = {
            target[0] + cam_dist * cos_p * std::sin(yaw_rad),
            target[1] + cam_dist * std::sin(pitch_rad),
            target[2] + cam_dist * cos_p * std::cos(yaw_rad),
        };

        float view[16], proj[16], vp[16];
        mat4_look_at(view, eye[0], eye[1], eye[2], target[0], target[1], target[2], 0, 1, 0);
        // Near/far both scale with the actual camera distance instead of being
        // fixed relative to the model's radius alone. The old fixed pair
        // (near=0.01, far=radius*50) broke down at either zoom extreme: zoomed
        // in close, a near plane way smaller than the viewing distance wastes
        // depth-buffer precision across the whole 0.01..50*radius range and
        // causes z-fighting flicker (surfaces randomly failing the depth test —
        // your "parts appear/disappear"); zoomed out far, cam_dist could exceed
        // the fixed far plane entirely and the whole model vanished, clipped.
        float near_p = std::max(cam_dist * 0.01f, 0.001f);
        float far_p  = cam_dist + radius * 20.0f;
        mat4_perspective(proj, 45.0f, (float)w / (float)hpx, near_p, far_p);
        mat4_multiply(vp, proj, view);

        // Clear.
        glViewport(0, 0, w, hpx);
        glClearColor(0.055f, 0.058f, 0.08f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);

        if (have_model) {
            GpuModel& gm = *gm_ptr;
            glUseProgram(s_model_prog);

            glUniform3fv(s_light_dir, 1, light_dir);
            glUniform3fv(s_light_col, 1, light_col);
            glUniform3fv(s_fill, 1, fill_col);
            glUniform3fv(s_ambient, 1, ambient);
            glUniform3fv(s_amb_ground, 1, amb_ground);
            glUniform1f(s_rim, rim);
            glUniform1f(s_spec, spec);
            glUniform1f(s_flat, 0);
            glUniform3fv(s_cam, 1, eye);

            // Center-offset so the model sits at origin.
            float global_model[16];
            mat4_identity(global_model);
            float centered_global[16];
            if (gm.pod.has_center_point) {
                float center_offset[16];
                mat4_translate(center_offset, -gm.pod.center_point[0], -gm.pod.center_point[1], -gm.pod.center_point[2]);
                mat4_multiply(centered_global, global_model, center_offset);
            } else {
                std::memcpy(centered_global, global_model, sizeof(centered_global));
            }

            int mesh_node_count = gm.node_count;
            if (mesh_node_count <= 0 || mesh_node_count > (int)gm.pod.nodes.size())
                mesh_node_count = (int)gm.pod.nodes.size();

            for (int i = 0; i < mesh_node_count; ++i) {
                const av::PODNode& node = gm.pod.nodes[i];
                if (node.object_index < 0 || node.object_index >= (int)gm.pod.meshes.size()) continue;
                GpuMesh& gm_mesh = gm.meshes[node.object_index];
                if (!gm_mesh.vao) continue;

                // Node matrix (frame interpolation for animation).
                float node_matrix[16];
                av::get_node_matrix(gm.pod, i, frame, node_matrix);
                float final_matrix[16];
                mat4_multiply(final_matrix, centered_global, node_matrix);

                // CPU skinning.
                if (gm.pod.meshes[node.object_index].bones_per_vertex > 0) {
                    std::vector<float> sk_pos, sk_nrm;
                    if (av::skin_mesh(gm.pod, i, frame, sk_pos, sk_nrm)) {
                        update_mesh_vertices(gm_mesh, sk_pos.data(), sk_nrm.empty() ? nullptr : sk_nrm.data(),
                                             (int)(sk_pos.size() / 3));
                    }
                }

                // Material color + texture.
                float mat_color[4] = { 1, 1, 1, 1 };
                GLuint tex_id = 0;
                int mat_idx = node.material_index;
                if (mat_idx >= 0 && mat_idx < (int)gm.pod.materials.size()) {
                    const auto& m = gm.pod.materials[mat_idx];
                    mat_color[0] = m.diffuse[0]; mat_color[1] = m.diffuse[1]; mat_color[2] = m.diffuse[2];
                    mat_color[3] = m.opacity;
                    int t = m.diffuse_texture_index;
                    if (t >= 0 && t < (int)gm.textures.size()) tex_id = gm.textures[t];
                }

                float mvp[16], nmat[9];
                mat4_multiply(mvp, vp, final_matrix);
                mat4_normal_matrix(nmat, final_matrix);

                glUniformMatrix4fv(s_mvp, 1, GL_FALSE, mvp);
                glUniformMatrix4fv(s_model, 1, GL_FALSE, final_matrix);
                glUniformMatrix3fv(s_nmat, 1, GL_FALSE, nmat);
                glUniform4fv(s_mat_color, 1, mat_color);
                glUniform1f(s_alpha, mat_color[3]);
                glUniform1i(s_has_tex, tex_id ? 1 : 0);
                if (tex_id) {
                    glActiveTexture(GL_TEXTURE0);
                    glBindTexture(GL_TEXTURE_2D, tex_id);
                    glUniform1i(s_tex, 0);
                }

                glBindVertexArray(gm_mesh.vao);
                bool has_ebo = gm_mesh.ebo != 0;
                if (has_ebo) {
                    glDrawElements(GL_TRIANGLES, gm_mesh.index_count, GL_UNSIGNED_INT, 0);
                } else {
                    glDrawArrays(GL_TRIANGLES, 0, gm_mesh.index_count);
                }

                if (wireframe && gm_mesh.wire_vao) {
                    // GLES wireframe: draw the edge line buffer on top (slightly
                    // depth-biased so edges always read against the shaded surface).
                    float wire_col[4] = { 0.2f, 0.8f, 1.0f, 0.8f };
                    glUniform4fv(s_mat_color, 1, wire_col);
                    glUniform1i(s_has_tex, 0);
                    glBindVertexArray(gm_mesh.wire_vao);
                    glDrawArrays(GL_LINES, 0, gm_mesh.wire_vertex_count);
                    glBindVertexArray(0);
                }
                glBindVertexArray(0);
            }
        }

        // Grid — always drawn (when enabled), even with no model loaded, so a
        // failed/pending load still shows a visible reference plane instead of
        // a black screen.
        if (show_grid && s_grid_prog) {
            glUseProgram(s_grid_prog);
            float grid_size = radius * 4.0f;
            float grid_model[16];
            mat4_identity(grid_model);
            // place grid at model's feet (or the origin, if nothing is loaded)
            grid_model[13] = have_model ? gm_ptr->pod.min_y : 0.0f;
            float gmvp[16];
            mat4_multiply(gmvp, vp, grid_model);
            glUniformMatrix4fv(s_g_mvp, 1, GL_FALSE, gmvp);
            glUniformMatrix4fv(s_g_model, 1, GL_FALSE, grid_model);
            glUniform1f(s_g_size, grid_size);
            float grid_col[4] = { 0.35f, 0.40f, 0.55f, 0.5f };
            glUniform4fv(s_g_color, 1, grid_col);
            // scale = world units per pixel at grid plane (approx)
            float grid_scale = (2.0f * radius) / (float)hpx;
            glUniform1f(s_g_scale, grid_scale);
            glBindVertexArray(s_grid_vao);
            glDrawArrays(GL_TRIANGLE_FAN, 0, 4);
            glBindVertexArray(0);
        }

        glBindVertexArray(0);
        glUseProgram(0);
    }

    std::string info(ModelHandle h) {
        if (h < 0 || h >= MAX_MODELS || !s_models[h].loaded) return "";
        return s_models[h].info_line;
    }

    int frame_count(ModelHandle h) {
        if (h < 0 || h >= MAX_MODELS || !s_models[h].loaded) return 0;
        return s_models[h].pod.num_frames;
    }

} // namespace podgl