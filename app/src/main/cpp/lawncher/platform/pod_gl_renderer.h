/* pod_gl_renderer.h — OpenGL ES 3.0 POD model renderer for the Android launcher.
 *
 * A faithful port of Ruby's POD viewer (SwordigoDesktop src/tools/av_renderer.cpp
 * + asset_viewer.cpp render loop): real GL shaders, orbit camera, node-matrix
 * transforms via av::get_node_matrix, skin_mesh animation, and diffuse texture
 * mapping decoded through the launcher's PVR/tex decoder.
 *
 * Lifecycle (all calls must happen on the GL thread / with a current context):
 *   pod_gl_init()                    once
 *   handle = pod_gl_load(data,size,base_dir)
 *   pod_gl_render(handle,w,h,yaw,pitch,dist,frame,wireframe,showGrid,autoRot)
 *   pod_gl_free(handle)              when done
 */

#ifndef POD_GL_RENDERER_H
#define POD_GL_RENDERER_H

#include <cstdint>
#include <cstddef>
#include <string>

namespace podgl {

// Opaque handle to a loaded POD model.
using ModelHandle = int;

// ─── Lifecycle ──────────────────────────────────────────────────────────
// Compile shaders. Call once after a GL context is current. Returns false on
// shader compile/link failure.
bool init();

// Load a POD model from an in-memory buffer. base_dir is used to resolve
// diffuse textures (a directory containing the .pod, or "" to skip textures).
// Returns a handle >= 0, or -1 on failure.
ModelHandle load(const uint8_t* data, size_t size, const std::string& base_dir);

// Free GPU resources for a model.
void free_model(ModelHandle h);

// ─── Rendering ──────────────────────────────────────────────────────────
// Render one frame of the model into the current GL framebuffer.
//   w, h        viewport size
//   yaw, pitch  orbit camera angles (degrees)
//   dist        camera distance from target
//   frame       animation frame (float, interpolated)
//   wireframe   draw as GL_LINES overlay
//   show_grid   draw the XZ grid plane
//   auto_rotate add time-based yaw drift (turntable)
void render(ModelHandle h, int width, int height,
            float yaw, float pitch, float dist,
            float frame, bool wireframe, bool show_grid, bool auto_rotate);

// One-line info string for the loaded model (empty handle -> empty).
std::string info(ModelHandle h);

// Animation frame count for the model (0 = static).
int frame_count(ModelHandle h);

} // namespace podgl

#endif /* POD_GL_RENDERER_H */
