#include "TextureLibrary.h"

G_DL_SYMBOL(
	TextureLibrary_sharedLibrary,
	"_ZN5Caver14TextureLibrary13sharedLibraryEv",
	TextureLibrary*, (void)
)

G_DL_SYMBOL(
	TextureLibrary_TextureForName,
	"_ZN5Caver14TextureLibrary14TextureForNameERKNSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEEb",
	void*, (TextureLibrary *lib, String *name, bool unknown)
)

G_DL_SYMBOL(
	TextureLibrary_LoadTextureAtlasWithName,
	"_ZN5Caver14TextureLibrary24LoadTextureAtlasWithNameERKNSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEE",
	void, (TextureLibrary *lib, String *name)
)

G_DL_SYMBOL(
	TextureLibrary_Clear,
	"_ZN5Caver14TextureLibrary5ClearEv",
	void, (TextureLibrary *lib)
)

G_DL_SYMBOL(
	TextureLibrary_ReloadTextures,
	"_ZN5Caver14TextureLibrary14ReloadTexturesEv",
	void, (TextureLibrary *lib)
)

G_DL_SYMBOL(
	TextureLibrary_GetAllTextures,
	"_ZN5Caver14TextureLibrary14GetAllTexturesEPNSt6__ndk16vectorIN5boost13intrusive_ptrINS_7TextureEEENS1_9allocatorIS6_EEEE",
	void, (TextureLibrary *lib, void *vector)
)

G_DL_SYMBOL(
	TextureLibrary_TotalByteSize,
	"_ZN5Caver14TextureLibrary13TotalByteSizeEv",
	unsigned long, (TextureLibrary *lib)
)

G_DL_SYMBOL(
	TextureLibrary_PurgeTexturesIfNecessary,
	"_ZN5Caver14TextureLibrary25PurgeTexturesIfNecessaryEv",
	void, (TextureLibrary *lib)
)
