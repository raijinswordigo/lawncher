#include "core.h"

#include <stdio.h>
#include <string.h>
#include <signal.h>
#include <ucontext.h>
#include <dlfcn.h>
#include <unwind.h>
#include <android/log.h>

#define LOG_TAG "NativeCrashCatcher"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Helper to extract the Program Counter (PC) depending on CPU architecture
#if defined(__arm__)
#define GET_PC(ctx) ((ctx)->uc_mcontext.arm_pc)
#elif defined(__aarch64__)
#define GET_PC(ctx) ((ctx)->uc_mcontext.pc)
#elif defined(__i386__)
#define GET_PC(ctx) ((ctx)->uc_mcontext.gregs[REG_EIP])
#elif defined(__x86_64__)
    #define GET_PC(ctx) ((ctx)->uc_mcontext.gregs[REG_RIP])
#else
    #define GET_PC(ctx) 0
#endif

// To store the old signal handlers so we don't break Android's libsigchain/ART
static struct sigaction g_old_sa[NSIG];

// State struct for the unwinder
struct BacktraceState {
	int current_depth;
};

// Small utility to get just the filename from a full path
static const char *get_basename(const char *path) {
	if (!path) return "unknown_lib";
	const char *slash = strrchr(path, '/');
	return slash ? slash + 1 : path;
}

// Callback to walk the stack trace frame by frame
static _Unwind_Reason_Code unwind_callback(struct _Unwind_Context* context, void* arg) {
	struct BacktraceState* state = (struct BacktraceState*)arg;
	uintptr_t pc = _Unwind_GetIP(context);

	if (pc) {
		Dl_info info;
		// dladdr looks up the library name, base address, and symbol name from a raw memory address
		if (dladdr((void*)pc, &info) != 0) {
			const char *lib_name = get_basename(info.dli_fname);
			const char *sym_name = info.dli_sname ? info.dli_sname : "<stripped_symbol>";
			uintptr_t offset = pc - (uintptr_t)info.dli_fbase;

			LOGE("    #%02d pc %08zx  %s (%s + 0x%zx)",
			     state->current_depth, offset, lib_name, sym_name,
			     (info.dli_sname ? (pc - (uintptr_t)info.dli_saddr) : offset));
		} else {
			LOGE("    #%02d pc %08zx  <unknown>", state->current_depth, pc);
		}
	}
	state->current_depth++;
	return _URC_NO_REASON;
}

// The actual function that fires when the game crashes (SIGSEGV, etc)
static void native_crash_handler(int sig, siginfo_t *info, void *context) {
	ucontext_t *uc = (ucontext_t *)context;
	uintptr_t pc = GET_PC(uc);

	LOGE("==========================================================");
	LOGE("💥 FATAL NATIVE CRASH DETECTED 💥");
	LOGE("Signal: %d (%s) at address %p", sig, strsignal(sig), info->si_addr);

	Dl_info dl_info;
	if (pc != 0 && dladdr((void*)pc, &dl_info) != 0) {
		const char *lib_name = get_basename(dl_info.dli_fname);
		const char *sym_name = dl_info.dli_sname ? dl_info.dli_sname : "<stripped>";
		uintptr_t offset = pc - (uintptr_t)dl_info.dli_fbase;
		LOGE("Faulting Instruction: pc %08zx | %s | %s", offset, lib_name, sym_name);
	}

	LOGE("--- Stack Trace ---");
	struct BacktraceState state = {0};
	_Unwind_Backtrace(unwind_callback, &state);
	LOGE("==========================================================");

	// Chain the crash to the original handler (letting libsigchain or debuggerd finish the job)
	if (g_old_sa[sig].sa_flags & SA_SIGINFO) {
		if (g_old_sa[sig].sa_sigaction != NULL) {
			g_old_sa[sig].sa_sigaction(sig, info, context);
		}
	} else if (g_old_sa[sig].sa_handler != SIG_DFL && g_old_sa[sig].sa_handler != SIG_IGN) {
		g_old_sa[sig].sa_handler(sig);
	}
}

void init_crasher(void) {
	struct sigaction sa;
	memset(&sa, 0, sizeof(sa));
	sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
	sa.sa_sigaction = native_crash_handler;

	// The typical fatal signals we want to catch
	int signals[] = { SIGSEGV, SIGABRT, SIGILL, SIGFPE, SIGBUS };
	for (int i = 0; i < (sizeof(signals) / sizeof(signals[0])); i++) {
		sigaction(signals[i], &sa, &g_old_sa[signals[i]]);
	}
	LOGI("Custom native crash handler installed.");
}