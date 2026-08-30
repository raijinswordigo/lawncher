#ifndef LAWNCHER_TOML_H
#define LAWNCHER_TOML_H

#include "map.h"

// Load top-level key = "value" pairs into an owned string map.
// Supports basic strings, multiline """, escapes, comments.
// Returns pair count, or -1 on I/O error.
int toml_load_string_map(const char *path, Map *out);

#endif
