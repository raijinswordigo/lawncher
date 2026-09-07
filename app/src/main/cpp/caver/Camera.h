#ifndef LAWNCHER_CAMERA_H
#define LAWNCHER_CAMERA_H

#include "hook.h"
#include "types.h"
#include "lua.h"

typedef struct Camera {
	char _pad0[0x10];
	Vector3 position;
	Quaternion rotation;
	Matrix4 view;
	Matrix4 projection;
	Matrix4 viewProjection;
	bool isOrtho;
	char _pad1[0x03];
	float aspect;
	float fov;
	float nearPlane;
	float farPlane;
	Matrix4 inverseProjection;
	Matrix4 inverseView;
	Matrix4 inverseViewProjection;
} Camera;

Camera *camera_from_L(lua_State *L);

DL_SYMBOL_DECL(Camera_SetAspectRatio, void, (Camera *cam, float aspect));
DL_SYMBOL_DECL(Camera_SetOrthoProjection, void, (Camera *cam, float left, float right, float bottom, float top));
DL_SYMBOL_DECL(Camera_SetPerspectiveProjection, void, (Camera *cam, float fovy, float aspect, float near, float far));
DL_SYMBOL_DECL(Camera_EvaluateViewMatrix, void, (Camera *cam));
DL_SYMBOL_DECL(Camera_SetProjectionMatrix, void, (Camera *cam, Matrix4 *mat));

#endif