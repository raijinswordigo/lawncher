/* pvr_loader.h — PVR texture file loader for the Android launcher port.
 *
 * Android-flavoured header: GL-free, exposes only the software decode path
 * (pvr_decode_to_rgba) used by the launcher's PVR / texpng viewer. The
 * desktop original (SwordigoDesktop/src/platform/pvr_loader.h) also exposes
 * GL texture-upload helpers which are irrelevant here.
 *
 * Handles:
 *   - PVR v2 (44-byte header) and v3 (52-byte header) files
 *   - ETC1, PVRTC 2/4bpp, DXT1/3/5, uncompressed pixel formats
 *   - gzipped .tex files (auto gzip decompression)
 *   - raw .tex files with the 12-byte header (RGBA_8888/4444/5551,
 *     RGB_888/565, LUMINANCE_8, ALPHA_8)
 */

#ifndef PVR_LOADER_H
#define PVR_LOADER_H

#include <stdint.h>
#include <stddef.h>
#include <vector>

/* Decode a PVR or gzipped .tex file buffer directly to RGBA8888.
 * Handles automatic gzip decompression.
 * Returns true on success. */
bool pvr_decode_to_rgba(const uint8_t* file_data, size_t file_size,
                        std::vector<uint8_t>& rgba_out, int& width, int& height);

#endif /* PVR_LOADER_H */
