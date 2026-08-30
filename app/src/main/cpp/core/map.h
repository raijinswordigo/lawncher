#ifndef LAWNCHER_MAP_H
#define LAWNCHER_MAP_H

#include <stddef.h>

typedef struct Map Map;

// type: 1 = pointer identity, 2 = strcmp on char* keys
Map *Map_Create(int type);

void  Map_Set(Map *m, void *key, void *value);
void *Map_Get(Map *m, void *key);
void  Map_Remove(Map *m, void *key);

// type 2 only – strdup key/value, frees previous value on replace
void  Map_SetOwned(Map *m, const char *key, const char *value);
void  Map_RemoveOwned(Map *m, const char *key);
void  Map_ClearOwned(Map *m);
void  Map_DestroyOwned(Map *m);

void  Map_Clear(Map *m);
void  Map_Destroy(Map *m);

size_t Map_Count(const Map *m);

#endif
