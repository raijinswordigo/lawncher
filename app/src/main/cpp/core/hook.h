#ifndef LAWNCHER_HOOK_H
#define LAWNCHER_HOOK_H
#include "../libs/Gloss.h"
#include <dlfcn.h>
#include <stdint.h>
// from LGL Mod Menu thing but it doesnt matter
#ifndef HOOK_LIB_NAME
#define HOOK_LIB_NAME "libswordigo.so"
#endif
// SwMini Concept (?)
#if defined(__aarch64__)
#define archSplit(arm_val, arm64_val) (arm64_val)
#elif defined(__arm__)
#define archSplit(arm_val, arm64_val) (arm_val)
#else
    #error "GlossHook only supports arm/arm64"
#endif
// OFFSET THING
#define OFFSET(off32, off64) ((uintptr_t)archSplit(off32, off64))
#define $(type, base, off32, off64) \
    ((type*)((uintptr_t)(base) + OFFSET(off32, off64)))
// attribute constuctor auto hook reg
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
// same as above
#define HOOK_OFFSET(name, off32, off64, ret, args)                        \
    typedef ret (*name##_t) args;                                         \
    static name##_t orig_##name = NULL;                                   \
    static ret hook_##name args;                                          \
    static void register_##name(void) {                                  \
        GlossInit(true);                                                  \
        GlossHookAddrByName(HOOK_LIB_NAME, OFFSET(off32, off64),          \
                             (void*)hook_##name, (void**)&orig_##name,    \
                             false, I_ARM64, NULL);                        \
    }                                                                     \
    static ret hook_##name args
// ---- global dlsym registry (auto-resolve, same idea as HOOK_SYMBOL's constructor) ----
typedef void (*dl_resolver_t)(void);

#define DL_RESOLVER_CAP 512
extern dl_resolver_t g_dl_resolvers[DL_RESOLVER_CAP];
extern int g_dl_resolver_count;

void dl_register_resolver(dl_resolver_t fn);
// call this once, from init_hooks(), after libswordigo.so is confirmed loaded
void dl_resolve_all(void);

// symbols - single translation unit version.
// Stays static (only visible in the .c file it's used in) but no longer needs
// a manual initC_xxx() call: the constructor registers it into g_dl_resolvers,
// and dl_resolve_all() (called from init_hooks()) resolves everything for you.
#define DL_SYMBOL(name, symbol_str, ret, args)                            \
    typedef ret (*name##_t) args;                                         \
    static name##_t name = NULL;                                          \
    static void resolve_##name(void) {                                    \
        if (name) return;                                                 \
        void* h = dlopen(HOOK_LIB_NAME, RTLD_NOW | RTLD_NOLOAD);          \
        if (h) name = (name##_t)dlsym(h, symbol_str);                     \
    }                                                                     \
    __attribute__((constructor))                                          \
    static void register_##name(void) {                                   \
        dl_register_resolver(resolve_##name);                             \
    }                                                                     \
    struct __dl_symbol_semicolon_##name { int _unused; }

// symbols - cross-file version. Use DL_SYMBOL_DECL in the header (e.g. scene.h)
// so other .c files can see and call the resolved function pointer, and
// G_DL_SYMBOL in exactly ONE .c file (e.g. scene.c) to actually define it.
// Still fully auto-resolving, no manual initC_xxx() calls needed anywhere.
#define DL_SYMBOL_DECL(name, ret, args) \
    typedef ret (*name##_t) args;       \
    extern name##_t name;               \
    void resolve_##name(void)

#define G_DL_SYMBOL(name, symbol_str, ret, args)                          \
    typedef ret (*name##_t) args;                                         \
    name##_t name = NULL;                                                 \
    void resolve_##name(void) {                                           \
        if (name) return;                                                 \
        void* h = dlopen(HOOK_LIB_NAME, RTLD_NOW | RTLD_NOLOAD);          \
        if (h) name = (name##_t)dlsym(h, symbol_str);                     \
    }                                                                     \
    __attribute__((constructor))                                          \
    static void register_##name(void) {                                   \
        dl_register_resolver(resolve_##name);                             \
    }

/* lazy resolve BRUH */
uintptr_t get_lib_bias(void);
uintptr_t get_lib_bss(size_t* size);
uintptr_t get_lib_data(size_t* size);
uintptr_t get_lib_text(size_t* size);
void *swordigo_dlsym(const char *symbol);
void init_hooks(void);
#endif //LAWNCHER_HOOK_H