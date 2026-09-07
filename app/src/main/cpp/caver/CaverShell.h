#ifndef LAWNCHER_CAVERSHELL_H
#define LAWNCHER_CAVERSHELL_H

#include "hook.h"

typedef struct CaverShell {
	char _pad0[archSplit(0x04, 0x08)];
	void *argvBegin;
	void *argvEnd;
	char _pad1[archSplit(0x04, 0x08)];
	char preferences[archSplit(0x34, 0x40)];
	bool initialized;
	char _pad2[archSplit(0x0f, 0x1f)];
	void *window;
	char _pad3[archSplit(0x04, 0x08)];
	void *navigationController;
	char _pad4[archSplit(0x04, 0x08)];
	bool suspended;
} CaverShell;

CaverShell *caverShell_get();

DL_SYMBOL_DECL(CaverShell_SuspendApplication, void, (CaverShell *shell, bool unknown));
DL_SYMBOL_DECL(CaverShell_ResumeApplication, void, (CaverShell *shell));
DL_SYMBOL_DECL(CaverShell_QuitApplication, void, (CaverShell *shell));
DL_SYMBOL_DECL(CaverShell_InitApplication, void, (CaverShell *shell));

#endif //LAWNCHER_CAVERSHELL_H
