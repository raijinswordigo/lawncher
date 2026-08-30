package com.touchfoo.swordigo;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;

import net.kiwi.lawncher.MainActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class MusicPlayer implements MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener {

	private static final String TAG = "MusicPlayer";

	private static MusicPlayer sInstance;

	private final Activity activity;
	private final MediaPlayer player;

	private String currentTrack = "";
	private String pendingTrack = "";
	private boolean isPreparing = false;
	private boolean playWhenReady = false;
	private boolean looping = false;
	private float volume = 1.0f;

	public MusicPlayer(Activity activity) {
		this.activity = activity;
		this.player = new MediaPlayer();
		this.player.setAudioAttributes(
		new AudioAttributes.Builder()
		.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
		.setUsage(AudioAttributes.USAGE_GAME)
		.build()
		);
		this.player.setOnErrorListener(this);
		this.player.setOnPreparedListener(this);
		sInstance = this;
		// native init no longer required – we own everything
	}

	/** Called from native. Creates the singleton if needed. */
	public static MusicPlayer get() {
		if (sInstance == null) {
			Activity act = MainActivity.getCurrentActivity();
			if (act != null) {
				sInstance = new MusicPlayer(act);
			}
		}
		return sInstance;
	}

	// ---------- public API (called from native) ----------

	public boolean loadFile(String fileName) {
		Log.d(TAG, "loadFile " + fileName);
		pendingTrack = fileName;
		playWhenReady = false;
		return isPreparing || prepareTrack();
	}

	public void play() {
		Log.d(TAG, "play");
		if (isPreparing) {
			playWhenReady = true;
		} else {
			try { player.start(); } catch (IllegalStateException ignored) {}
		}
	}

	public void pause() {
		Log.d(TAG, "pause");
		playWhenReady = false;
		if (!isPreparing && !currentTrack.isEmpty()) {
			try { player.pause(); } catch (IllegalStateException ignored) {}
		}
	}

	public void stop() {
		Log.d(TAG, "stop");
		playWhenReady = false;
		if (!isPreparing && !currentTrack.isEmpty()) {
			try { player.stop(); } catch (IllegalStateException ignored) {}
			currentTrack = "";
		}
	}

	public void setLooping(boolean loop) {
		looping = loop;
		applyLooping();
	}

	public void setVolume(float vol) {
		volume = vol;
		applyVolume();
	}

	// ---------- callbacks ----------

	@Override
	public void onPrepared(MediaPlayer mp) {
		Log.d(TAG, "onPrepared");
		isPreparing = false;

		if (!currentTrack.equals(pendingTrack)) {
			prepareTrack();
			return;
		}

		applyLooping();
		applyVolume();

		if (playWhenReady) {
			playWhenReady = false;
			try { player.start(); } catch (IllegalStateException ignored) {}
		}
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		Log.e(TAG, "onError " + what + " " + extra);
		isPreparing = false;
		return false;
	}

	public void onGamePause() {
		playWhenReady = false;
		if (!isPreparing && !currentTrack.isEmpty()) {
			try {
				if (player.isPlaying()) player.pause();
			} catch (IllegalStateException ignored) {}
		}
	}

	/** Resume only if we still have a track loaded. */
	public void onGameResume() {
		if (!isPreparing && !currentTrack.isEmpty()) {
			try { player.start(); } catch (IllegalStateException ignored) {}
		}
	}

	/** Hard stop + clear state (used when leaving the game entirely). */
	public void onGameStop() {
		playWhenReady = false;
		isPreparing = false;
		try {
			if (!currentTrack.isEmpty()) {
				player.stop();
				player.reset();
			}
		} catch (IllegalStateException ignored) {}
		currentTrack = "";
		pendingTrack = "";
	}

	// ---------- internals ----------

	private boolean prepareTrack() {
		if (currentTrack.equals(pendingTrack)) return true;

		File source = resolveMusicFile(pendingTrack);
		if (source == null) {
			Log.w(TAG, "no file for " + pendingTrack);
			return false;
		}

		Log.d(TAG, "preparing " + source.getAbsolutePath());

		try {
			if (!currentTrack.isEmpty()) {
				player.stop();
				player.reset();
			}

			try (FileInputStream fis = new FileInputStream(source)) {
				player.setDataSource(fis.getFD());
			}

			isPreparing = true;
			currentTrack = pendingTrack;
			player.prepareAsync();
			return true;
		} catch (IOException | IllegalStateException | IllegalArgumentException | SecurityException e) {
			Log.e(TAG, "prepare failed", e);
			isPreparing = false;
			currentTrack = "";
			return false;
		}
	}

	private File resolveMusicFile(String track) {
		String norm = track.replace('-', '_');

		// 1. mod override: external/mods/<id>/music/
		File modMusic = getModMusicDir();
		if (modMusic != null) {
			File f = firstExisting(
			new File(modMusic, norm + ".mp3"),
			new File(modMusic, norm),
			new File(modMusic, track + ".mp3"),
			new File(modMusic, track)
			);
			if (f != null) return f;
		}

		// 2. extracted: external/music/
		File extMusic = getExternalMusicDir();
		if (extMusic != null) {
			File f = firstExisting(
			new File(extMusic, norm),
			new File(extMusic, norm + ".mp3"),
			new File(extMusic, track),
			new File(extMusic, track + ".mp3")
			);
			if (f != null) return f;
		}

		return null;
	}

	private static File firstExisting(File... files) {
		for (File f : files) {
			if (f != null && f.isFile()) return f;
		}
		return null;
	}

	private File getModMusicDir() {
		String modId = MainActivity.currentMod();
		if (modId == null || modId.isEmpty()) return null;
		File ext = activity.getExternalFilesDir(null);
		if (ext == null) return null;
		return new File(ext, "mods/" + modId + "/music");
	}

	private File getExternalMusicDir() {
		File ext = activity.getExternalFilesDir(null);
		return ext != null ? new File(ext, "music") : null;
	}

	private void applyLooping() {
		if (!isPreparing) player.setLooping(looping);
	}

	private void applyVolume() {
		if (!isPreparing) player.setVolume(volume, volume);
	}

	// keep the native declaration so the original constructor still links
	public static native void initMusicPlayer();
}