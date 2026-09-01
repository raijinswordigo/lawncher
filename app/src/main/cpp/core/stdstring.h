#ifndef LAWNCHER_STDSTRING_H
#define LAWNCHER_STDSTRING_H

#include <stddef.h>

#define STRING_SHORT_CAP 23

typedef struct {
	size_t cap;
	size_t size;
	char *data;
} String_Long;

typedef struct {
	unsigned char size;
	char data[STRING_SHORT_CAP];
} String_Short;

typedef struct String {
	union {
		String_Long l;
		String_Short s;
	}; // sonion
} String;

static inline int String_isLong(const String *s) {
	return s->s.size & 1u;
}

static inline size_t String_size(const String *s) {
	return String_isLong(s) ? s->l.size : (s->s.size >> 1);
}

static inline const char *String_get(const String *s) { // basically s->cstr
	return String_isLong(s) ? s->l.data : s->s.data;
}

void String_create(String *out, const char *s);
void String_destroy(String *s);

#endif //LAWNCHER_STDSTRING_H
