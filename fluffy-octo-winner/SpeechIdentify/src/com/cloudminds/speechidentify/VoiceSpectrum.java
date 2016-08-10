package com.cloudminds.speechidentify;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class VoiceSpectrum extends View {

	private byte[] mBytes;
	private float[] mPoints;
	private Rect mRect = new Rect();

	private Paint mForePaint = new Paint();
	private int mSpectrumNum = 72;

	public VoiceSpectrum(Context context) {
		super(context);
		init();
	}
	public VoiceSpectrum(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}
	public VoiceSpectrum(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	private void init() {
		mBytes = null;
		mForePaint.setStrokeWidth(3f);
		mForePaint.setAntiAlias(true);
		mForePaint.setColor(Color.rgb(105, 190, 99));
	}

	public void updateVisualizer(byte[] fft) {
		byte[] mode1 = new byte[fft.length / 2 + 1];
		mode1[0] = (byte) Math.abs(fft[0]);
		for (int i = 2, j = 1; j < mSpectrumNum * 2;) {
			// Math.hypot()返回 sqrt(x2 +y2)
			mode1[j] = (byte) Math.hypot(fft[i], fft[i + 1]);
			i += 2;
			j++;
		}
		mBytes = mode1;
		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		Log.d("TAG", "调用onDraw");
		super.onDraw(canvas);
		if (mBytes == null) {
			return;
		}
		if (mPoints == null || mPoints.length < mBytes.length * 4) {
			// mPoints用来存储画直线的2个坐标（x,y）
			mPoints = new float[mBytes.length * 4];
		}
		mRect.set(0, 0, getWidth(), getHeight());
		// baseX是每个刻度长度
		final int baseX = mRect.width() / mSpectrumNum;
		final int height = mRect.height();
		for (int i = 0; i < mSpectrumNum; i++) {
			if (mBytes[i] < 0) {
				mBytes[i] = 127;
			}
			final int xi = baseX * i + baseX / 2;
			mPoints[i * 4] = xi;
			mPoints[i * 4 + 1] = height;
			mPoints[i * 4 + 2] = xi;
			mPoints[i * 4 + 3] = height - mBytes[i];
		}
		canvas.drawLines(mPoints, mForePaint);
	}
}