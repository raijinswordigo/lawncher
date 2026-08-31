#include "hook.h"
#include "log.h"
#include "main/SceneObject.h"
#include <math.h>

#define LOG_TAG "PhysicsPatch"

static float g_dt = 1.0f / 60.0f;

// useless ass patch
// TODO: fix this patch doing NOTHING

DL_SYMBOL(
	SceneObject_RegisterForWorldBoundsUpdate,
	"_ZN5Caver11SceneObject28RegisterForWorldBoundsUpdateEv",
	void, (SceneObject *so)
	);

DL_SYMBOL(
	PhysicsObjectState_AdjustGroundCollisionVector,
	"_ZN5Caver18PhysicsObjectState27AdjustGroundCollisionVectorEPNS_7Vector2EPf",
	void, (void *this, float *n, float *depth)
	);

HOOK_SYMBOL(
	CS_Update,
	"_ZN5Caver10CaverShell6UpdateEf",
	void, (void *shell, float dt)
	) {
	if (dt < 0.001f) dt = 0.001f;
	if (dt > 0.0667f) dt = 0.0667f;
	g_dt = dt;
	orig_CS_Update(shell, dt);
}

HOOK_SYMBOL(
	POS_HandleGroundCollision,
	"_ZN5Caver18PhysicsObjectState21HandleGroundCollisionERKNS_20CollisionMessageDataE",
	void, (void *this, void *msg)
	) {
	if (!*$(char, this, 0x68, 0x74))
		return;

	float nx = *$(float, msg, 0x20, 0x28);
	float ny = *$(float, msg, 0x24, 0x2c);
	float depth = *$(float, msg, 0x28, 0x30);
	float rvx = *$(float, msg, 0x10, 0x18);
	float rvy = *$(float, msg, 0x14, 0x1c);

	// nah... IDK
	if (rvx * (g_dt) * nx + rvy * (g_dt) * ny > depth + 0.01f) return;

	float n[2] = { nx, ny };
	float d = depth;
	if (PhysicsObjectState_AdjustGroundCollisionVector)
		PhysicsObjectState_AdjustGroundCollisionVector(this, n, &d);
	nx = n[0];
	ny = n[1];
	depth = d;

	SceneObject *so = *(SceneObject **)this;

	float fric_src = *$(float, this, 0x6c, 0x78);
	float fric = -fric_src;
	if (fric > -0.1f) fric = -0.1f;
	if (ny >= 0.7f) fric = -fric_src;

	float vDot = so->velocity.x * nx + so->velocity.y * ny;
	float vTan = so->velocity.y * nx - so->velocity.x * ny;
	float vN = fric * vDot;
	if (vDot >= 0.01f) vN = vDot;

	if (*$(char, this, 0x69, 0x75)) {
		void *other = *(void **)msg;
		float half = *$(float, other, 0xe0, 0x170) * 0.5f;
		so->angular_velocity = vTan / half;
	}

	so->velocity.x = vTan * -ny + vN * nx;
	so->velocity.y = vTan * nx + vN * ny;

	float npx = so->position.x + nx * depth;
	float npy = so->position.y + ny * depth;
	if (fabsf(npx - so->position.x) > 0.0001f || fabsf(npy - so->position.y) > 0.0001f) {
		so->position.x = npx;
		so->position.y = npy;
		if (!so->bounds_registered) {
			if (SceneObject_RegisterForWorldBoundsUpdate)
				SceneObject_RegisterForWorldBoundsUpdate(so);
			so->bounds_registered = 1;
		}
	}

	void *col = *$(void *, msg, 0x4, 0x8);
	*$(char, this, 0x15, 0x19) = *$(char, col, 0xf8, 0x188);

	if (*$(char, col, 0xea, 0x17a)) {
		void *ref = *$(void *, col, 0x18, 0x28);
		if (ref) (*(int *)((char *)ref + OFFSET(4, 8)))++;
		void **slot = $(void *, this, 0x18, 0x20);
		void *old = *slot;
		*slot = ref;
		if (old) {
			int *rc = (int *)((char *)old + OFFSET(4, 8));
			if (--(*rc) == 0)
				(*(void (**)(void *))(*(uintptr_t *)old + OFFSET(4, 8)))(old);
		}
	}

	float ny2 = *$(float, msg, 0x24, 0x2c);
	float *timer = $(float, this, 0x4, 0x8);
	float *gnx = $(float, this, 0xc, 0x10);
	float *gny = $(float, this, 0x10, 0x14);

	if (*timer > 0.001f || *gny < ny2) {
		*$(uint32_t, this, 0x24, 0x30) = *$(uint32_t, col, 0xf4, 0x184);
		*$(char, this, 0x14, 0x18) = (ny2 < 0.7f);
		*timer = 0.f;
		if (ny2 >= 0.7f)
			*$(float, this, 0x8, 0xc) = 0.f;
		*gnx = *$(float, msg, 0x20, 0x28);
		*gny = ny2;
		ny2 = *$(float, msg, 0x24, 0x2c);
	}

	if (ny2 > 0.f) {
		float sp2 = so->velocity.x * so->velocity.x + so->velocity.y * so->velocity.y;
		float rx = *$(float, msg, 0x18, 0x20);
		float ry = *$(float, msg, 0x1c, 0x24);
		if (sp2 < 0.001f && rx * rx + ry * ry < 0.001f) {
			*$(uint16_t, this, 0x58, 0x64) = 0x0101;
			so->velocity.x = 0.f;
			so->velocity.y = 0.f;
		}
	}
}

HOOK_SYMBOL(
	PPC_HandleObjectCollision,
	"_ZN5Caver24PhysicsPlatformComponent21HandleObjectCollisionERKNS_20CollisionMessageDataE",
	void, (void *this, void *msg)
	) {
	float ny = *$(float, msg, 0x24, 0x2c);
	if (ny >= -0.2f)
		return;

	float nx = *$(float, msg, 0x20, 0x28);
	float rvx = *$(float, msg, 0x18, 0x20);
	float rvy = *$(float, msg, 0x1c, 0x24);
	float depth = *$(float, msg, 0x28, 0x30);

	// im out 1.3s
	if (rvx * -(g_dt) * nx + rvy * -(g_dt) * ny > depth + 0.01f) return;

	float *t = $(float, this, 0x48, 0x7c);
	float *ax = $(float, this, 0x40, 0x74);
	float *ay = $(float, this, 0x44, 0x78);
	float *av = $(float, this, 0x4c, 0x80);
	int *mode = $(int, this, 0x3c, 0x70);

	if (*t > 0.0001f) {
		*av = 0.f;
		*ax = 0.f;
		*ay = 0.f;
	}
	*t = 0.f;

	float side = -ny;

	if (*mode == 1) {
		*ax += side * 0.f * 2000.f;
		*ay -= side * 2000.f;

		void *plat = *$(void *, this, 0x18, 0x28);
		void *col = *$(void *, msg, 0x4, 0x8);
		void *other = *$(void *, col, 0x18, 0x28);

		float inv = 1.f / (*$(float, this, 0x60, 0x94) * 1000.f *
		                   *$(float, plat, 0x6c, 0x9c));

		float ox = *$(float, other, 0x40, 0x70);
		float oy = *$(float, other, 0x44, 0x74);
		float px = *$(float, plat, 0x40, 0x70);
		float py = *$(float, plat, 0x44, 0x74);

		*av += inv * ((*ay) * (ox - px) - (*ax) * (oy - py));
	} else if (*mode == 0) {
		*ax += side * 0.f * 2000.f;
		*ay -= side * 2000.f;
	}
}