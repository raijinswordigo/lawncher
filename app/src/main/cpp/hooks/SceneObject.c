#include "SceneObject.h"

G_DL_SYMBOL(
	SceneObject_setPosition,
	"_ZN5Caver11SceneObject11setPositionERKNS_7Vector2E",
	void, (SceneObject *obj, Vector2 *pos)
)

G_DL_SYMBOL(
	SceneObject_setLocalAABB,
	"_ZN5Caver11SceneObject12setLocalAABBERKNS_9RectangleE",
	void, (SceneObject *obj, Rectangle *aabb)
)

G_DL_SYMBOL(
	SceneObject_SetIdentifier,
	"_ZN5Caver11SceneObject13SetIdentifierERKNSt6__ndk112basic_stringIcNS1_11char_traitsIcEENS1_9allocatorIcEEEE",
	void, (SceneObject *obj, String *id)
)

G_DL_SYMBOL(
	SceneObject_Activate,
	"_ZN5Caver11SceneObject8ActivateEv",
	void, (SceneObject *obj)
)

G_DL_SYMBOL(
	SceneObject_isHidden,
	"_ZNK5Caver11SceneObject8isHiddenEv",
	bool, (SceneObject *obj)
)

G_DL_SYMBOL(
	SceneObject_UpdateBounds,
	"_ZN5Caver11SceneObject12UpdateBoundsEv",
	void, (SceneObject *obj)
)

G_DL_SYMBOL(
	SceneObject_SetAlwaysActive,
	"_ZN5Caver11SceneObject15SetAlwaysActiveEb",
	void, (SceneObject *obj, bool active)
)

G_DL_SYMBOL(
	SceneObject_SetInstanceScaling,
	"_ZN5Caver11SceneObject18SetInstanceScalingEf",
	void, (SceneObject *obj, float scale)
)

G_DL_SYMBOL(
	SceneObject_AddComponent,
	"_ZN5Caver11SceneObject12AddComponentEPNS_9ComponentE",
	void, (SceneObject *obj, void *comp)
)

G_DL_SYMBOL(
	SceneObject_RemoveAllComponents,
	"_ZN5Caver11SceneObject19RemoveAllComponentsEv",
	void, (SceneObject *obj)
)

G_DL_SYMBOL(
	SceneObject_Update,
	"_ZN5Caver11SceneObject6UpdateEfbb",
	void, (SceneObject *obj, float dt, bool a, bool b)
)
