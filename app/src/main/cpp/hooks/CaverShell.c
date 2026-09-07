#include "CaverShell.h"

static CaverShell *g_shell = NULL;

CaverShell *caverShell_get() {
	return g_shell;
}

HOOK_SYMBOL(
	Update,
	"_ZN5Caver10CaverShell6UpdateEf",
	void, (CaverShell *shell, float dt)
) {
	g_shell = shell;
	return orig_Update(shell, dt);
}

G_DL_SYMBOL(
	CaverShell_SuspendApplication,
	"_ZN5Caver10CaverShell18SuspendApplicationEb",
	void, (CaverShell *shell, bool unknown)
);

G_DL_SYMBOL(
	CaverShell_ResumeApplication,
	"_ZN5Caver10CaverShell17ResumeApplicationEv",
	void, (CaverShell *shell)
);

G_DL_SYMBOL(
	CaverShell_QuitApplication,
	"_ZN5Caver10CaverShell15QuitApplicationEv",
	void, (CaverShell *shell)
);

G_DL_SYMBOL(
	CaverShell_InitApplication,
	"_ZN5Caver10CaverShell15InitApplicationEv",
	void, (CaverShell *shell)
);
