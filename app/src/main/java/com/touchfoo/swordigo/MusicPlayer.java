package com.touchfoo.swordigo;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MusicPlayer implements MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener {
    Activity activity;
    String currentFileName = "";
    String desiredFileName = "";
    boolean looping = false;
    MediaPlayer mediaPlayer;
    boolean preparing = false;
    boolean shouldPlayWhenPrepared = false;
    float volume = 1.0F;

    public MusicPlayer(Activity activity) {
        this.activity = activity;
        this.mediaPlayer = new MediaPlayer();

        this.mediaPlayer.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .build()
        );

        this.mediaPlayer.setOnErrorListener(this);
        this.mediaPlayer.setOnPreparedListener(this);
        this.initMusicPlayer();
    }

    public native void initMusicPlayer();

    public boolean loadFile(String fileName) {
        Debug.Log("MusicPlayer loadFile: " + fileName);
        this.desiredFileName = fileName;
        this.shouldPlayWhenPrepared = false;
        return this.preparing || this.prepareDesiredFile();
    }

    public boolean onError(MediaPlayer mp, int what, int extra) {
        Debug.Log("MusicPlayer.onError: " + what + ", " + extra);
        return false;
    }

    public void onPrepared(MediaPlayer mp) {
        Debug.Log("MusicPlayer onPrepared!");
        this.preparing = false;
        if (this.currentFileName.equals(this.desiredFileName)) {
            if (this.shouldPlayWhenPrepared) {
                this.mediaPlayer.start();
                this.shouldPlayWhenPrepared = false;
            }

            this.updateLoopingSetting();
            this.updateVolumeSetting();
        } else {
            this.prepareDesiredFile();
        }
    }

    public void pause() {
        Debug.Log("MusicPlayer Pause!");
        this.shouldPlayWhenPrepared = false;
        if (!this.preparing && !this.currentFileName.isEmpty()) {
            this.mediaPlayer.pause();
        }
    }

    public void play() {
        Debug.Log("MusicPlayer Play!");
        if (!this.preparing) {
            this.mediaPlayer.start();
        } else {
            this.shouldPlayWhenPrepared = true;
        }
    }

    boolean prepareDesiredFile() {
        if (this.currentFileName.equals(this.desiredFileName)) return true;

        String assetPath = "music/" + desiredFileName.replace('-', '_') + ".mp3";
        Debug.Log("Preparing music asset: " + assetPath);
        try {
            if (!currentFileName.isEmpty()) {
                mediaPlayer.stop();
                mediaPlayer.reset();
            }			// The game's music lives inside the mounted game APK, not the
			// launcher's own assets — use the game AssetManager first.
			AssetManager am = Native.gameAssets != null ? Native.gameAssets : activity.getAssets();
			try (AssetFileDescriptor afd = am.openFd(assetPath)) {
				mediaPlayer.setDataSource(
						afd.getFileDescriptor(),
						afd.getStartOffset(),
						afd.getLength()
				);
			} catch (IOException fdFailed) {
				// Compressed assets can't be opened as a file descriptor —
				// extract to cache and play from a plain file instead.
				File dir = new File(activity.getCacheDir(), "music");
				if (!dir.exists()) dir.mkdirs();
				File file = new File(dir, assetPath.replace('/', '_'));
				try (InputStream in = am.open(assetPath);
					 FileOutputStream fos = new FileOutputStream(file)) {
					byte[] buffer = new byte[8192];
					int n;
					while ((n = in.read(buffer)) != -1) fos.write(buffer, 0, n);
				}
				mediaPlayer.setDataSource(file.getAbsolutePath());
			}

			preparing = true;
            currentFileName = desiredFileName;
            mediaPlayer.prepareAsync();
            return true;

        } catch (IOException | IllegalArgumentException | SecurityException e) {
            Debug.Log("Asset load failed: ", e);
            return false;
        }
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
        this.updateLoopingSetting();
    }

    public void setVolume(float volume) {
        this.volume = volume;
        this.updateVolumeSetting();
    }

    public void stop() {
        Debug.Log("MusicPlayer stop!");
        this.shouldPlayWhenPrepared = false;
        if (!this.preparing && !this.currentFileName.isEmpty()) {
            this.mediaPlayer.stop();
            this.currentFileName = "";
        }
    }

    void updateLoopingSetting() {
        if (!this.preparing) {
            this.mediaPlayer.setLooping(this.looping);
        }
    }

    void updateVolumeSetting() {
        if (!this.preparing) {
            this.mediaPlayer.setVolume(this.volume, this.volume);
        }
    }
}