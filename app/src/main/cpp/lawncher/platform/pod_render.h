/* pod_render.h — lightweight software 3D renderer for POD models.
 *
 * Part of the launcher's POD viewer (Raijin's request). Renders a parsed
 * PowerVR POD model to an RGBA8888 buffer using an orbit camera, painter's
 * algorithm depth sorting and flat shading — no OpenGL required.
 */

#ifndef POD_RENDER_H
#define POD_RENDER_H

#include <cstdint>
#include <cstddef>
#include <string>
#include <vector>

namespace podview {

/* Render a POD model (in-memory buffer) into an RGBA8888 image.
 *
 * @param data         POD file bytes
 * @param size         byte count
 * @param width,height output image size (clamped internally, max 2048)
 * @param rot_y,rot_x  orbit angles in radians (yaw, pitch)
 * @param zoom         scale factor, 1.0 = fit model to view
 * @param wireframe    true = edge lines only, false = flat-shaded solid
 * @param rgba_out     receives width*height*4 bytes (row-major, no alpha blend)
 * @return true on success
 */
bool render_pod(const uint8_t* data, size_t size,
                int width, int height,
                float rot_y, float rot_x, float zoom,
                bool wireframe, std::vector<uint8_t>& rgba_out);

/* Human-readable one-line summary of a POD model (used for the viewer title). */
std::string pod_info_line(const uint8_t* data, size_t size);

} // namespace podview

#endif /* POD_RENDER_H */
