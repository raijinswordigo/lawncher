#include "ModelLibrary.h"

G_DL_SYMBOL(
	ModelLibrary_sharedLibrary,
	"_ZN5Caver12ModelLibrary13sharedLibraryEv",
	ModelLibrary*, (void)
);

G_DL_SYMBOL(
	ModelLibrary_ModelForName,
	"_ZN5Caver12ModelLibrary12ModelForNameERKNSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEE",
	void*, (ModelLibrary *lib, String *name)
);

G_DL_SYMBOL(
	ModelLibrary_Clear,
	"_ZN5Caver12ModelLibrary5ClearEv",
	void, (ModelLibrary *lib)
);

G_DL_SYMBOL(
	ModelLibrary_SetSharedLibrary,
	"_ZN5Caver12ModelLibrary16SetSharedLibraryEPS0_",
	void, (ModelLibrary *lib)
);
