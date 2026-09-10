#ifndef LAWNCHER_BINDINGVALUE_H
#define LAWNCHER_BINDINGVALUE_H

#include "hook.h"
#include "stdstring.h"
#include "types.h"

typedef struct BindingValue {
	int type;
	char _pad0[archSplit(0x0, 0x4)];
	void* value;
	void* value_count;
	String* description;
	void* description_count;
} BindingValue; // sizeof(0x14, 0x28)

typedef struct Binding {
	String name;
	BindingValue value;
} Binding; // sizeof(0x2c, 0x40)

DL_SYMBOL_DECL(BindingValue_ValueWithString, void, (BindingValue *this, String *s));
DL_SYMBOL_DECL(BindingValue_ValueWithBool, void, (BindingValue *this, bool v));
DL_SYMBOL_DECL(BindingValue_ValueWithFloat, void, (BindingValue *this, float v));
DL_SYMBOL_DECL(BindingValue_ValueWithInt, void, (BindingValue *this, int v));
DL_SYMBOL_DECL(BindingValue_ValueWithUInt, void, (BindingValue *this, int v));
DL_SYMBOL_DECL(BindingValue_ValueWithProgram, void, (BindingValue *this, void *program));
DL_SYMBOL_DECL(BindingValue_ValueWithFloatColor, void, (BindingValue *this, FloatColor *c));
DL_SYMBOL_DECL(BindingValue_ValueWithVector2, void, (BindingValue *this, Vector2 *v));
DL_SYMBOL_DECL(BindingValue_ValueWithVector3, void, (BindingValue *this, Vector3 *v));
DL_SYMBOL_DECL(BindingValue_ValueWithRectangle, void, (BindingValue *this, Rectangle *r));
DL_SYMBOL_DECL(BindingValue_ParseFromString, void, (BindingValue *this, String *s, int type));
DL_SYMBOL_DECL(BindingValue_ConvertToString, void, (BindingValue *this, String *out));

#endif //LAWNCHER_BINDINGVALUE_H
