/* pod_render.cpp — lightweight software 3D renderer for POD models.
 *
 * Orbit camera + painter's algorithm + flat shading, all in pure C++ so the
 * launcher can preview Swordigo .pod models without a GL surface.
 *
 * Matrix convention: column-major 4x4 (OpenGL style), matching
 * av::get_node_matrix in pod_loader.cpp.
 */

#include "pod_render.h"
#include "tools/pod_loader.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <string>

namespace podview {

namespace {

struct Vec3 { float x, y, z; };
struct Triangle {
    float z;                 // view-space average depth (painter sort)
    float shade;             // flat shading intensity 0..1
    int v[3];                // indices into projected vertex list
};

/* ── small math helpers ─────────────────────────────────────────────── */

inline float clampf(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

inline void mat4_identity(float m[16]) {
    std::memset(m, 0, 16 * sizeof(float));
    m[0] = m[5] = m[10] = m[15] = 1.0f;
}

/* M * p (column-major M, p treated as column vector). */
inline void mat4_mul_vec(const float m[16], const float p[3], float out[3]) {
    out[0] = m[0] * p[0] + m[4] * p[1] + m[8]  * p[2] + m[12];
    out[1] = m[1] * p[0] + m[5] * p[1] + m[9]  * p[2] + m[13];
    out[2] = m[2] * p[0] + m[6] * p[1] + m[10] * p[2] + m[14];
}

inline void mat4_mul_vec3(const float m[16], const float p[3], float out[3]) {
    out[0] = m[0] * p[0] + m[4] * p[1] + m[8]  * p[2];
    out[1] = m[1] * p[0] + m[5] * p[1] + m[9]  * p[2];
    out[2] = m[2] * p[0] + m[6] * p[1] + m[10] * p[2];
}

inline Vec3 cross3(const Vec3& a, const Vec3& b) {
    return {a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x};
}

inline float dot3(const Vec3& a, const Vec3& b) { return a.x * b.x + a.y * b.y + a.z * b.z; }

inline Vec3 norm3(const Vec3& v) {
    float len = std::sqrt(dot3(v, v));
    if (len < 1e-6f) return {0, 0, 0};
    return {v.x / len, v.y / len, v.z / len};
}

/* ── tiny rasterizer ────────────────────────────────────────────────── */

struct Pixel {
    int x, y;
    float shade;
};

/* Edge-function triangle fill (conservative). Assumes screen-space verts. */
static void fill_triangle(std::vector<uint8_t>& img, int W, int H,
                          float ax, float ay, float bx, float by, float cx, float cy,
                          float shade, bool wireframe, const float color[3]) {
    int minx = (int)std::floor(std::min({ax, bx, cx}));
    int maxx = (int)std::ceil(std::max({ax, bx, cx}));
    int miny = (int)std::floor(std::min({ay, by, cy}));
    int maxy = (int)std::ceil(std::max({ay, by, cy}));
    minx = std::max(minx, 0); maxx = std::min(maxx, W - 1);
    miny = std::max(miny, 0); maxy = std::min(maxy, H - 1);

    if (wireframe) return; // lines drawn separately

    float dxAB = bx - ax, dyAB = by - ay;
    float dxBC = cx - bx, dyBC = cy - by;
    float dxCA = ax - cx, dyCA = ay - cy;

    for (int y = miny; y <= maxy; ++y) {
        for (int x = minx; x <= maxx; ++x) {
            float px = x + 0.5f, py = y + 0.5f;
            float w0 = (px - ax) * dyAB - (py - ay) * dxAB;
            float w1 = (px - bx) * dyBC - (py - by) * dxBC;
            float w2 = (px - cx) * dyCA - (py - cy) * dxCA;
            // All same sign (or zero) => inside
            if ((w0 >= 0 && w1 >= 0 && w2 >= 0) || (w0 <= 0 && w1 <= 0 && w2 <= 0)) {
                size_t off = ((size_t)y * W + x) * 4;
                img[off + 0] = (uint8_t)clampf(color[0] * shade * 255.0f, 0, 255);
                img[off + 1] = (uint8_t)clampf(color[1] * shade * 255.0f, 0, 255);
                img[off + 2] = (uint8_t)clampf(color[2] * shade * 255.0f, 0, 255);
                img[off + 3] = 255;
            }
        }
    }
}

static void draw_line(std::vector<uint8_t>& img, int W, int H,
                      float x0f, float y0f, float x1f, float y1f,
                      const float color[3], float shade) {
    int x0 = (int)std::lround(x0f), y0 = (int)std::lround(y0f);
    int x1 = (int)std::lround(x1f), y1 = (int)std::lround(y1f);
    int dx = std::abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
    int dy = -std::abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
    int err = dx + dy;
    for (;;) {
        if (x0 >= 0 && x0 < W && y0 >= 0 && y0 < H) {
            size_t off = ((size_t)y0 * W + x0) * 4;
            img[off + 0] = (uint8_t)clampf(color[0] * shade * 255.0f, 0, 255);
            img[off + 1] = (uint8_t)clampf(color[1] * shade * 255.0f, 0, 255);
            img[off + 2] = (uint8_t)clampf(color[2] * shade * 255.0f, 0, 255);
            img[off + 3] = 255;
        }
        if (x0 == x1 && y0 == y1) break;
        int e2 = 2 * err;
        if (e2 >= dy) { err += dy; x0 += sx; }
        if (e2 <= dx) { err += dx; y0 += sy; }
    }
}

/* Project a world-space vertex with orbit camera into NDC. Returns false if
 * behind the near plane. */
struct Camera {
    float eye[3];
    float center[3];
    float up[3] = {0, 1, 0};
    float fov_scale;
};

} // namespace

std::string pod_info_line(const uint8_t* data, size_t size) {
    av::PODModel m = av::pod_parse(data, size);
    if (m.meshes.empty()) return "POD model (no meshes)";
    std::string out = "POD | meshes " + std::to_string(m.meshes.size());
    if (m.num_frames > 1) out += " | anim " + std::to_string(m.num_frames) + "f";
    out += " | verts " + std::to_string(m.total_vertices);
    out += " | tris " + std::to_string(m.total_faces);
    return out;
}

bool render_pod(const uint8_t* data, size_t size,
                int width, int height,
                float rot_y, float rot_x, float zoom,
                bool wireframe, std::vector<uint8_t>& rgba_out) {
    if (!data || size == 0 || width <= 0 || height <= 0) return false;

    av::PODModel model = av::pod_parse(data, size);
    if (model.meshes.empty()) return false;

    width  = std::min(width, 2048);
    height = std::min(height, 2048);

    rgba_out.assign((size_t)width * height * 4, 0);

    // Background: deep blue-black gradient (simple flat fill).
    for (size_t i = 0; i < (size_t)width * height; ++i) {
        rgba_out[i * 4 + 0] = 14; rgba_out[i * 4 + 1] = 16; rgba_out[i * 4 + 2] = 26; rgba_out[i * 4 + 3] = 255;
    }

    // Orbit camera. Rotations applied as: yaw around world Y, then pitch.
    const float cy = std::cos(rot_y), sy = std::sin(rot_y);
    const float cp = std::cos(rot_x), sp = std::sin(rot_x);

    float radius = std::max(model.radius, 0.01f);
    float dist = radius * 2.6f / std::max(zoom, 0.1f);

    // View direction: from eye to center.
    Camera cam;
    cam.center[0] = model.center_x;
    cam.center[1] = model.center_y;
    cam.center[2] = model.center_z;
    // Eye = center + dist * (yaw, pitch) spherical offset
    cam.eye[0] = cam.center[0] + dist * sp * cy;
    cam.eye[1] = cam.center[1] + dist * cp;
    cam.eye[2] = cam.center[2] + dist * sp * sy;

    float fwd[3] = { cam.center[0] - cam.eye[0], cam.center[1] - cam.eye[1], cam.center[2] - cam.eye[2] };
    Vec3 f = norm3({fwd[0], fwd[1], fwd[2]});
    Vec3 r = norm3(cross3(f, {cam.up[0], cam.up[1], cam.up[2]}));
    Vec3 u = cross3(r, f);

    // View matrix (row-major basis | -dot(axis,eye)).
    float view[16];
    view[0] = r.x; view[1] = u.x; view[2] = -f.x; view[3] = 0;
    view[4] = r.y; view[5] = u.y; view[6] = -f.y; view[7] = 0;
    view[8] = r.z; view[9] = u.z; view[10] = -f.z; view[11] = 0;
    view[12] = -dot3(r, {cam.eye[0], cam.eye[1], cam.eye[2]});
    view[13] = -dot3(u, {cam.eye[0], cam.eye[1], cam.eye[2]});
    view[14] =  dot3(f, {cam.eye[0], cam.eye[1], cam.eye[2]});
    view[15] = 1;

    // Perspective: fov ~45 deg vertical.
    const float fov = 0.7854f; // 45deg
    float focal = (float)height / (2.0f * std::tan(fov / 2.0f));
    float near_p = 0.05f * radius;
    float far_p  = 50.0f * radius;

    // Gather all projected vertices + build triangle list.
    std::vector<float> px, py, pz;   // screen space
    std::vector<Triangle> tris;

    const float shade_color[3] = {0.66f, 0.78f, 1.0f}; // soft blue (Xed primary-ish)
    const float wire_color[3]  = {0.68f, 0.78f, 0.95f};

    // Light direction (world space, normalized).
    Vec3 light = norm3({-0.4f, 0.8f, 0.45f});

    int mesh_node_count = model.num_mesh_nodes;
    if (mesh_node_count <= 0 || mesh_node_count > (int)model.nodes.size())
        mesh_node_count = (int)model.nodes.size();

    for (int node_idx = 0; node_idx < mesh_node_count; ++node_idx) {
        const av::PODNode& node = model.nodes[node_idx];
        if (node.object_index < 0 || node.object_index >= (int)model.meshes.size()) continue;
        const av::PODMesh& mesh = model.meshes[node.object_index];
        if (mesh.positions.empty()) continue;

        float world[16];
        av::get_node_matrix(model, node_idx, 0.0f, world);

        int base = (int)px.size() / 3;
        size_t vcount = mesh.positions.size() / 3;
        px.reserve(px.size() + vcount);
        py.reserve(py.size() + vcount);
        pz.reserve(pz.size() + vcount);

        for (size_t vi = 0; vi < vcount; ++vi) {
            const float* p = &mesh.positions[vi * 3];
            float wpos[3], vpos[3];
            mat4_mul_vec(world, p, wpos);
            mat4_mul_vec(view, wpos, vpos);

            // perspective divide. View-space z is NEGATIVE in front of the
            // camera (OpenGL convention) - flip to positive depth.
            float vz = -vpos[2];
            if (vz < near_p) vz = near_p;
            float s = focal / vz;
            px.push_back((float)width  * 0.5f + vpos[0] * s);
            py.push_back((float)height * 0.5f - vpos[1] * s);
            pz.push_back(vz);
        }

        // Build triangles. Prefer the index list; some PODs (e.g. deadtree1)
        // store a direct triangle list with NO index buffer - handle that too.
        const std::vector<uint32_t>& idx = mesh.indices;
        size_t n = idx.size();
        if (n < 3 && mesh.positions.size() >= 9) {
            // Non-indexed triangle list: every 3 consecutive verts = 1 triangle.
            size_t tri_count = mesh.positions.size() / 9;
            for (size_t t = 0; t < tri_count; ++t) {
                int a = base + (int)(t * 3), b = a + 1, c = a + 2;
                const float* pa = &mesh.positions[t * 9];
                const float* pb = pa + 3;
                const float* pc = pa + 6;
                float wa[3], wb[3], wc[3];
                mat4_mul_vec(world, pa, wa);
                mat4_mul_vec(world, pb, wb);
                mat4_mul_vec(world, pc, wc);
                Vec3 nn = cross3({wb[0] - wa[0], wb[1] - wa[1], wb[2] - wa[2]},
                                 {wc[0] - wa[0], wc[1] - wa[1], wc[2] - wa[2]});
                nn = norm3(nn);
                float diff = std::max(0.0f, dot3(nn, light));
                float shade = 0.32f + 0.68f * diff;
                float avgz = (pz[a] + pz[b] + pz[c]) / 3.0f;
                tris.push_back({avgz, shade, a, b, c});
            }
        } else if (n >= 3) {
            if (n % 3 == 0) {
                for (size_t i = 0; i + 2 < n; i += 3) {
                    int a = base + (int)idx[i], b = base + (int)idx[i + 1], c = base + (int)idx[i + 2];
                    if (a < base || b < base || c < base) continue;
                    if (a >= (int)px.size() / 3 || b >= (int)px.size() / 3 || c >= (int)px.size() / 3) continue;

                    // World-space normal for shading (backface-aware).
                    const float* pa = &mesh.positions[idx[i] * 3];
                    const float* pb = &mesh.positions[idx[i + 1] * 3];
                    const float* pc = &mesh.positions[idx[i + 2] * 3];
                    float wa[3], wb[3], wc[3];
                    mat4_mul_vec(world, pa, wa);
                    mat4_mul_vec(world, pb, wb);
                    mat4_mul_vec(world, pc, wc);
                    Vec3 n = cross3({wb[0] - wa[0], wb[1] - wa[1], wb[2] - wa[2]},
                                    {wc[0] - wa[0], wc[1] - wa[1], wc[2] - wa[2]});
                    n = norm3(n);
                    float diff = std::max(0.0f, dot3(n, light));
                    float shade = 0.32f + 0.68f * diff; // ambient floor

                    float avgz = (pz[a] + pz[b] + pz[c]) / 3.0f;
                    tris.push_back({avgz, shade, a, b, c});
                }
            } else if (n % 4 == 0) {
                // Quads -> two tris
                for (size_t i = 0; i + 3 < n; i += 4) {
                    int a = base + (int)idx[i], b = base + (int)idx[i + 1],
                        c = base + (int)idx[i + 2], d = base + (int)idx[i + 3];
                    tris.push_back({(pz[a] + pz[b] + pz[c]) / 3.0f, 0.7f, a, b, c});
                    tris.push_back({(pz[a] + pz[c] + pz[d]) / 3.0f, 0.7f, a, c, d});
                }
            }
        }

        // Points fallback: vertices with no faces still render as small dots.
        if (n < 3) {
            for (size_t vi = 0; vi < vcount; ++vi) {
                int v = base + (int)vi;
                if (v >= 0 && v < (int)px.size() / 3) {
                    tris.push_back({pz[v], 0.8f, v, v, v});
                }
            }
        }
    }

    if (px.empty()) return false;

    // Painter's algorithm: far -> near.
    std::sort(tris.begin(), tris.end(), [](const Triangle& t0, const Triangle& t1) { return t0.z > t1.z; });

    for (const Triangle& t : tris) {
        int a = t.v[0], b = t.v[1], c = t.v[2];
        if (a == b && b == c) {
            // degenerate point dot
            if (px[a] >= 0 && px[a] < width && py[a] >= 0 && py[a] < height) {
                size_t off = ((size_t)(int)py[a] * width + (int)px[a]) * 4;
                uint8_t v = (uint8_t)clampf(0.66f * t.shade * 255.0f, 0, 255);
                rgba_out[off + 0] = v; rgba_out[off + 1] = v; rgba_out[off + 2] = (uint8_t)clampf(v * 1.3f, 0, 255); rgba_out[off + 3] = 255;
            }
            continue;
        }
        if (wireframe) {
            draw_line(rgba_out, width, height, px[a], py[a], px[b], py[b], wire_color, 1.0f);
            draw_line(rgba_out, width, height, px[b], py[b], px[c], py[c], wire_color, 1.0f);
            draw_line(rgba_out, width, height, px[c], py[c], px[a], py[a], wire_color, 1.0f);
        } else {
            fill_triangle(rgba_out, width, height, px[a], py[a], px[b], py[b], px[c], py[c], t.shade, false, shade_color);
        }
    }

    return true;
}

} // namespace podview
