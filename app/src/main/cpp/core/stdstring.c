#include "stdstring.h"
#include "log.h"
#include <string.h>

#define LOG_TAG "MiniCPPStrings"

/* ------------------------------------------------------------------ */
/*  Function pointers & sentinel                                      */
/* ------------------------------------------------------------------ */
void (*String_create)(String **out, const char *data)                = NULL;
void (*String_assign)(String **self, const char *data)               = NULL;
void (*String_append_impl)(String **self, const char *data, size_t len) = NULL;
void (*String_unsafe_release)(StringHeader *header)                  = NULL;

#if defined(__arm__)
void (*String_safe_release)(String **self)                           = NULL;
#endif

StringHeader *String_s_empty = NULL;

/* ------------------------------------------------------------------ */
/*  Atomic refcount decrement (identical to the engine’s sequence)    */
/* ------------------------------------------------------------------ */
static void atomic_dec_and_maybe_free(StringHeader *base)
{
	if (base == String_s_empty)
		return;

	uint32_t old, neu, status;
	asm volatile (
#if defined(__aarch64__)
		"1:\n"
		"   ldaxr   %w0, [%3]\n"
		"   sub     %w1, %w0, #1\n"
		"   stlxr   %w2, %w1, [%3]\n"
		"   cbnz    %w2, 1b\n"
#elif defined(__arm__)
		"   dmb     sy\n"
        "1:\n"
        "   ldrex   %0, [%3]\n"
        "   subs    %1, %0, #1\n"
        "   strex   %2, %1, [%3]\n"
        "   cmp     %2, #0\n"
        "   bne     1b\n"
        "   dmb     sy\n"
#endif
		: "=&r"(old), "=&r"(neu), "=&r"(status)
		: "r"(&base->uc)
		: "memory", "cc"
		);

	if (old <= 1)           /* was the last reference */
		free(base);
}

/* ------------------------------------------------------------------ */
/*  High-level API                                                    */
/* ------------------------------------------------------------------ */
String *string_new(const char *cstr)
{
	if (!String_create) {
		LOGE("string_new: String_create not resolved");
		return NULL;
	}
	String *out = NULL;
	String_create(&out, cstr ? cstr : "");
	return out;
}

void string_assign(String **self, const char *cstr)
{
	if (!self || !String_assign) return;
	String_assign(self, cstr ? cstr : "");
}

void string_append(String **self, const char *cstr)
{
	if (!self || !*self || !cstr || !String_append_impl) return;
	String_append_impl(self, cstr, strlen(cstr));
}

void string_release(String *s)
{
	if (!s) return;
	StringHeader *header = (StringHeader *)((uintptr_t)s - sizeof(StringHeader));
	atomic_dec_and_maybe_free(header);
}

/* ------------------------------------------------------------------ */
/*  Init                                                              */
/* ------------------------------------------------------------------ */
void init_stdstring(void)
{
	LOGI("Initializing COW std::string helpers…");

	uintptr_t bias = get_lib_bias();
	if (!bias) {
		LOGE("init_stdstring: library bias is zero – is libswordigo.so loaded?");
		return;
	}

	/* .bss sentinel */
	size_t bss_sz = 0;
	uintptr_t bss = get_lib_bss(&bss_sz);
	if (bss) {
		String_s_empty = (StringHeader *)(bss + BOFF_STRING_EMPTY_SENTINEL);
		LOGI("Empty-string sentinel @ %p (len=%zu, cap=%zu, uc=%d)",
		     (void *)String_s_empty,
		     String_s_empty->length,
		     String_s_empty->capacity,
		     String_s_empty->uc);
	} else {
		LOGE("Failed to locate .bss – empty sentinel unavailable");
	}

	/* Function pointers (bias + offset) */
	String_create        = (void (*)(String **, const char *))
		(bias + EOFF_STRING_FROM_CHAR_P);
	String_assign        = (void (*)(String **, const char *))
		(bias + EOFF_STRING_ASSIGN);
	String_append_impl   = (void (*)(String **, const char *, size_t))
		(bias + EOFF_STRING_APPEND_IMPL);
	String_unsafe_release = (void (*)(StringHeader *))
		(bias + EOFF_STRING_UNSAFE_RELEASE);

#if defined(__arm__)
	String_safe_release  = (void (*)(String **))
                           (bias + EOFF_STRING_SAFE_RELEASE);
#endif

	LOGI("String_create         = %p", (void *)String_create);
	LOGI("String_assign         = %p", (void *)String_assign);
	LOGI("String_append_impl    = %p", (void *)String_append_impl);
	LOGI("String_unsafe_release = %p", (void *)String_unsafe_release);
}