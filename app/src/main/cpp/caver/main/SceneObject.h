#ifndef LAWNCHER_SCENEOBJECT_H
#define LAWNCHER_SCENEOBJECT_H

#include "types.h"
#include "stdstring.h"

typedef struct Component Component; // Needs mapping

typedef struct SceneObject {
	char _pad0[archSplit(0x10, 0x20)];
	void *scene; // Scene*
	void *groups; // FastVector<SceneObjectGroup*>*
	char _pad1[archSplit(0x8, 0x10)];
	float speed;
	char _pad1b[0x4];
	void *object_template; // ObjectTemplate*
	String *identifier;
	void *program; // boost::shared_ptr<Program>.px
	void *program_refcount; // boost::shared_ptr<Program>.pn
	Vector2 velocity;
	Vector3 position;
	char _pad_unk[0x4];
	float rotation;
	float angular_velocity;
	float instance_scale;
	float world_scale;
	char flip_x;
	char _pad3d[0x3];
	Rectangle local_aabb; // {xf, yf, wf, hf};
	char _pad4[0x10];
	char bounds_registered;
	char _pad5a[0xB];
	Component **components_begin;
	Component **components_end;
	Component **components_cap_end;
	char _pad5b[archSplit(0x1C, 0x24)];
	char alwaysActive;
} SceneObject;

DL_SYMBOL_DECL(
	SceneObject_ComponentWithInterface,
	void*, (SceneObject *obj, void *interface)
	);

#endif //LAWNCHER_SCENEOBJECT_H
