#include "Program.h"
#include "log.h"
#define LOG_TAG "CaverProgram"

G_DL_SYMBOL(
	Program_InitWithString,
	"_ZN5Caver7Program14InitWithStringERKNSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEEPS7_",
	bool, (Program *this, String *src, String *err)
)

G_DL_SYMBOL(
	Program_LoadFromProtobufMessage,
	"_ZN5Caver7Program23LoadFromProtobufMessageERKNS_5Proto7ProgramE",
	void, (Program *this, void *msg)
)

G_DL_SYMBOL(
	Program_SaveToProtobufMessage,
	"_ZNK5Caver7Program21SaveToProtobufMessageEPNS_5Proto7ProgramE",
	void, (Program *this, void *msg)
)