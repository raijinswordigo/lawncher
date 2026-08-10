#ifndef LAWNCHER_VFS_H
#define LAWNCHER_VFS_H

/*
 * mod_vfs — the launcher's mod filesystem layer.
 *
 * Everything that used to be hard-wired inside the asset/save hooks now
 * lives here as one small, testable module:
 *
 *   • Session state (instance + mod ids, storage roots) fed from Java via
 *     MainActivity.initPaths()/setSession(), with a lazy JNI fallback for
 *     native-only sessions.
 *   • A single layered model:
 *         assets  ->  [mod overlay]  ->  [vanilla APK]   (read-only, fallback ok)
 *         saves   ->  [mod isolated]                    (no vanilla fallback ever)
 *   • Path normalization + containment: every mod-relative path is
 *     canonicalized and refused if it would escape the mod's own tree.
 *   • A bounded negative cache for asset misses, so per-frame loads of
 *     files a mod does NOT override stop hitting the filesystem.
 *   • Save seeding from the vanilla Documents/ dir, scoped per
 *     (instance, mod) session — not a process-global one-shot.
 *
 * The PUBLIC mod contract is untouched: zip layout (icon.png, resources/…,
 * properties.toml), the `mod_name` Lua global, and the JNI signatures.
 * This header is the internal remastered API.
 */

#include <stddef.h>

/* One-time init (call from init_assets()). Safe to call again. */
void vfs_init(void);

/* ---- Session configuration (fed by Java via JNI) ---- */

void vfs_set_paths(const char *internal_root, const char *external_root);
void vfs_set_session(const char *instance_id, const char *mod_id);

/* Current session ids ("" when vanilla). Triggers the lazy JNI fetch once. */
const char *vfs_mod_id(void);
const char *vfs_instance_id(void);

/* 1 when a mod is active AND its storage root is configured. */
int vfs_mod_active(void);

/* ---- Asset layer (mod overlay; caller falls back to vanilla on NULL) ---- */

/*
 * Returns a malloc'd buffer holding the mod's override for `game_path`
 * (e.g. "resources/foo.png"), or NULL when there is no override — meaning
 * the caller should load the vanilla asset. Handles normalization,
 * containment, and the negative cache internally.
 */
void *vfs_read_asset(const char *game_path, int *out_size);

/* 1 if the active mod overrides `game_path` (probe, no read). */
int vfs_asset_exists(const char *game_path);

/* ---- Save layer (fully mod-isolated; NULL means "no such save") ---- */

/*
 * Reads a save that the game requested under .../Documents/<name>.
 * Returns a malloc'd buffer or NULL. A NULL result MUST NOT fall back to
 * the shared vanilla save — saves are scoped to the active mod only.
 */
void *vfs_read_save(const char *game_path, int *out_size);

/*
 * Writes a save (write-then-rename, crash-safe). Returns 1 on success,
 * 0 on I/O failure. Same containment/isolation as the read path.
 */
int vfs_write_save(const char *game_path, const void *data, unsigned int size);

#endif //LAWNCHER_VFS_H
