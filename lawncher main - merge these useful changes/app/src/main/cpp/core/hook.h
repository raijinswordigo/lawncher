#ifndef LAWNCHER_HOOK_H
#define LAWNCHER_HOOK_H

#include "../libs/Gloss.h"
#include <dlfcn.h>
#include <stdint.h>

// The .so you're hooking into — override before including this header if needed
#ifndef HOOK_LIB_NAME
#define HOOK_LIB_NAME "libswordigo.so"
#endif

// ---------------------------------------------------------------------
// archSplit(arm_val, arm64_val) — pick a value for the current arch.
// Defined once via #if here; never needs #if again at any call site.
// ---------------------------------------------------------------------
#if defined(__aarch64__)
#define archSplit(arm_val, arm64_val) (arm64_val)
#elif defined(__arm__)
#define archSplit(arm_val, arm64_val) (arm_val)
#else
    #error "GlossHook only supports arm/arm64"
#endif

// Offset pair -> resolved value for current arch, as uintptr_t
#define OFFSET(off32, off64) ((uintptr_t)archSplit(off32, off64))

// ---------------------------------------------------------------------
// $(type, base, off32, off64) -> typed pointer at (base + arch offset)
//
// Usage:
//   *$(float, g_lib_bias, 0x1234, 0x5678) = 1.0f;
//   int hp = *$(int, thiz, 0xA0, 0x120);
// ---------------------------------------------------------------------
#define $(type, base, off32, off64) \
    ((type*)((uintptr_t)(base) + OFFSET(off32, off64)))

// ---------------------------------------------------------------------
// HOOK_SYMBOL(name, "mangled_symbol", ret_type, (arg_types...)) { body }
//
// Generates:
//   name##_t         - function pointer typedef
//   orig_##name      - pointer to the original, call this from your body
//   hook_##name      - your replacement (the { ... } you write after the macro)
//   register_##name  - constructor that auto-registers the hook
// ---------------------------------------------------------------------
#define HOOK_SYMBOL(name, symbol_str, ret, args)                          \
    typedef ret (*name##_t) args;                                         \
    static name##_t orig_##name = NULL;                                   \
    static ret hook_##name args;                                          \
    __attribute__((constructor))                                          \
    static void register_##name(void) {                                   \
        GlossInit(true);                                                  \
        GlossHookByName(HOOK_LIB_NAME, symbol_str,                        \
                         (void*)hook_##name, (void**)&orig_##name, NULL); \
    }                                                                     \
    static ret hook_##name args

// ---------------------------------------------------------------------
// HOOK_OFFSET(name, off32, off64, ret_type, (arg_types...)) { body }
//
// Same as HOOK_SYMBOL but targets a raw offset from lib_bias instead of
// a symbol name — useful when the symbol is stripped. Arch-split offset
// is resolved automatically via archSplit, no #if needed at call site.
// ---------------------------------------------------------------------
#define HOOK_OFFSET(name, off32, off64, ret, args)                        \
    typedef ret (*name##_t) args;                                         \
    static name##_t orig_##name = NULL;                                   \
    static ret hook_##name args;                                          \
    __attribute__((constructor))                                          \
    static void register_##name(void) {                                  \
        GlossInit(true);                                                  \
        GlossHookAddrByName(HOOK_LIB_NAME, OFFSET(off32, off64),          \
                             (void*)hook_##name, (void**)&orig_##name,    \
                             false, I_NONE, NULL);                        \
    }                                                                     \
    static ret hook_##name args

// ---------------------------------------------------------------------
// DL_SYMBOL(name, "mangled_symbol", ret_type, (arg_types...));
//
// Resolves a symbol you just want to CALL, not hook. Call
// resolve_##name() once (e.g. from init_hooks()) before using `name`.
// ---------------------------------------------------------------------
#define DL_SYMBOL(name, symbol_str, ret, args)                            \
    typedef ret (*name##_t) args;                                         \
    static name##_t name = NULL;                                         \
    static void resolve_##name(void) {                                   \
        if (name) return;                                                 \
        void* h = dlopen(HOOK_LIB_NAME, RTLD_NOW | RTLD_NOLOAD);          \
        if (h) name = (name##_t)dlsym(h, symbol_str);                    \
    }

// ---------------------------------------------------------------------
// Lib layout essentials — lazily resolved, safe to call anytime after
// the target lib is loaded.
// ---------------------------------------------------------------------
uintptr_t get_lib_bias(void);          // load_bias / base address
uintptr_t get_lib_bss(size_t* size);   // .bss section start (+ size out)
uintptr_t get_lib_data(size_t* size);  // .data section start (+ size out)
uintptr_t get_lib_text(size_t* size);  // .text section start (+ size out)
void *swordigo_dlsym(const char *symbol);

void init_hooks(void);

#endif //LAWNCHER_HOOK_H