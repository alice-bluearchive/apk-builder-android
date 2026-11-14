package com.example.androidstudio.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import com.example.androidstudio.build.BuildProgress;

public class BuildProgressView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private BuildProgress progress;

    public BuildProgressView(Context c) { this(c, null); }
    public BuildProgressView(Context c, AttributeSet a) {
        super(c, a);
        paint.setTypeface(Typeface.MONOSPACE);
        paint.setTextSize(28f);
        paint.setColor(0xFFD4D4D4);
        setVisibility(GONE);
    }

    public void show(BuildProgress p) {
        this.progress = p;
        setVisibility(VISIBLE);
        invalidate();
    }

    public void hide() {
        progress = null;
        setVisibility(GONE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (progress == null) return;
        int charW = (int) paint.measureText("M");
        int widthChars = Math.max(20, (getWidth() - 40) / charW - 12);
        String line = progress.render(widthChars);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float y = getHeight() / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(line, 20, y, paint);
    }
}