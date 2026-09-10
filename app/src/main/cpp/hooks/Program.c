#include "Program.h"

G_DL_SYMBOL(
	Program_InitWithString,
	"_ZN5Caver7Program13InitWithStringERKNSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEES8_",
	bool, (Program *this, String *src, String *err)
)

G_DL_SYMBOL(
	Program_LoadFromProtobufMessage,
	"_ZN5Caver7Program22LoadFromProtobufMessageERKN5Proto7ProgramE",
	void, (Program *this, void *msg)
)

G_DL_SYMBOL(
	Program_SaveToProtobufMessage,
	"_ZNK5Caver7Program21SaveToProtobufMessageEPN5Proto7ProgramE",
	void, (Program *this, void *msg)
)