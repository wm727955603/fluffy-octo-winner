package com.cloudminds.speechidentify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONException;
import org.json.JSONObject;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SynthesizerListener;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.iflytek.thirdparty.l;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener {

	private static String TAG = MainActivity.class.getSimpleName();
	// 语音听写对象
	private SpeechRecognizer mIat;
	// 语音听写UI
	private RecognizerDialog mIatDialog;

	private SpeechSynthesizer mTts;

	protected int timeCount = 1;
	// 默认发音人
	private String voicer = "xiaoyan";
	// 用HashMap存储听写结果
	private HashMap<String, String> mIatResults = new LinkedHashMap<String, String>();

	private TextView mResultText;
	private TextView mChoiceText;
	private Button mbutton;
	private Toast mToast;
	// 引擎类型
	private String mEngineType = SpeechConstant.TYPE_CLOUD;

	/*
	 * 话筒相关view
	 */
	private ViewPager viewPager;// 滑屏
	private View textView;// 文本界面
	private View voiceView;// 语音界面
	private ViewPagerAdapter viewPagerAdapter;// view适配器
	private List<View> views;

	private ImageView btn_send_message;
	private EditText et_content;
	private Button btn_asr;
	private Button btn_rec_1;
	private Button btn_rec_2;
	private Animation voiceAnim;
	private VoiceSpectrum spectrum_voice;
	protected boolean text_page = false;

	private PlayerUtil playerUtil;

	private Timer speakTimer;
	private MySpeakTask speakTask;

	private Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {
			case 0:
				listening();
				break;
			}

		};
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		initLayout();
		initListener();
		initData();

		mIat = SpeechRecognizer.createRecognizer(MainActivity.this, mInitListener);
		mTts = SpeechSynthesizer.createSynthesizer(MainActivity.this, mTtsInitListener);

		playerUtil = new PlayerUtil();

		// 初始化听写Dialog，如果只使用有UI听写功能，无需创建SpeechRecognizer
		// 使用UI听写功能，请根据sdk文件目录下的notice.txt,放置布局文件和图片资源
		mIatDialog = new RecognizerDialog(MainActivity.this, mInitListener);
		mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
		speak("请问有什么可以帮您");

	}

	private void initLayout() {
		mResultText = (TextView) findViewById(R.id.tv_show);
		mChoiceText = (TextView) findViewById(R.id.tv_choice);

		viewPager = (ViewPager) findViewById(R.id.viewPager);
		voiceView = LayoutInflater.from(this).inflate(R.layout.part_voice, null);
		textView = LayoutInflater.from(this).inflate(R.layout.part_text, null);

		// 频谱
		spectrum_voice = (VoiceSpectrum) voiceView.findViewById(R.id.spectrum_voice);
		btn_asr = (Button) voiceView.findViewById(R.id.btn_asr);
		btn_rec_1 = (Button) voiceView.findViewById(R.id.btn_rec_1);
		btn_rec_2 = (Button) voiceView.findViewById(R.id.btn_rec_2);
		btn_send_message = (ImageView) textView.findViewById(R.id.btn_send_message);
		et_content = (EditText) textView.findViewById(R.id.et_content);
	}

	private void initListener() {
		btn_send_message.setOnClickListener(this);
		btn_asr.setOnClickListener(this);
		spectrum_voice.setOnClickListener(this);
		et_content.setOnClickListener(this);
	}

	private void initData() {
		views = new ArrayList<View>();
		views.add(voiceView);
		views.add(textView);
		viewPagerAdapter = new ViewPagerAdapter();
		viewPagerAdapter.setViews(views);
		viewPager.setAdapter(viewPagerAdapter);
		viewPager.setOnPageChangeListener(onPageChangeListener);

		voiceAnim = AnimationUtils.loadAnimation(this, R.anim.voice_rotate);
		voiceAnim.setInterpolator(new LinearInterpolator());
		showVoice(1);
	}

	/**
	 * pageChange 页面滑动时调用
	 */
	OnPageChangeListener onPageChangeListener = new OnPageChangeListener() {

		@Override
		public void onPageSelected(int arg0) {
			switch (arg0) {
			case 0:// voice
				text_page = false;
				closeInputMethod();
				if (playerUtil != null && playerUtil.isPlayering()) {
					showVoice(1);
				} else {
					listening();
				}
				break;
			default:
				text_page = true;
				mTts.destroy();
				mIat.destroy();
				break;
			}
		}

		@Override
		public void onPageScrolled(int arg0, float arg1, int arg2) {
		}

		@Override
		public void onPageScrollStateChanged(int arg0) {

		}
	};

	/**
	 * 关闭键盘
	 */
	public void closeInputMethod() {
		InputMethodManager inputMethodManager = (InputMethodManager) this
				.getSystemService(Context.INPUT_METHOD_SERVICE);
		try {
			if (inputMethodManager.isActive()) {
				inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getApplicationWindowToken(),
						InputMethodManager.HIDE_NOT_ALWAYS);
			}
			;
		} catch (Exception e) {
			Log.e(TAG, "关闭键盘失败");
		}
	}

	int ret = 0; // 函数调用返回值

	private void listening() {
		// 设置参数
		setIatParam();
		boolean isShowDialog = false;
		if (isShowDialog) {
			// 显示听写对话框
			mIatDialog.setListener(mRecognizerDialogListener);
			mIatDialog.show();
			mToast.makeText(MainActivity.this, getString(R.string.text_begin), Toast.LENGTH_LONG).show();
		} else {
			// 不显示听写对话框
			ret = mIat.startListening(mRecognizerListener);
			if (ret != ErrorCode.SUCCESS) {
				Toast.makeText(MainActivity.this, "听写失败,错误码：" + ret, 0).show();
				;
			} else {
				Toast.makeText(MainActivity.this, getString(R.string.text_begin), 0).show();
				;
			}
		}
	};

	/**
	 * Iat 初始化监听器。
	 */
	private InitListener mInitListener = new InitListener() {

		@Override
		public void onInit(int code) {
			Log.d(TAG, "SpeechRecognizer init() code = " + code);
			if (code != ErrorCode.SUCCESS) {
				Log.e(TAG, "初始化失败，错误码：" + code);
			}
			Toast.makeText(MainActivity.this, "初始化成功", 1).show();
		}
	};

	/**
	 * TTs 初始化监听。
	 */
	private InitListener mTtsInitListener = new InitListener() {
		@Override
		public void onInit(int code) {
			Log.d(TAG, "InitListener init() code = " + code);
			if (code != ErrorCode.SUCCESS) {
				showTip("初始化失败,错误码：" + code);
			} else {
				// 初始化成功，之后可以调用startSpeaking方法
				// 注：有的开发者在onCreate方法中创建完合成对象之后马上就调用startSpeaking进行合成，
				// 正确的做法是将onCreate中的startSpeaking调用移至这里
			}
		}
	};

	/**
	 * 听写监听器。
	 */
	private RecognizerListener mRecognizerListener = new RecognizerListener() {

		@Override
		public void onBeginOfSpeech() {
			// 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
			// showTip("开始说话");
			showVoice(3);
		}

		@Override
		public void onError(SpeechError error) {
			// Tips：
			// 错误码：10118(您没有说话)，可能是录音机权限被禁，需要提示用户打开应用的录音权限。
			// 如果使用本地功能（语记）需要提示用户开启语记的录音权限。
			// showTip(error.getPlainDescription(true));
			if (true) {// 一直监听
				if (speakTimer != null) {
					speakTimer.cancel();
				}
				if (speakTask != null) {
					speakTask.cancel();
				}
				speakTimer = new Timer(true);
				speakTask = new MySpeakTask();
				speakTimer.schedule(speakTask, 1000);
			} else {
				if (timeCount < 3) {
					speak(getResources().getString(R.string.help));
					// mIat.startListening(mRecognizerListener);
					timeCount++;
				} else {// 三次未说话就停止
					showVoice(1);
					if (mIat.isListening()) {
						mIat.stopListening();
					}
					timeCount = 1;

				}
			}

		}

		@Override
		public void onEndOfSpeech() {
			// 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
			// showTip("结束说话");
			showVoice(2);
		}

		@Override
		public void onResult(RecognizerResult results, boolean isLast) {
			Log.d(TAG, results.getResultString());
			String contentText = printResult(results);
			if (contentText != null && !"".equals(contentText.trim()) && contentText.length() > 1) {
				showTip("内容不是为空");
				mResultText.setText(null);// 清空显示内容
				mIatResults.clear();
				mResultText.setText(contentText);
				String choiceText = wordJudgment(contentText);
				mChoiceText.setText(null);
				mChoiceText.setText(choiceText);
				showVoice(1);
				speak(choiceText);
			} else {
				showTip("内容为空");
				mResultText.setText(null);// 清空显示内容
				mIatResults.clear();
				mResultText.setText("没有说话");
				listening();
			}
			// }
		}

		@Override
		public void onVolumeChanged(int volume, byte[] data) {
			Log.d(TAG, "返回音频数据：" + data.length);
			spectrum_voice.updateVisualizer(data);
		}

		@Override
		public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
			// 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
			// 若使用本地能力，会话id为null
			// if (SpeechEvent.EVENT_SESSION_ID == eventType) {
			// String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
			// Log.d(TAG, "session id =" + sid);
			// }
		}
	};

	/**
	 * Tts合成回调监听。
	 */
	private SynthesizerListener mTtsListener = new SynthesizerListener() {

		@Override
		public void onSpeakBegin() {
			showVoice(1);
		}

		@Override
		public void onSpeakPaused() {
		}

		@Override
		public void onSpeakResumed() {
		}

		@Override
		public void onBufferProgress(int percent, int beginPos, int endPos, String info) {
			// 合成进度
		}

		@Override
		public void onSpeakProgress(int percent, int beginPos, int endPos) {
			// 播放进度
		}

		@Override
		public void onCompleted(SpeechError error) {
			if (error == null) {
				// playerDi();
				playerUtil.playDi(getBaseContext(), R.raw.office);
				if (speakTimer != null) {
					speakTimer.cancel();
				}
				if (speakTask != null) {
					speakTask.cancel();
				}
				speakTimer = new Timer(true);
				speakTask = new MySpeakTask();
				speakTimer.schedule(speakTask, 2000);
				if (!playerUtil.isPlayering()) {
					listening();
				}
			} else {
				showTip("语音合成错误");
			}
		}

		@Override
		public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
			// 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
			// 若使用本地能力，会话id为null
			// if (SpeechEvent.EVENT_SESSION_ID == eventType) {
			// String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
			// Log.d(TAG, "session id =" + sid);
			// }
		}
	};

	/**
	 * Iat听写UI监听器
	 */
	private RecognizerDialogListener mRecognizerDialogListener = new RecognizerDialogListener() {
		public void onResult(RecognizerResult results, boolean isLast) {

		}

		/**
		 * 识别回调错误.
		 */
		public void onError(SpeechError error) {
			Log.e(TAG, error.getPlainDescription(true));
		}

	};

	private String printResult(RecognizerResult results) {
		String text = JsonParser.parseIatResult(results.getResultString());
		String sn = null;
		// 读取json结果中的sn字段
		try {
			JSONObject resultJson = new JSONObject(results.getResultString());
			sn = resultJson.optString("sn");
		} catch (JSONException e) {
			e.printStackTrace();
		}

		mIatResults.put(sn, text);

		StringBuffer resultBuffer = new StringBuffer();
		for (String key : mIatResults.keySet()) {
			resultBuffer.append(mIatResults.get(key));
		}
		showTip(resultBuffer.toString());
		return resultBuffer.toString();
	}

	private String wordJudgment(String s) {
		String choiceText = "";
		if (s.contains("钱")) {
			choiceText = "钱币识别";
		} else if (s.contains("谁")) {
			choiceText = "人脸识别";
		} else if (s.contains("什么东西")) {
			choiceText = "商品识别";
		} else if (s.contains("牌")) {
			choiceText = "LOGO识别";
		} else
			choiceText = s;

		return "您选择了：" + choiceText;

	}

	/**
	 * iat语音识别参数设置
	 * 
	 * @param param
	 * @return
	 */
	public void setIatParam() {
		// 清空参数
		mIat.setParameter(SpeechConstant.PARAMS, null);

		// 设置听写引擎
		mIat.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
		// 设置返回结果格式
		mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");

		String lag = "mandarin";
		if (lag.equals("en_us")) {
			// 设置语言
			mIat.setParameter(SpeechConstant.LANGUAGE, "en_us");
		} else {
			// 设置语言
			mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
			// 设置语言区域
			mIat.setParameter(SpeechConstant.ACCENT, lag);
		}

		// 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
		// mIat.setParameter(SpeechConstant.VAD_BOS, "4000");

		// 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
		mIat.setParameter(SpeechConstant.VAD_EOS, "1000");

		// 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
		mIat.setParameter(SpeechConstant.ASR_PTT, "1");

		// 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
		// 注：AUDIO_FORMAT参数语记需要更新版本才能生效
		mIat.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
		mIat.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory() + "/msc/iat.wav");
	}

	/**
	 * tts语音合成参数设置
	 * 
	 * @param param
	 * @return
	 */
	private void setTtsParam() {
		// 清空参数
		mTts.setParameter(SpeechConstant.PARAMS, null);
		// 根据合成引擎设置相应参数
		if (mEngineType.equals(SpeechConstant.TYPE_CLOUD)) {
			mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
			// 设置在线合成发音人
			mTts.setParameter(SpeechConstant.VOICE_NAME, voicer);
			// 设置合成语速
			mTts.setParameter(SpeechConstant.SPEED, "50");
			// 设置合成音调
			mTts.setParameter(SpeechConstant.PITCH, "50");
			// 设置合成音量
			mTts.setParameter(SpeechConstant.VOLUME, "50");
		} else {
			mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
			// 设置本地合成发音人 voicer为空，默认通过语记界面指定发音人。
			mTts.setParameter(SpeechConstant.VOICE_NAME, "");
			/**
			 * TODO 本地合成不设置语速、音调、音量，默认使用语记设置 开发者如需自定义参数，请参考在线合成参数设置
			 */
		}
		// 设置播放器音频流类型
		mTts.setParameter(SpeechConstant.STREAM_TYPE, "3");
		// 设置播放合成音频打断音乐播放，默认为true
		mTts.setParameter(SpeechConstant.KEY_REQUEST_FOCUS, "true");

		// 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
		// 注：AUDIO_FORMAT参数语记需要更新版本才能生效
		mTts.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
		mTts.setParameter(SpeechConstant.TTS_AUDIO_PATH, Environment.getExternalStorageDirectory() + "/msc/tts.wav");
	}

	private void showTip(final String str) {
		mToast.setText(str);
		mToast.show();
	}

	private void speak(String s) {
		setTtsParam();
		showVoice(1);
		if (mIat.isListening()) {
			mIat.cancel();
		}
		mTts.startSpeaking(s, mTtsListener);
	}

	/**
	 * 不同operation，bottom展示不同
	 * 
	 * @param operation
	 */
	private void showVoice(int opration) {
		switch (opration) {
		case 1:// 话筒
			if (voiceAnim != null) {
				btn_rec_1.clearAnimation();
			}
			btn_asr.setVisibility(View.VISIBLE);
			btn_rec_1.setVisibility(View.GONE);
			btn_rec_2.setVisibility(View.GONE);
			spectrum_voice.setVisibility(View.GONE);
			break;
		case 2:// 识别中
			btn_asr.setVisibility(View.GONE);
			btn_rec_1.setVisibility(View.VISIBLE);
			btn_rec_2.setVisibility(View.VISIBLE);
			spectrum_voice.setVisibility(View.GONE);
			if (voiceAnim != null) {
				btn_rec_1.startAnimation(voiceAnim);
			}
			break;
		case 3:// 频谱
			if (voiceAnim != null) {
				btn_rec_1.clearAnimation();
			}
			btn_asr.setVisibility(View.GONE);
			btn_rec_1.setVisibility(View.GONE);
			btn_rec_2.setVisibility(View.GONE);
			spectrum_voice.setVisibility(View.VISIBLE);
			break;
		default:
			break;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
		playerUtil.stop();
		playerUtil.release();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		// 退出时释放连接
		mIat.cancel();
		mIat.destroy();

		showVoice(1);
		mTts.stopSpeaking();
		// 退出时释放连接
		mTts.destroy();
		speakTask = null;
		speakTimer = null;
		playerUtil.stop();
		playerUtil.release();
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch (v.getId()) {
		case R.id.btn_send_message:// 文本的发送
			// baseApplication.closeInputMethod(mContext);
			String content = et_content.getText().toString().trim();
			if (TextUtils.isEmpty(content)) {
				showTip("您输入的内容为空。");
			} else {
				mResultText.setText(content);
				et_content.setText("");
			}
			break;
		case R.id.btn_asr:// 话筒
			if (btn_asr.getVisibility() == View.VISIBLE) {
				mTts.destroy();
				listening();
			}
			break;
		case R.id.spectrum_voice:// 频谱
			if (spectrum_voice.getVisibility() == View.VISIBLE) {
				mTts.stopSpeaking();
				mIat.cancel();
				showVoice(1);
				if (mIat.isListening()) {
					mIat.stopListening();
				}

			}
			break;
		default:
			break;
		}
	}

	public class MySpeakTask extends TimerTask {

		@Override
		public void run() {
			Message message = new Message();
			message.what = 0;
			mHandler.sendMessage(message);
		}
	}

}
