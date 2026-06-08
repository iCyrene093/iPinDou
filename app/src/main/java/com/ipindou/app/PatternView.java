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

    private static final float MIN_ZOOM = 1f;
    private static final float MAX_ZOOM = 6f;
    private static final int RULER_BACKGROUND = 0xffefe2cf;
    private static final int RULER_TEXT = 0xff5b4633;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private BeadPattern pattern;
    private int selectedColor = 12;
    private CellEditor editor;
    private float zoom = MIN_ZOOM;
    private float panX = 0f;
    private float panY = 0f;
    private float pinchStartDistance = 0f;
    private float pinchStartZoom = MIN_ZOOM;
    private float pinchStartPanX = 0f;
    private float pinchStartPanY = 0f;
    private float pinchStartFocusX = 0f;
    private float pinchStartFocusY = 0f;
    private boolean pinching = false;

    public PatternView(Context context) { super(context); init(); }
    public PatternView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        textPaint.setTextAlign(Paint.Align.CENTER);
        setBackgroundColor(0xfff6f1e9);
    }

    public void setPattern(BeadPattern pattern) {
        this.pattern = pattern;
        resetViewport();
        invalidate();
    }
    public BeadPattern getPattern() { return pattern; }
    public void setSelectedColor(int selectedColor) { this.selectedColor = selectedColor; }
    public void setCellEditor(CellEditor editor) { this.editor = editor; }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        clampPan();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (pattern == null) return;
        float cell = currentCellSize();
        float ruler = rulerSize(cell);
        float left = contentLeft(cell);
        float top = contentTop(cell);
        float gridWidth = cell * pattern.width;
        float gridHeight = cell * pattern.height;
        drawRulers(canvas, left, top, cell, ruler, gridWidth, gridHeight);
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
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, cell * 0.04f));
        paint.setColor(0xff6d5f55);
        canvas.drawRect(left, top, left + gridWidth, top + gridHeight, paint);
    }

    private void drawRulers(Canvas canvas, float left, float top, float cell, float ruler, float gridWidth, float gridHeight) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(RULER_BACKGROUND);
        canvas.drawRect(left, top - ruler, left + gridWidth, top, paint);
        canvas.drawRect(left, top + gridHeight, left + gridWidth, top + gridHeight + ruler, paint);
        canvas.drawRect(left - ruler, top, left, top + gridHeight, paint);
        canvas.drawRect(left + gridWidth, top, left + gridWidth + ruler, top + gridHeight, paint);
        textPaint.setColor(RULER_TEXT);
        textPaint.setTextSize(Math.max(7f, Math.min(cell * 0.38f, ruler * 0.46f)));
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baselineOffset = -(fm.ascent + fm.descent) / 2f;
        for (int x = 0; x < pattern.width; x++) {
            String number = String.valueOf(x + 1);
            float cx = left + x * cell + cell / 2f;
            canvas.drawText(number, cx, top - ruler / 2f + baselineOffset, textPaint);
            canvas.drawText(number, cx, top + gridHeight + ruler / 2f + baselineOffset, textPaint);
        }
        for (int y = 0; y < pattern.height; y++) {
            String number = String.valueOf(y + 1);
            float cy = top + y * cell + cell / 2f + baselineOffset;
            canvas.drawText(number, left - ruler / 2f, cy, textPaint);
            canvas.drawText(number, left + gridWidth + ruler / 2f, cy, textPaint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (pattern == null) return true;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2) {
            beginPinch(event);
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_UP) {
            pinching = event.getPointerCount() > 2;
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            pinching = false;
            return true;
        }
        if (event.getPointerCount() >= 2 && (action == MotionEvent.ACTION_MOVE || pinching)) {
            updatePinch(event);
            return true;
        }
        if (!pinching && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE)) {
            editCell(event.getX(), event.getY());
            return true;
        }
        return true;
    }

    private void beginPinch(MotionEvent event) {
        pinching = true;
        pinchStartDistance = pointerDistance(event);
        pinchStartZoom = zoom;
        pinchStartPanX = panX;
        pinchStartPanY = panY;
        pinchStartFocusX = focusX(event);
        pinchStartFocusY = focusY(event);
    }

    private void updatePinch(MotionEvent event) {
        if (!pinching) beginPinch(event);
        float distance = pointerDistance(event);
        if (pinchStartDistance <= 0f || distance <= 0f) return;
        zoom = clamp(pinchStartZoom * distance / pinchStartDistance, MIN_ZOOM, MAX_ZOOM);
        panX = pinchStartPanX + focusX(event) - pinchStartFocusX;
        panY = pinchStartPanY + focusY(event) - pinchStartFocusY;
        clampPan();
        invalidate();
    }

    private void editCell(float touchX, float touchY) {
        float cell = currentCellSize();
        float left = contentLeft(cell);
        float top = contentTop(cell);
        int x = (int)((touchX - left) / cell);
        int y = (int)((touchY - top) / cell);
        if (x >= 0 && x < pattern.width && y >= 0 && y < pattern.height) {
            pattern.set(x, y, selectedColor);
            if (editor != null) editor.onCellEdited(x, y);
            invalidate();
        }
    }

    private float currentCellSize() {
        if (pattern == null) return 0f;
        return baseCellSize() * zoom;
    }

    private float baseCellSize() {
        if (pattern == null) return 0f;
        float extraColumns = 2f * rulerCells();
        float extraRows = 2f * rulerCells();
        return Math.min(getWidth() / (pattern.width + extraColumns), getHeight() / (pattern.height + extraRows));
    }

    private float contentLeft(float cell) { return (getWidth() - cell * pattern.width) / 2f + panX; }
    private float contentTop(float cell) { return (getHeight() - cell * pattern.height) / 2f + panY; }
    private float rulerSize(float cell) { return cell * rulerCells(); }
    private float rulerCells() { return 0.72f; }

    private void resetViewport() {
        zoom = MIN_ZOOM;
        panX = 0f;
        panY = 0f;
        pinching = false;
    }

    private void clampPan() {
        if (pattern == null || getWidth() == 0 || getHeight() == 0) return;
        float contentWidth = currentCellSize() * pattern.width;
        float contentHeight = currentCellSize() * pattern.height;
        panX = clampAxisPan(panX, contentWidth, getWidth());
        panY = clampAxisPan(panY, contentHeight, getHeight());
    }

    private float clampAxisPan(float pan, float contentSize, float viewportSize) {
        if (contentSize <= viewportSize) return 0f;
        float max = (contentSize - viewportSize) / 2f;
        return clamp(pan, -max, max);
    }

    private float pointerDistance(MotionEvent event) {
        if (event.getPointerCount() < 2) return 0f;
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float)Math.sqrt(dx * dx + dy * dy);
    }

    private float focusX(MotionEvent event) { return (event.getX(0) + event.getX(1)) / 2f; }
    private float focusY(MotionEvent event) { return (event.getY(0) + event.getY(1)) / 2f; }

    private float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
