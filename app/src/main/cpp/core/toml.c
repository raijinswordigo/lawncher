#include "toml.h"
#include "log.h"
#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define LOG_TAG "Toml"

static char *read_file(const char *path, size_t *out_len)
{
	FILE *f = fopen(path, "rb");
	if (!f) return NULL;

	if (fseek(f, 0, SEEK_END) != 0) { fclose(f); return NULL; }
	long sz = ftell(f);
	if (sz < 0) { fclose(f); return NULL; }
	rewind(f);

	char *buf = malloc((size_t)sz + 1);
	if (!buf) { fclose(f); return NULL; }

	size_t n = fread(buf, 1, (size_t)sz, f);
	fclose(f);
	buf[n] = '\0';
	if (out_len) *out_len = n;
	return buf;
}

static const char *skip_ws(const char *p)
{
	while (*p == ' ' || *p == '\t') p++;
	return p;
}

static const char *skip_line(const char *p)
{
	while (*p && *p != '\n') p++;
	if (*p == '\n') p++;
	return p;
}

static int decode_escape(const char **pp, char *out, size_t *used, size_t cap)
{
	const char *p = *pp;
	if (*p != '\\') return -1;
	p++;

	char c;
	switch (*p) {
		case 'n':  c = '\n'; break;
		case 'r':  c = '\r'; break;
		case 't':  c = '\t'; break;
		case '\\': c = '\\'; break;
		case '"':  c = '"';  break;
		case 'b':  c = '\b'; break;
		case 'f':  c = '\f'; break;
		case 'u': {
			unsigned code = 0;
			for (int i = 0; i < 4; i++) {
				p++;
				char h = *p;
				if (!isxdigit((unsigned char)h)) return -1;
				code <<= 4;
				if (h >= '0' && h <= '9') code |= (unsigned)(h - '0');
				else if (h >= 'a' && h <= 'f') code |= (unsigned)(h - 'a' + 10);
				else code |= (unsigned)(h - 'A' + 10);
			}
			if (code < 0x80) {
				if (*used + 1 >= cap) return -1;
				out[(*used)++] = (char)code;
			} else if (code < 0x800) {
				if (*used + 2 >= cap) return -1;
				out[(*used)++] = (char)(0xC0 | (code >> 6));
				out[(*used)++] = (char)(0x80 | (code & 0x3F));
			} else {
				if (*used + 3 >= cap) return -1;
				out[(*used)++] = (char)(0xE0 | (code >> 12));
				out[(*used)++] = (char)(0x80 | ((code >> 6) & 0x3F));
				out[(*used)++] = (char)(0x80 | (code & 0x3F));
			}
			*pp = p + 1;
			return 0;
		}
		default:
			return -1;
	}
	if (*used + 1 >= cap) return -1;
	out[(*used)++] = c;
	*pp = p + 1;
	return 0;
}

static char *parse_string(const char *p, const char **end)
{
	int multiline = 0;
	if (p[0] == '"' && p[1] == '"' && p[2] == '"') {
		multiline = 1;
		p += 3;
		if (*p == '\n') p++;
		else if (p[0] == '\r' && p[1] == '\n') p += 2;
	} else if (*p == '"') {
		p++;
	} else {
		return NULL;
	}

	size_t cap = 128;
	size_t used = 0;
	char *buf = malloc(cap);
	if (!buf) return NULL;

	while (*p) {
		if (multiline) {
			if (p[0] == '"' && p[1] == '"' && p[2] == '"') {
				p += 3;
				*end = p;
				buf[used] = '\0';
				return buf;
			}
		} else {
			if (*p == '"') {
				p++;
				*end = p;
				buf[used] = '\0';
				return buf;
			}
			if (*p == '\n' || *p == '\r') {
				free(buf);
				return NULL;
			}
		}

		if (*p == '\\') {
			if (used + 8 >= cap) {
				cap *= 2;
				char *nb = realloc(buf, cap);
				if (!nb) { free(buf); return NULL; }
				buf = nb;
			}
			if (decode_escape(&p, buf, &used, cap) != 0) {
				free(buf);
				return NULL;
			}
			continue;
		}

		if (used + 1 >= cap) {
			cap *= 2;
			char *nb = realloc(buf, cap);
			if (!nb) { free(buf); return NULL; }
			buf = nb;
		}
		buf[used++] = *p++;
	}

	free(buf);
	return NULL;
}

static char *parse_bare_key(const char *p, const char **end)
{
	const char *start = p;
	while (isalnum((unsigned char)*p) || *p == '_' || *p == '-')
		p++;
	if (p == start) return NULL;

	size_t len = (size_t)(p - start);
	char *key = malloc(len + 1);
	if (!key) return NULL;
	memcpy(key, start, len);
	key[len] = '\0';
	*end = p;
	return key;
}

int toml_load_string_map(const char *path, Map *out)
{
	if (!path || !out) return -1;

	size_t len = 0;
	char *data = read_file(path, &len);
	if (!data) {
		LOGD("toml: cannot open %s", path);
		return -1;
	}

	int count = 0;
	const char *p = data;

	while (*p) {
		p = skip_ws(p);

		if (*p == '\0') break;
		if (*p == '#') { p = skip_line(p); continue; }
		if (*p == '\n') { p++; continue; }
		if (*p == '\r') { p++; if (*p == '\n') p++; continue; }

		char *key = NULL;
		const char *after = NULL;
		if (*p == '"')
			key = parse_string(p, &after);
		else
			key = parse_bare_key(p, &after);

		if (!key) {
			LOGW("toml: bad key near offset %zu, skipping line",
			     (size_t)(p - data));
			p = skip_line(p);
			continue;
		}
		p = skip_ws(after);

		if (*p != '=') {
			LOGW("toml: expected '=' after key '%s'", key);
			free(key);
			p = skip_line(p);
			continue;
		}
		p = skip_ws(p + 1);

		if (*p != '"') {
			LOGW("toml: expected string value for key '%s'", key);
			free(key);
			p = skip_line(p);
			continue;
		}

		char *val = parse_string(p, &after);
		if (!val) {
			LOGW("toml: bad string value for key '%s'", key);
			free(key);
			p = skip_line(p);
			continue;
		}
		p = after;

		Map_SetOwned(out, key, val);
		count++;
		free(key);
		free(val);

		p = skip_ws(p);
		if (*p == '#') p = skip_line(p);
		else if (*p == '\r') { p++; if (*p == '\n') p++; }
		else if (*p == '\n') p++;
		else if (*p != '\0') p = skip_line(p);
	}

	free(data);
	LOGI("toml: loaded %d pair(s) from %s", count, path);
	return count;
}
