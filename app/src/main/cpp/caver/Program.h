#ifndef LAWNCHER_PROGRAM_H
#define LAWNCHER_PROGRAM_H

#include "hook.h"
#include "stdstring.h"

typedef struct Program {
	String source;
	void* string;
	void* string_count;
	void* bytecode;
	void* bytecode_count;
} Program; // sizeof(0x1c, 0x38)

DL_SYMBOL_DECL(Program_InitWithString, bool, (Program *this, String *src, String *err));
DL_SYMBOL_DECL(Program_LoadFromProtobufMessage, void, (Program *this, void *msg));
DL_SYMBOL_DECL(Program_SaveToProtobufMessage, void, (Program *this, void *msg));

#endif //LAWNCHER_PROGRAM_H
