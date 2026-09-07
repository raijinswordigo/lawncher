#ifndef LAWNCHER_TEXTURELIBRARY_H
#define LAWNCHER_TEXTURELIBRARY_H

#include "hook.h"
#include "stdstring.h"

typedef struct TextureLibrary {
	void *vtable;
	void *texturesBegin;
	void *texturesEnd;
	char _pad0[archSplit(0x08, 0x10)];
	long long someCounter;
	char _pad1[archSplit(0x08, 0x10)];
	void *unusedList;
	char _pad2[archSplit(0x00, 0x08)];
} TextureLibrary;

DL_SYMBOL_DECL(TextureLibrary_sharedLibrary, TextureLibrary*, (void));
DL_SYMBOL_DECL(TextureLibrary_TextureForName, void*, (TextureLibrary *lib, String *name, bool unknown));
DL_SYMBOL_DECL(TextureLibrary_LoadTextureAtlasWithName, void, (TextureLibrary *lib, String *name));
DL_SYMBOL_DECL(TextureLibrary_Clear, void, (TextureLibrary *lib));
DL_SYMBOL_DECL(TextureLibrary_ReloadTextures, void, (TextureLibrary *lib));
DL_SYMBOL_DECL(TextureLibrary_GetAllTextures, void, (TextureLibrary *lib, void *vector));
DL_SYMBOL_DECL(TextureLibrary_TotalByteSize, unsigned long, (TextureLibrary *lib));
DL_SYMBOL_DECL(TextureLibrary_PurgeTexturesIfNecessary, void, (TextureLibrary *lib));

#endif