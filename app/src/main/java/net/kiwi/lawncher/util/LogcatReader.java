package net.kiwi.lawncher.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/** Streams / dumps logcat output for the current process. */
public final class LogcatReader {

	private LogcatReader() {}

	public interface Listener {
		void onLine(String line);
	}

	/** Starts a live logcat stream; returns the process to stop later. */
	public static Process startLive(int pid, Listener listener) {
		try {
			final Process p = new ProcessBuilder("logcat", "-v", "threadtime",
					"--pid=" + pid).redirectErrorStream(true).start();
			Thread t = new Thread(() -> {
				try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(p.getInputStream()))) {
					String line;
					while ((line = reader.readLine()) != null && isAlive(p)) {
						listener.onLine(line);
					}
				} catch (IOException ignored) {
				}
			});
			t.setDaemon(true);
			t.start();
			return p;
		} catch (IOException e) {
			return null;
		}
	}

	public static void stop(Process p) {
		if (p != null) {
			try {
				p.destroy();
			} catch (Throwable ignored) {
			}
		}
	}

	/** One-shot dump of the current process's buffer. */
	public static String dump(int pid) {
		StringBuilder sb = new StringBuilder();
		try {
			Process p = new ProcessBuilder("logcat", "-d", "-v", "threadtime",
					"--pid=" + pid).redirectErrorStream(true).start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line;
			while ((line = reader.readLine()) != null) sb.append(line).append('\n');
			reader.close();
			p.waitFor();
		} catch (Throwable ignored) {
		}
		return sb.toString();
	}

	private static boolean isAlive(Process p) {
		try {
			p.exitValue();
			return false;
		} catch (IllegalThreadStateException e) {
			return true;
		}
	}
}
