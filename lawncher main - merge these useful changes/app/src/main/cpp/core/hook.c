#include "hook.h"
#include "log.h" // LOGI/LOGE
#include "../libs/Gloss.h"

#define LOG_TAG "SwordigoHooks"

static uintptr_t g_lib_bias = 0;
static void *g_engine_handle = NULL;

static void ensure_bias(void) {
	if (g_lib_bias) return;
	g_lib_bias = GlossGetLibBias(HOOK_LIB_NAME);
	if (g_lib_bias) {
		LOGI("%s bias = %p", HOOK_LIB_NAME, (void*)g_lib_bias);
	} else {
		LOGE("Failed to resolve bias for %s (not loaded yet?)", HOOK_LIB_NAME);
	}
}

void *swordigo_dlsym(const char *symbol) {
	void *addr = dlsym(g_engine_handle, symbol);
	return addr;
}

uintptr_t get_lib_bias(void) {
	ensure_bias();
	return g_lib_bias;
}

uintptr_t get_lib_bss(size_t* size) {
	return GlossGetLibSection(HOOK_LIB_NAME, ".bss", size);
}

uintptr_t get_lib_data(size_t* size) {
	return GlossGetLibSection(HOOK_LIB_NAME, ".data", size);
}

uintptr_t get_lib_text(size_t* size) {
	return GlossGetLibSection(HOOK_LIB_NAME, ".text", size);
}

void init_hooks(void) {
	GlossInit(true);
	ensure_bias();

	size_t bss_size = 0, data_size = 0, text_size = 0;
	uintptr_t bss  = get_lib_bss(&bss_size);
	uintptr_t data = get_lib_data(&data_size);
	uintptr_t text = get_lib_text(&text_size);

	g_engine_handle = dlopen(HOOK_LIB_NAME, RTLD_NOW | RTLD_NOLOAD);

	LOGI("%s .text = %p (%zu bytes)", HOOK_LIB_NAME, (void*)text, text_size);
	LOGI("%s .data = %p (%zu bytes)", HOOK_LIB_NAME, (void*)data, data_size);
	LOGI("%s .bss  = %p (%zu bytes)", HOOK_LIB_NAME, (void*)bss,  bss_size);
}