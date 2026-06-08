package com.ipindou.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.ipindou.app.core.BeadColor;
import com.ipindou.app.core.BeadPalette;
import com.ipindou.app.core.BeadPattern;

public class PatternView extends View {
    public interface CellEditor { void onCellEdited(int x, int y); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private BeadPattern pattern;
    private int selectedColor = 18;
    private CellEditor editor;

    public PatternView(Context context) { super(context); init(); }
    public PatternView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        textPaint.setTextAlign(Paint.Align.CENTER);
        setBackgroundColor(0xfff6f1e9);
    }

    public void setPattern(BeadPattern pattern) { this.pattern = pattern; invalidate(); }
    public BeadPattern getPattern() { return pattern; }
    public void setSelectedColor(int selectedColor) { this.selectedColor = selectedColor; }
    public void setCellEditor(CellEditor editor) { this.editor = editor; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (pattern == null) return;
        float cell = Math.min(getWidth() / (float) pattern.width, getHeight() / (float) pattern.height);
        float left = (getWidth() - cell * pattern.width) / 2f;
        float top = (getHeight() - cell * pattern.height) / 2f;
        textPaint.setTextSize(Math.max(7f, cell * 0.32f));
        for (int y = 0; y < pattern.height; y++) for (int x = 0; x < pattern.width; x++) {
            BeadColor color = BeadPalette.colorAt(pattern.get(x, y));
            float l = left + x * cell, t = top + y * cell;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color.argb == 0x00ffffff ? 0x11ffffff : color.argb);
            canvas.drawRect(new RectF(l, t, l + cell, t + cell), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, cell * 0.025f));
            paint.setColor(0x663a2a1f);
            canvas.drawRect(l, t, l + cell, t + cell, paint);
            int luminance = (((color.argb >>> 16) & 0xff) * 30 + ((color.argb >>> 8) & 0xff) * 59 + (color.argb & 0xff) * 11) / 100;
            textPaint.setColor(luminance < 130 ? 0xffffffff : 0xff222222);
            if (cell > 16) canvas.drawText(color.code, l + cell / 2f, t + cell * 0.62f, textPaint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (pattern == null) return true;
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            float cell = Math.min(getWidth() / (float) pattern.width, getHeight() / (float) pattern.height);
            float left = (getWidth() - cell * pattern.width) / 2f;
            float top = (getHeight() - cell * pattern.height) / 2f;
            int x = (int)((event.getX() - left) / cell);
            int y = (int)((event.getY() - top) / cell);
            if (x >= 0 && x < pattern.width && y >= 0 && y < pattern.height) {
                pattern.set(x, y, selectedColor);
                if (editor != null) editor.onCellEdited(x, y);
                invalidate();
            }
            return true;
        }
        return true;
    }
}
