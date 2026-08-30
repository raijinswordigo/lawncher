#include "hook.h"

extern void clear_bone_map(void);
extern void button_remove_all(void);

HOOK_SYMBOL(
	Scene_Destructor,
	"_ZN5Caver5SceneD0Ev",
	void, (void *this)
) {
	orig_Scene_Destructor(this);
	button_remove_all();
	clear_bone_map();
}
