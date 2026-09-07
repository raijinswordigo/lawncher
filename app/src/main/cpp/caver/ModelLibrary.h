#ifndef LAWNCHER_MODELLIBRARY_H
#define LAWNCHER_MODELLIBRARY_H

#include "hook.h"
#include "stdstring.h"

typedef struct ModelLibrary {
	char _pad[1];
} ModelLibrary;

DL_SYMBOL_DECL(ModelLibrary_sharedLibrary, ModelLibrary*, (void));
DL_SYMBOL_DECL(ModelLibrary_ModelForName, void*, (ModelLibrary *lib, String *name));
DL_SYMBOL_DECL(ModelLibrary_Clear, void, (ModelLibrary *lib));
DL_SYMBOL_DECL(ModelLibrary_SetSharedLibrary, void, (ModelLibrary *lib));

#endif //LAWNCHER_MODELLIBRARY_H
