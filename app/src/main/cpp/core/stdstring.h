#ifndef LAWNCHER_STDSTRING_H
#define LAWNCHER_STDSTRING_H

#include "hook.h"          // archSplit, OFFSET, get_lib_bias, get_lib_bss, …
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ------------------------------------------------------------------ */
/*  Layout of the old GCC COW std::string (_Rep_base + data)           */
/* ------------------------------------------------------------------ */

typedef struct StringHeader {
	size_t length;
	size_t capacity;
	int    uc;          /* reference / use count */
	/* implicit padding on 64-bit so that data[] is 8-byte aligned */
	char   data[];      /* flexible array – the actual characters */
} StringHeader;

/* Public type – just a pointer to the character data.
   (sizeof(std::string) == sizeof(void*) under this ABI) */
typedef char String;

/* ------------------------------------------------------------------ */
/*  Offsets (mapped with our macros)                                  */
/* ------------------------------------------------------------------ */

#define EOFF_STRING_FROM_CHAR_P     OFFSET(0x37bc60, 0x566bb8)
#define EOFF_STRING_APPEND_IMPL     OFFSET(0x379988, 0x567254)
#define EOFF_STRING_ASSIGN          OFFSET(0x37aa1c, 0x56918c)
#define EOFF_STRING_UNSAFE_RELEASE  OFFSET(0x3787c8, 0x565220)

#if defined(__arm__)
#define EOFF_STRING_SAFE_RELEASE    OFFSET(0x379768, 0)   /* 32-bit only */
#endif

#define BOFF_STRING_EMPTY_SENTINEL  OFFSET(0x6a04, 0x14880)

/* ------------------------------------------------------------------ */
/*  Engine function pointers (resolved in init_stdstring)             */
/* ------------------------------------------------------------------ */

/* Creates a new COW string from a C string.  *out receives the data pointer. */
extern void (*String_create)(String **out, const char *data);

/* Assigns a C string into an existing String (handles refcount, reallocation …) */
extern void (*String_assign)(String **self, const char *data);

/* Appends a C string (the “impl” that is called after length checks) */
extern void (*String_append_impl)(String **self, const char *data, size_t len);

/* Releases without the empty-sentinel check (rarely needed) */
extern void (*String_unsafe_release)(StringHeader *header);

#if defined(__arm__)
/* 32-bit only: safe release that checks the empty sentinel first */
extern void (*String_safe_release)(String **self);
#endif

/* The static empty-string sentinel that lives in .bss */
extern StringHeader *String_s_empty;

/* ------------------------------------------------------------------ */
/*  High-level helpers you actually call from hooks / Lua / …         */
/* ------------------------------------------------------------------ */

/* Create a fresh string (returns data pointer, never NULL on success) */
String *string_new(const char *cstr);

/* Assign a C string into an existing string object */
void string_assign(String **self, const char *cstr);

/* Append a C string */
void string_append(String **self, const char *cstr);

/* Release (decrements refcount, frees when it hits zero).
   Safe to call on the empty sentinel. */
void string_release(String *s);

/* Convenience accessors – never crash on NULL */
static inline size_t string_length(const String *s) {
	if (!s) return 0;
	return ((StringHeader *)((uintptr_t)s - sizeof(StringHeader)))->length;
}
static inline size_t string_capacity(const String *s) {
	if (!s) return 0;
	return ((StringHeader *)((uintptr_t)s - sizeof(StringHeader)))->capacity;
}
static inline int string_refcount(const String *s) {
	if (!s) return 0;
	return ((StringHeader *)((uintptr_t)s - sizeof(StringHeader)))->uc;
}

/* True when the string is the shared empty sentinel */
static inline int string_is_empty_sentinel(const String *s) {
	return s && ((StringHeader *)((uintptr_t)s - sizeof(StringHeader))) == String_s_empty;
}

/* ------------------------------------------------------------------ */
/*  Init – call once after the engine library is loaded               */
/* ------------------------------------------------------------------ */
void init_stdstring(void);

#ifdef __cplusplus
}
#endif

#endif /* LAWNCHER_STDSTRING_H */