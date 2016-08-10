package com.cloudminds.speechidentify;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.util.Log;

public class PlayerUtil implements OnCompletionListener, OnPreparedListener {

	public MediaPlayer mediaPlayer; // 媒体播放器
	public int selectPlay = 1;
	public static final int MUSIC = 0;
	public static final int RESOURCE = 1;
	public static MusicPlayHandler musicPlayHandler = null;

	// 初始化播放器
	public PlayerUtil() {
		super();
		try {
			if(mediaPlayer!=null){
				mediaPlayer.release();
			}
			mediaPlayer = new MediaPlayer();
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);// 设置媒体流类型
			mediaPlayer.setOnPreparedListener(this);
			mediaPlayer.setOnCompletionListener(this);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void play() {
		mediaPlayer.start();
	}

	/**
	 * 
	 * @param url
	 *            url地址
	 */
	public void playUrl(final String url) {
		MessageHandleThreadFactory.getInstance().addTask(new Runnable() {

			@Override
			public void run() {
				try {
					mediaPlayer.reset();
					selectPlay = MUSIC;
					mediaPlayer.setDataSource(url); // 设置数据源
					mediaPlayer.prepareAsync();
				} catch (Exception e) {
					e.printStackTrace();
				}

			}
		});
	}

	public void playDi(final Context mContext, final int resource) {
		MessageHandleThreadFactory.getInstance().addTask(new Runnable() {

			@Override
			public void run() {
				try {
					selectPlay = RESOURCE;
					mediaPlayer = MediaPlayer.create(mContext, resource);// 播放固定资源
					mediaPlayer.start();
					mediaPlayer.setOnCompletionListener(new OnCompletionListener() {
						@Override
						public void onCompletion(MediaPlayer mp) {
							if (musicPlayHandler != null) {
								musicPlayHandler.onMusic(2, selectPlay);
							}
						}
					});
				} catch (Exception e) {
					e.printStackTrace();
				}

			}
		});
	}

	// 暂停
	public void pause() {
		mediaPlayer.pause();
	}

	// 停止
	public void stop() {
		if (musicPlayHandler != null) {
			musicPlayHandler.onMusic(1, selectPlay);
		}
		if (mediaPlayer != null) {
			mediaPlayer.stop();
		}
	}

	// 开始
	public void start() {
		if (musicPlayHandler != null) {
			musicPlayHandler.onMusic(3, selectPlay);
		}
		if (mediaPlayer != null) {
			mediaPlayer.start();
		}
	}

	public void release() {
		if (mediaPlayer != null) {
			mediaPlayer.release();
			mediaPlayer = null;
		}
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		if (musicPlayHandler != null) {
			musicPlayHandler.onMusic(0, selectPlay);
		}
		// mp.start();
		Log.e("mediaPlayer", "onPrepared");
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		if (musicPlayHandler != null) {
			musicPlayHandler.onMusic(2, selectPlay);
		}
		Log.e("mediaPlayer", "onCompletion");
	}

	public boolean isPlayering() {
		if (mediaPlayer != null) {
			return mediaPlayer.isPlaying();
		} else {
			return false;
		}

	}

	public static abstract interface MusicPlayHandler {
		public abstract void onMusic(int status, int select);// 0:onPrepared
																// 1:stop
																// 2.onCompletion
	}

}