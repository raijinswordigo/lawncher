#include "Camera.h"
#include "CameraController.h"

Camera *camera_from_L(lua_State *L) {
	CameraController *cc = cameraController_from_L(L);
	if (!cc) return NULL;
	return cc->camera;
}

G_DL_SYMBOL(
	Camera_SetAspectRatio,
	"_ZN5Caver6Camera14SetAspectRatioEf",
	void, (Camera *cam, float aspect)
)

G_DL_SYMBOL(
	Camera_SetOrthoProjection,
	"_ZN5Caver6Camera18SetOrthoProjectionEffff",
	void, (Camera *cam, float left, float right, float bottom, float top)
);

G_DL_SYMBOL(
	Camera_SetPerspectiveProjection,
	"_ZN5Caver6Camera24SetPerspectiveProjectionEffff",
	void, (Camera *cam, float fovy, float aspect, float near, float far)
)

G_DL_SYMBOL(
	Camera_EvaluateViewMatrix,
	"_ZN5Caver6Camera18EvaluateViewMatrixEv",
	void, (Camera *cam)
)

G_DL_SYMBOL(
	Camera_SetProjectionMatrix,
	"_ZN5Caver6Camera19SetProjectionMatrixERKNS_7Matrix4E",
	void, (Camera *cam, Matrix4 *mat)
)