# PostFX — Feasibility Study → P0 implemented, v2 backend (ES1 fixed-function)

**Status:** Study complete; the P0 slice is now live: `com/L/SwordigoRuntime/PostFx.java`
**v2 (current):** the first implementation called GLES20 APIs inside the game's GLES1 EGL context and crashed. The backend was rebuilt as pure GLES1 fixed-function — OES FBO chain, blend-based prebaked mask pass, INTERPOLATE grading passes drawn via `glDrawTexOES` (no client arrays/VBOs), full state save/restore, and capability gating (GL_OES_framebuffer_object + GL_OES_texture_npot) with graceful self-disable. Zero GLES20 references remain.
wraps `Native.drawApplication()` with a host FBO (GLES11Ext OES, ES1-context safe)
and applies a single fullscreen filter pass — presets **Off / Vibrant / Retro /
Noir / Warm Film** — selectable in **Settings → POSTFX**. GL state is saved and
restored so the game's next frame is untouched. Remaining phases in §5 (bloom,
god rays, depth-based effects, FSR upscale) are still unimplemented.
This report is based on the **real engine source** referenced by `seethis.md` —
`/home/quantumcreeper/SwordigoDesktop/src` — where a complete FBO +
post-processing pipeline already exists for the desktop build. The question
this study answers: *can that pipeline be ported into the Android Lawncher
app (`liblawncher.so` + GlossHook), and at what cost?*

---

## 1. Source of truth (what was studied)

| File | Role |
|------|------|
| `src/platform/fbo_scaler.h` | Public API: `PostFXState`, `FBOScale`, `PostFXPreset`, `fbo_init/destroy/begin_game/end_game_and_blit` |
| `src/platform/fbo_scaler.cpp` | Full implementation: 13 shaders, FBO set, 7-pass pipeline, presets, JSON custom presets |
| `src/jni/gl_render_state.h` | The GL-state "handshake": per-frame lights / materials / matrices / color written by bridge hooks, read by the composite shader |
| `src/main.cpp` | Frame-loop integration (`fbo_begin_game` → draw → `fbo_end_game_and_blit`), render-resolution presets (240p→4K), F4/F6 debug toggles |
| `src/jni/jni_bridge*.{cpp,h}` | ARM64 bridge that captures guest fixed-function GL state into `gl_render_state` |

---

## 2. What the engine already implements (feature inventory)

### 2.1 Upscale modes (`FBOScale`)
`SHARP_BILINEAR` (default), `NEAREST`, `CRT_SCANLINE`, `FSR` (AMD FSR 1.0 EASU).
Game renders at internal `GAME_W×GAME_H` (native 960×544) into an FBO and is
upscaled to the window — resolution presets range 240p → 4K, live-switchable
via `apply_render_preset()` (rebuilds FBO + re-calls the engine's
`setApplicationViewSize`).

### 2.2 Effect tiers (`PostFXState`)
- **Tier 1 — color:** vignette, film grain, chromatic aberration,
  color adjust (saturation / contrast / brightness / warmth), sharpen.
- **Tier 2 — advanced:** god rays (UV sun position, intensity, decay),
  volumetric light, **SSAO** (radius / intensity), **bloom** (threshold /
  intensity, quarter-res), **shadows** (2D screen-space + 3D `sun_z`
  component, soft edges), **outlines** (depth-based cel-shading edges).
- **Tier 3 — remaster:** **PBR** (Cook-Torrance BRDF + LabPBR materials) and
  **dynamic wave reflections** (intensity blend).

### 2.3 Presets (`PostFXPreset`)
`OFF`, `SW_PLUS_MEDIUM`, `SW_PLUS_HIGH`, `ATMOSPHERIC`, `ETHEREAL`,
`CINEMATIC`, `RETRO`, `FANTASY`, `NOIR`, `CUSTOM`, plus JSON
load/save of custom presets (`postfx_load_custom_json` /
`postfx_save_default_json`).

### 2.4 The pass pipeline (`fbo_end_game_and_blit`)
```
1. SSAO  @ half-res (g_half_fbo_a) → blur (g_half_fbo_b)
2. God rays / volumetric @ half-res (ping-pong a/b)
3. Bloom extract @ quarter-res (g_bloom_fbo_a) → blur (a/b ping-pong)
4. Portal glow drawn into scene FBO (g_fbo)
5. Composite @ full-res (g_postfx_fbo): scene + AO + god rays + bloom
   + shadows + PBR + reflections — driven by per-frame GL-state uniforms
6. Tier-1 color postfx @ full-res (ping-pong g_postfx_fbo / _b)
7. Final upscale blit to screen (blit fallback or sharp/nearest/CRT/FSR shader)
```
Resource set: scene FBO + depth **texture** (not renderbuffer — depth must be
sampleable), 2× full-res postfx FBOs, 2× half-res, 2× quarter-res bloom.
Shaders are raw GLSL string constants in the `.cpp` (no asset files).

### 2.5 GL-state handshake (`gl_render_state.h`)
Every frame the bridge hooks capture: `GL_LIGHT0..7` (position/ambient/
diffuse/specular/enabled), light-model ambient, material (ambient/diffuse/
specular/emission/shininess), current modelview & projection matrices,
current vertex color, and the host-cached **hero position** — so shaders
react to the actual scene (light-reactive SSAO/shadows/PBR) rather than
being purely static. This is the *only* part that depends on the desktop
ARM64-emulator plumbing.

---

## 3. The core question: can this port to Android?

### 3.1 Architectural difference (this is the crux)

**Desktop:** the game runs inside an ARM64 **emulator**; the host *owns the GL
context* and wraps the whole draw call — `fbo_begin_game()` binds the host
FBO, the emulated `drawApp` runs, then `fbo_end_game_and_blit()` composites
and presents. Guest fixed-function GL state is captured by the bridge hooks.

**Android (Lawncher):** the game runs **natively in-process** (`libswordigo.so`)
and renders into the `GLSurfaceView` default framebuffer. There is no
emulator seam. BUT the seam that exists is equivalent:

```
GameRenderer.onDrawFrame (GL thread)
 ├─ Native.updateApplication(dt)
 ├─ Native.drawApplication()      ← wrap here
 └─ (GLSurfaceView presents)
```

Because **everything between surface-created and present happens on one GL
thread**, we can bind our own FBO in native code *before*
`Native.drawApplication()` and run the whole `fbo_end_game_and_blit()`
composite *after* it — the desktop pattern maps 1:1, minus the emulator.
Swordigo's renderer is fixed-function GLES1-style (see `gl_render_state.h`:
`GL_LIGHT0..7`, `glMaterialfv`, matrix stack) — it draws to whatever
framebuffer is bound and does not bind its own FBOs, so the wrap is safe in
practice (verify with a runtime probe; GlossHook can intercept
`glBindFramebuffer` defensively).

### 3.2 Option table

| # | Approach | Feasibility | Notes |
|---|----------|-------------|-------|
| **A** | **FBO-wrap `Native.drawApplication()`** (port of the desktop pipeline) | ✅ **High — recommended** | Direct 1:1 port of the existing, proven `fbo_scaler`. Game renders into our FBO; every pass and shader already exists. Needs GLES2 FBO + optional depth-texture. |
| **B** | `eglSwapBuffers` hook + copy-to-texture | ⚠️ Medium | Simpler depth story (no game draw into FBO), but loses the exact desktop architecture; copy cost; no depth texture for SSAO/outlines from default FB. |
| **C** | Inject into the game's own pipeline | ❌ Low now | Requires per-version RE of `libswordigo.so`'s renderer. Revisit only if A fails. |

**Verdict: Approach A.** The desktop code is literally a module (`fbo_scaler.cpp`)
wrapping a draw call — the Android seam is the same shape.

### 3.3 GLES requirement audit (Android, minSdk 24)

| Requirement | Status on Android |
|-------------|-------------------|
| `OES_framebuffer_object` / FBOs | Universal on GLES2. ✅ |
| Depth as **texture** (needed by SSAO / outlines / shadows) | `OES_depth_texture` on GLES2; native on GLES3 (near-universal at API 24+). **Probe at runtime; degrade gracefully to "no depth-based FX" if absent.** ✅ with fallback |
| `GL_EXT_framebuffer_blit` (fast final blit) | Optional — shader-quad fallback exists in the engine already. ✅ |
| Shader dialect | Desktop GLSL must be converted to **GLSL ES 1.00** (`texture2D`, precision qualifiers). FSR EASU has official ES ports. Mechanical, ~1 session. |
| `setApplicationViewSize` (render-preset resize) | **Already exposed** via `Native.setApplicationViewSize(w, h, …)` — same entry point the desktop engine re-calls. ✅ zero new plumbing |

### 3.4 The GL-state handshake on Android
The light/material/matrix uniforms come from **intercepted API calls** (bridge
hooks). On Android the same interception is doable — GlossHook already hooks
game symbols in-process (`GlossHook(func, new_func, &old_func)`), so hooking
`glLightfv / glMaterialfv / glMatrixMode / glLoadMatrixf / glColor4f` captures
the identical state with roughly the same code as the desktop bridge.
**However**, this is the *only* genuinely hard part, and none of the
Tier-1 / bloom / god-rays / CRT / FSR effects need it — they are pure
screen-space. Recommendation: **ship without the handshake first** (fixed sun
position, no scene-reactive SSAO/shadows), add it as a later enhancement.
This cuts port effort dramatically.

---

## 4. Performance budget (Android mobile GPU, 960×544 game res)

| Pass | Est. cost |
|------|-----------|
| Tier-1 color pass (full-res ping-pong) | ~0.3–0.8 ms |
| Bloom (quarter-res extract + 2× blur) | ~0.4–1.0 ms |
| God rays (half-res) | ~0.3–0.8 ms |
| SSAO (half-res + blur) — depth-based | ~0.8–2.0 ms |
| Final upscale (sharp/FSR) | ~0.2–0.6 ms |
| **Typical combo (Tier-1 + bloom + upscale)** | **~1.0–2.5 ms** → safe at 60 fps on mid-range |
| Full "SW+ High" stack | ~3–5 ms → enable only on strong GPUs / lower game res |

Framebuffer blits at 960×544 (and upscale to the device panel) are cheap;
the game's native res is low by design, which is exactly why the desktop
pipeline works at 60fps. Cap stacking in the UI + optional framerate-floor
auto-disable.

---

## 5. Port plan (if approved — NOT now)

```
app/src/main/cpp/core/postfx.c + postfx.h        ← port of fbo_scaler.cpp
  · GLES2 port of the 13 shader strings (ES 1.00 dialect)
  · fbo wrap: postfx_begin() / postfx_end_and_blit(w, h, mode, state)
  · preset table + JSON custom preset (assets/modstore/ or files dir)
app/src/main/cpp/core/hook.c                     ← defensive glBindFramebuffer hook (optional)
app/src/main/cpp/main.c                           ← wrap Native.drawApplication() body:
                                                      postfx_begin(); drawApp…; postfx_end_and_blit();
app/src/main/java/…/Native.java + SettingsScreen  ← enable + preset picker + scale mode
Per-mod preset in the Store mod detail screen     ← reuse IAP/mod infra already built
```

| Phase | Scope | Effort | Risk |
|-------|-------|--------|------|
| **P0** | FBO wrap + Tier-1 (color grade/vignette/grain/CA/sharpen) + upscale modes (sharp/nearest/CRT) + global toggle | ~2–3 sessions | Low |
| **P1** | Bloom + god rays + presets (incl. JSON custom) + per-mod preset + Settings UI polish | ~1 week | Low-Med |
| **P2** | Depth-texture SSAO/outlines/shadows (probe-gated) | ~3–5 days | Med |
| **P3** | GL-state handshake via GlossHook (light-reactive SSAO/PBR/reflections, FSR) | ~1–2 weeks | Med-High |
| **P4** | Perf autotune (fps floor), device allow/deny list | ~2 days | Low |

---

## 6. Risk register

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| Game internally rebinds FB 0 during draw | Low | Runtime probe on first frame; defensive GlossHook on `glBindFramebuffer`. |
| Depth-texture missing on some GLES2 devices | Low-Med (rare at API 24+) | Probe; drop depth-based effects only, keep Tier-1/bloom. |
| GLSL desktop→ES conversion bugs | Med | ES 1.00 rewrite per shader + on-device validation pass. |
| Context loss / surface resize | Med | Rebuild FBO set in `reloadContext` / `onSurfaceChanged` (desktop already does this via `fbo_destroy/fbo_init`). |
| Perf on old GPUs | Med | Cap stacking, fps-floor auto-disable, lower internal res (presets already exist). |
| Menu/UI scenes get postfx'd (gameplay FX over menus) | Med | Desktop's menu-suppression infra (`g_sre_menu_active`) was disabled as unreliable — defer; simple heuristic: pause FX when touch-driven menu is open. |
| Game update changes hook symbols | Low | Hook stable exported symbols (`drawApplication` is ours; GL hooks by name). |

---

## 7. Conclusion

**Feasible — high confidence.** The postfx system the user pointed to in
`seethis.md` is a complete, working, self-contained FBO pipeline in the
SwordigoDesktop engine. Its integration seam (wrap the game's draw call with
a host FBO) has an exact Android analogue: wrapping `Native.drawApplication()`
on the GL thread. Two thirds of the effects (all of Tier-1, bloom, god rays,
all upscale modes incl. FSR) port with **no engine reverse-engineering** and
no Google APIs; only depth-based SSAO/outlines/shadows and the scene-reactive
handshake need the harder GL-state interception, which GlossHook can provide.

Recommended path if approved: **P0 → P1** (screen-space effects + presets +
UI) first — that delivers the visible "postfx" experience — then **P2/P3**
for the depth-based and scene-reactive tiers.

**Not implemented** — this document is the feasibility study only.
