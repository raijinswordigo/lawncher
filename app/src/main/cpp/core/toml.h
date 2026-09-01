#ifndef LAWNCHER_TOML_H
#define LAWNCHER_TOMLjpatch_H

#include "map.h"

// kv pairs into a strmap
// Returns pair count, or -1 on I/O error.
int toml_load_string_map(const char *path, Map *out);

#endif
