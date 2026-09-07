#include "RenderingContext.h"

G_DL_SYMBOL(
	RenderingContext_CurrentContext,
	"_ZN5Caver16RenderingContext14CurrentContextEv",
	RenderingContext*, (void)
);

G_DL_SYMBOL(
	RenderingContext_SetCurrentContext,
	"_ZN5Caver16RenderingContext17SetCurrentContextEPS0_",
	void, (RenderingContext *ctx)
);

G_DL_SYMBOL(
	RenderingContext_Clear,
	"_ZN5Caver16RenderingContext5ClearEbbb",
	void, (RenderingContext *ctx, bool color, bool depth, bool stencil)
);

G_DL_SYMBOL(
	RenderingContext_SetColor,
	"_ZN5Caver16RenderingContext8SetColorERKNS_5ColorE",
	void, (RenderingContext *ctx, void *color)
);

G_DL_SYMBOL(
	RenderingContext_SetAlpha,
	"_ZN5Caver16RenderingContext8SetAlphaEf",
	void, (RenderingContext *ctx, float a)
);

G_DL_SYMBOL(
	RenderingContext_SetMatrix,
	"_ZN5Caver16RenderingContext9SetMatrixERKNS_7Matrix4E",
	void, (RenderingContext *ctx, Matrix4 *mat)
);

G_DL_SYMBOL(
	RenderingContext_SetViewport,
	"_ZN5Caver16RenderingContext11SetViewportERKNS_9RectangleE",
	void, (RenderingContext *ctx, Rectangle *rect)
);

G_DL_SYMBOL(
	RenderingContext_BindTexture,
	"_ZN5Caver16RenderingContext11BindTextureEPNS_7TextureE",
	void, (RenderingContext *ctx, void *tex)
);

G_DL_SYMBOL(
	RenderingContext_SetBlendingEnabled,
	"_ZN5Caver16RenderingContext18SetBlendingEnabledEb",
	void, (RenderingContext *ctx, bool enabled)
);

G_DL_SYMBOL(
	RenderingContext_PrepareForDrawing,
	"_ZN5Caver16RenderingContext17PrepareForDrawingEv",
	void, (RenderingContext *ctx)
);

G_DL_SYMBOL(
	RenderingContext_UseProgram,
	"_ZN5Caver16RenderingContext10UseProgramEj",
	void, (RenderingContext *ctx, unsigned int prog)
);

G_DL_SYMBOL(
	RenderingContext_DrawTexture,
	"_ZN5Caver16RenderingContext11DrawTextureERKN5boost13intrusive_ptrINS_7TextureEEERKNS_9RectangleES9_f",
	void, (RenderingContext *ctx, void *intrusive_tex, Rectangle *src, Rectangle *dst, float rot)
);
