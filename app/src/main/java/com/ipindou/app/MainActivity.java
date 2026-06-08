package com.ipindou.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.ipindou.app.core.BeadColor;
import com.ipindou.app.core.BeadPalette;
import com.ipindou.app.core.BeadPattern;
import com.ipindou.app.core.PatternGenerator;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int OPEN_IMAGE = 1001;
    private static final int WRITE_STORAGE = 1002;
    private PatternView patternView;
    private TextView stats;
    private EditText widthInput;
    private EditText heightInput;
    private CheckBox removeBackground;
    private CheckBox outlineEnabled;
    private Button outlineColorButton;
    private Bitmap sourceBitmap;
    private String pendingSaveMessage;
    private int selectedColorIndex = BeadPalette.nearestOpaqueIndex(0xff000000);
    private int outlineColorIndex = BeadPalette.nearestOpaqueIndex(0xff000000);

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        patternView.setPattern(blankPattern(29, 29));
        updateStats();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 18, 18, 18);

        TextView title = new TextView(this);
        title.setText("iPinDou 拼豆图纸工作台");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(toolbar);
        toolbar.addView(button("导入图片", v -> openImagePicker()));
        toolbar.addView(button("生成并保存图纸", v -> generatePattern(true)));
        toolbar.addView(button("水平镜像", v -> mirrorHorizontal()));
        toolbar.addView(button("垂直镜像", v -> mirrorVertical()));
        toolbar.addView(button("导出PNG", v -> exportPattern()));

        LinearLayout settings = new LinearLayout(this);
        settings.setGravity(Gravity.CENTER);
        settings.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(settings);
        widthInput = input("29"); heightInput = input("29");
        removeBackground = new CheckBox(this); removeBackground.setText("自动去背景"); removeBackground.setChecked(true);
        outlineEnabled = new CheckBox(this); outlineEnabled.setText("自动描边"); outlineEnabled.setChecked(true);
        outlineColorButton = button("描边色：" + BeadPalette.colorAt(outlineColorIndex).code, v -> setOutlineColorFromBrush());
        settings.addView(label("宽")); settings.addView(widthInput); settings.addView(label("高")); settings.addView(heightInput);
        settings.addView(removeBackground); settings.addView(outlineEnabled); settings.addView(outlineColorButton);

        patternView = new PatternView(this);
        patternView.setSelectedColor(selectedColorIndex);
        patternView.setCellEditor((x, y) -> updateStats());
        root.addView(patternView, new LinearLayout.LayoutParams(-1, 0, 1));

        HorizontalScrollView swatchesScroll = new HorizontalScrollView(this);
        LinearLayout swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < BeadPalette.colors().length; i++) swatches.addView(swatch(i));
        swatchesScroll.addView(swatches);
        root.addView(swatchesScroll);

        stats = new TextView(this);
        stats.setTextSize(13);
        stats.setPadding(0, 8, 0, 0);
        root.addView(stats);
        return root;
    }

    private Button button(String text, View.OnClickListener listener) { Button b = new Button(this); b.setText(text); b.setOnClickListener(listener); return b; }
    private TextView label(String text) { TextView v = new TextView(this); v.setText(text); v.setGravity(Gravity.CENTER); v.setPadding(10, 0, 4, 0); return v; }
    private EditText input(String text) { EditText e = new EditText(this); e.setText(text); e.setEms(3); e.setSelectAllOnFocus(true); e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); return e; }

    private View swatch(int colorIndex) {
        BeadColor color = BeadPalette.colorAt(colorIndex);
        Button b = new Button(this);
        b.setText(color.code + "\n" + color.name);
        b.setTextSize(10);
        b.setBackgroundColor(color.argb == 0x00ffffff ? 0xffeeeeee : color.argb);
        int luminance = (((color.argb >>> 16) & 0xff) * 30 + ((color.argb >>> 8) & 0xff) * 59 + (color.argb & 0xff) * 11) / 100;
        b.setTextColor(luminance < 130 ? 0xffffffff : 0xff111111);
        b.setOnClickListener(v -> { selectedColorIndex = colorIndex; patternView.setSelectedColor(colorIndex); Toast.makeText(this, "画笔：" + color.code + " " + color.name, Toast.LENGTH_SHORT).show(); });
        return b;
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, OPEN_IMAGE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_IMAGE && resultCode == RESULT_OK && data != null) {
            try (InputStream in = getContentResolver().openInputStream(data.getData())) {
                sourceBitmap = BitmapFactory.decodeStream(in);
                generatePattern(false);
            } catch (Exception e) { showError("导入失败：" + e.getMessage()); }
        }
    }

    private void generatePattern(boolean saveAfterGenerate) {
        int w = parseSize(widthInput, 29), h = parseSize(heightInput, 29);
        if (sourceBitmap == null) {
            patternView.setPattern(blankPattern(w, h));
            updateStats();
            if (saveAfterGenerate) saveCurrentPattern("已生成并保存空白 " + w + "x" + h + " 图纸。");
            else Toast.makeText(this, "已生成空白 " + w + "x" + h + " 图纸，可直接手绘或先导入图片。", Toast.LENGTH_LONG).show();
            return;
        }
        Bitmap scaled = sourceBitmap.copy(Bitmap.Config.ARGB_8888, false);
        int[] pixels = new int[scaled.getWidth() * scaled.getHeight()];
        scaled.getPixels(pixels, 0, scaled.getWidth(), 0, 0, scaled.getWidth(), scaled.getHeight());
        BeadPattern generated = PatternGenerator.fromPixels(pixels, scaled.getWidth(), scaled.getHeight(), w, h, removeBackground.isChecked());
        if (outlineEnabled.isChecked()) generated = PatternGenerator.withOutline(generated, outlineColorIndex);
        patternView.setPattern(generated);
        updateStats();
        if (saveAfterGenerate) saveCurrentPattern("已生成并保存 " + w + "x" + h + " 图纸。");
        else Toast.makeText(this, "已生成 " + w + "x" + h + " 图纸。", Toast.LENGTH_SHORT).show();
    }

    private BeadPattern blankPattern(int width, int height) {
        BeadPattern pattern = new BeadPattern(width, height);
        pattern.fill(BeadPalette.nearestOpaqueIndex(0xffffffff));
        return pattern;
    }

    private int parseSize(EditText input, int fallback) {
        try { return Math.max(8, Math.min(120, Integer.parseInt(input.getText().toString()))); } catch (Exception ignored) { return fallback; }
    }

    private void mirrorHorizontal() { BeadPattern p = patternView.getPattern(); if (p != null) { patternView.setPattern(p.mirroredHorizontal()); updateStats(); } }
    private void mirrorVertical() { BeadPattern p = patternView.getPattern(); if (p != null) { patternView.setPattern(p.mirroredVertical()); updateStats(); } }

    private void setOutlineColorFromBrush() {
        outlineColorIndex = selectedColorIndex == BeadPalette.transparentIndex() ? BeadPalette.nearestOpaqueIndex(0xff000000) : selectedColorIndex;
        BeadColor color = BeadPalette.colorAt(outlineColorIndex);
        outlineColorButton.setText("描边色：" + color.code);
        outlineColorButton.setBackgroundColor(color.argb);
        Toast.makeText(this, "描边色：" + color.code + " " + color.name, Toast.LENGTH_SHORT).show();
    }

    private void exportPattern() {
        saveCurrentPattern("已导出到图库");
    }

    private void saveCurrentPattern(String successPrefix) {
        if (!hasWritePermission()) {
            pendingSaveMessage = successPrefix;
            requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, WRITE_STORAGE);
            return;
        }
        BeadPattern p = patternView.getPattern();
        if (p == null) return;
        try {
            Bitmap out = renderPatternBitmap(p, 64);
            String name = "ipindou_" + System.currentTimeMillis() + ".png";
            Uri uri = savePatternBitmap(out, name);
            if (uri == null) throw new IllegalStateException("系统图库拒绝写入");
            Toast.makeText(this, successPrefix + "：" + name, Toast.LENGTH_LONG).show();
        } catch (Exception e) { showError("保存失败：" + e.getMessage()); }
    }

    private boolean hasWritePermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WRITE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && pendingSaveMessage != null) {
                String message = pendingSaveMessage;
                pendingSaveMessage = null;
                saveCurrentPattern(message);
            } else {
                pendingSaveMessage = null;
                showError("保存失败：需要存储权限才能写入系统图库");
            }
        }
    }

    private Uri savePatternBitmap(Bitmap bitmap, String name) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/iPinDou");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return null;

        boolean success = false;
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("无法打开图库输出流");
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IllegalStateException("PNG 编码失败");
            success = true;
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues pending = new ContentValues();
                pending.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContentResolver().update(uri, pending, null, null);
            }
            if (!success) getContentResolver().delete(uri, null, null);
        }
        return uri;
    }

    private Bitmap renderPatternBitmap(BeadPattern p, int cell) {
        int ruler = Math.max(36, (int)(cell * 0.72f));
        int legendPadding = 28;
        int legendRow = 52;
        int swatch = 34;
        int[] counts = colorCounts(p);
        int usedColors = usedColorCount(counts);
        int legendColumns = Math.max(1, Math.min(4, Math.max(1, p.width * cell / 220)));
        int legendRows = (usedColors + legendColumns - 1) / legendColumns;
        int legendHeight = usedColors == 0 ? 0 : legendPadding * 2 + legendRows * legendRow + 34;
        int gridWidth = p.width * cell;
        int gridHeight = p.height * cell;
        Bitmap bitmap = Bitmap.createBitmap(gridWidth + ruler * 2, gridHeight + ruler * 2 + legendHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTextAlign(Paint.Align.CENTER);
        canvas.drawColor(0xffffffff);
        drawExportRulers(canvas, p, cell, ruler, gridWidth, gridHeight, paint, text);
        for (int y = 0; y < p.height; y++) for (int x = 0; x < p.width; x++) {
            BeadColor c = BeadPalette.colorAt(p.get(x, y));
            int left = ruler + x * cell, top = ruler + y * cell;
            paint.setStyle(Paint.Style.FILL); paint.setColor(c.argb == 0x00ffffff ? 0x22ffffff : c.argb);
            canvas.drawRect(left, top, left + cell, top + cell, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2); paint.setColor(0xff6d5f55);
            canvas.drawRect(left, top, left + cell, top + cell, paint);
            int lum = luminance(c.argb);
            text.setTextSize(cell * 0.32f);
            text.setColor(lum < 130 ? 0xffffffff : 0xff111111);
            canvas.drawText(c.code, left + cell / 2f, top + cell * 0.61f, text);
        }
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(4); paint.setColor(0xff514238);
        canvas.drawRect(ruler, ruler, ruler + gridWidth, ruler + gridHeight, paint);
        drawColorLegend(canvas, counts, ruler, ruler + gridHeight + ruler, gridWidth, legendColumns, legendPadding, legendRow, swatch, paint, text);
        return bitmap;
    }

    private void drawExportRulers(Canvas canvas, BeadPattern p, int cell, int ruler, int gridWidth, int gridHeight, Paint paint, Paint text) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(0xffefe2cf);
        canvas.drawRect(ruler, 0, ruler + gridWidth, ruler, paint);
        canvas.drawRect(ruler, ruler + gridHeight, ruler + gridWidth, ruler + gridHeight + ruler, paint);
        canvas.drawRect(0, ruler, ruler, ruler + gridHeight, paint);
        canvas.drawRect(ruler + gridWidth, ruler, ruler + gridWidth + ruler, ruler + gridHeight, paint);
        text.setColor(0xff5b4633); text.setTextSize(Math.max(18f, cell * 0.28f)); text.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = text.getFontMetrics();
        float baselineOffset = -(fm.ascent + fm.descent) / 2f;
        for (int x = 0; x < p.width; x++) {
            String number = String.valueOf(x + 1);
            float cx = ruler + x * cell + cell / 2f;
            canvas.drawText(number, cx, ruler / 2f + baselineOffset, text);
            canvas.drawText(number, cx, ruler + gridHeight + ruler / 2f + baselineOffset, text);
        }
        for (int y = 0; y < p.height; y++) {
            String number = String.valueOf(y + 1);
            float cy = ruler + y * cell + cell / 2f + baselineOffset;
            canvas.drawText(number, ruler / 2f, cy, text);
            canvas.drawText(number, ruler + gridWidth + ruler / 2f, cy, text);
        }
    }

    private void drawColorLegend(Canvas canvas, int[] counts, int left, int top, int width, int columns, int padding, int rowHeight, int swatch, Paint paint, Paint text) {
        if (usedColorCount(counts) == 0) return;
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(0xff3f342c);
        text.setTextSize(28f);
        canvas.drawText("颜色用量", left, top + padding, text);
        int columnWidth = Math.max(180, width / columns);
        int item = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] <= 0 || i == BeadPalette.transparentIndex()) continue;
            int col = item % columns;
            int row = item / columns;
            int x = left + col * columnWidth;
            int y = top + padding + 18 + row * rowHeight;
            BeadColor color = BeadPalette.colorAt(i);
            paint.setStyle(Paint.Style.FILL); paint.setColor(color.argb);
            canvas.drawRect(x, y, x + swatch, y + swatch, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2); paint.setColor(0xff6d5f55);
            canvas.drawRect(x, y, x + swatch, y + swatch, paint);
            text.setTextSize(22f); text.setColor(0xff3f342c);
            canvas.drawText(color.code + "  x" + counts[i], x + swatch + 12, y + swatch * 0.72f, text);
            item++;
        }
        text.setTextAlign(Paint.Align.CENTER);
    }

    private int[] colorCounts(BeadPattern p) {
        int[] counts = new int[BeadPalette.colors().length];
        for (int y = 0; y < p.height; y++) for (int x = 0; x < p.width; x++) counts[p.get(x, y)]++;
        return counts;
    }

    private int usedColorCount(int[] counts) {
        int used = 0;
        for (int i = 0; i < counts.length; i++) if (i != BeadPalette.transparentIndex() && counts[i] > 0) used++;
        return used;
    }

    private int luminance(int argb) { return (((argb >>> 16) & 0xff) * 30 + ((argb >>> 8) & 0xff) * 59 + (argb & 0xff) * 11) / 100; }

    private void updateStats() {
        BeadPattern p = patternView.getPattern();
        if (p == null || stats == null) return;
        StringBuilder s = new StringBuilder(String.format(Locale.US, "尺寸：%d x %d，共 %d 颗。用量：", p.width, p.height, p.width * p.height));
        for (int i = 0; i < BeadPalette.colors().length; i++) {
            int count = p.countOf(i);
            if (count > 0) s.append(BeadPalette.colorAt(i).code).append('=').append(count).append(' ');
        }
        stats.setText(s.toString());
    }

    private void showError(String message) { new AlertDialog.Builder(this).setTitle("iPinDou").setMessage(message).setPositiveButton("知道了", null).show(); }
}
