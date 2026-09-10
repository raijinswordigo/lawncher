#include "BindingValue.h"

G_DL_SYMBOL(
	BindingValue_ValueWithString,
	"_ZN5Caver12BindingValue15ValueWithStringERKNSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEE",
	void, (BindingValue *this, String *s)
)

G_DL_SYMBOL(
	BindingValue_ValueWithBool,
	"_ZN5Caver12BindingValue13ValueWithBoolEb",
	void, (BindingValue *this, bool v)
)

G_DL_SYMBOL(
	BindingValue_ValueWithFloat,
	"_ZN5Caver12BindingValue14ValueWithFloatEf",
	void, (BindingValue *this, float v)
)

G_DL_SYMBOL(
	BindingValue_ValueWithInt,
	"_ZN5Caver12BindingValue12ValueWithIntEi",
	void, (BindingValue *this, int v)
)

G_DL_SYMBOL(
	BindingValue_ValueWithUInt,
	"_ZN5Caver12BindingValue13ValueWithUIntEi",
	void, (BindingValue *this, int v)
)

G_DL_SYMBOL(
	BindingValue_ValueWithProgram,
	"_ZN5Caver12BindingValue16ValueWithProgramERKNS_7ProgramE",
	void, (BindingValue *this, void *program)
)

G_DL_SYMBOL(
	BindingValue_ValueWithFloatColor,
	"_ZN5Caver12BindingValue19ValueWithFloatColorERKNS_10FloatColorE",
	void, (BindingValue *this, FloatColor *c)
)

G_DL_SYMBOL(
	BindingValue_ValueWithVector2,
	"_ZN5Caver12BindingValue16ValueWithVector2ERKNS_7Vector2E",
	void, (BindingValue *this, Vector2 *v)
)

G_DL_SYMBOL(
	BindingValue_ValueWithVector3,
	"_ZN5Caver12BindingValue16ValueWithVector3ERKNS_7Vector3E",
	void, (BindingValue *this, Vector3 *v)
)

G_DL_SYMBOL(
	BindingValue_ValueWithRectangle,
	"_ZN5Caver12BindingValue18ValueWithRectangleERKNS_9RectangleE",
	void, (BindingValue *this, Rectangle *r)
)

G_DL_SYMBOL(
	BindingValue_ParseFromString,
	"_ZN5Caver12BindingValue15ParseFromStringERKNSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEENS_16BindingValueTypeE",
	void, (BindingValue *this, String *s, int type)
)

G_DL_SYMBOL(
	BindingValue_ConvertToString,
	"_ZNK5Caver12BindingValue15ConvertToStringEv",
	void, (BindingValue *this, String *out)
)