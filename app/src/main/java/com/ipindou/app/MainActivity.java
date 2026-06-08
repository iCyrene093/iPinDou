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
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int OPEN_IMAGE = 1001;
    private static final int WRITE_STORAGE = 1002;
    private static final String STATE_PATTERN_WIDTH = "patternWidth";
    private static final String STATE_PATTERN_HEIGHT = "patternHeight";
    private static final String STATE_PATTERN_INDEXES = "patternIndexes";
    private static final String STATE_SOURCE_URI = "sourceUri";
    private static final String STATE_WIDTH_INPUT = "widthInput";
    private static final String STATE_HEIGHT_INPUT = "heightInput";
    private static final String STATE_REMOVE_BACKGROUND = "removeBackground";
    private static final String STATE_OUTLINE_ENABLED = "outlineEnabled";
    private static final String STATE_SELECTED_COLOR = "selectedColor";
    private static final String STATE_OUTLINE_COLOR = "outlineColor";
    private static final int DEFAULT_PATTERN_SIZE = 29;
    private static final int MAX_EXPORT_CELL = 64;
    private static final int MIN_EXPORT_CELL = 18;
    private static final int MAX_EXPORT_GRID_SIDE = 4096;
    private PatternView patternView;
    private TextView stats;
    private EditText widthInput;
    private EditText heightInput;
    private CheckBox removeBackground;
    private CheckBox outlineEnabled;
    private Button outlineColorButton;
    private Bitmap sourceBitmap;
    private Uri sourceImageUri;
    private String pendingSaveMessage;
    private final Object sourceBitmapLock = new Object();
    private final ExecutorService generatorExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int generationToken = 0;
    private int selectedColorIndex = BeadPalette.nearestOpaqueIndex(0xff000000);
    private int outlineColorIndex = BeadPalette.nearestOpaqueIndex(0xff000000);

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) restoreColorSelection(savedInstanceState);
        configureCutoutHandling();
        setContentView(buildUi());
        if (savedInstanceState != null) restoreUiState(savedInstanceState);
        BeadPattern restoredPattern = savedInstanceState == null ? null : restorePattern(savedInstanceState);
        patternView.setPattern(restoredPattern != null ? restoredPattern : blankPattern(parseSize(widthInput, DEFAULT_PATTERN_SIZE), parseSize(heightInput, DEFAULT_PATTERN_SIZE)));
        updateStats();
        if (savedInstanceState != null) restoreSourceBitmap(savedInstanceState);
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        BeadPattern pattern = patternView == null ? null : patternView.getPattern();
        if (pattern != null) {
            outState.putInt(STATE_PATTERN_WIDTH, pattern.width);
            outState.putInt(STATE_PATTERN_HEIGHT, pattern.height);
            outState.putIntArray(STATE_PATTERN_INDEXES, pattern.copyIndexes());
        }
        if (sourceImageUri != null) outState.putString(STATE_SOURCE_URI, sourceImageUri.toString());
        if (widthInput != null) outState.putString(STATE_WIDTH_INPUT, widthInput.getText().toString());
        if (heightInput != null) outState.putString(STATE_HEIGHT_INPUT, heightInput.getText().toString());
        if (removeBackground != null) outState.putBoolean(STATE_REMOVE_BACKGROUND, removeBackground.isChecked());
        if (outlineEnabled != null) outState.putBoolean(STATE_OUTLINE_ENABLED, outlineEnabled.isChecked());
        outState.putInt(STATE_SELECTED_COLOR, selectedColorIndex);
        outState.putInt(STATE_OUTLINE_COLOR, outlineColorIndex);
    }

    private void restoreColorSelection(Bundle state) {
        selectedColorIndex = clampPaletteIndex(state.getInt(STATE_SELECTED_COLOR, selectedColorIndex));
        outlineColorIndex = clampOpaquePaletteIndex(state.getInt(STATE_OUTLINE_COLOR, outlineColorIndex));
    }

    private void restoreUiState(Bundle state) {
        widthInput.setText(state.getString(STATE_WIDTH_INPUT, String.valueOf(DEFAULT_PATTERN_SIZE)));
        heightInput.setText(state.getString(STATE_HEIGHT_INPUT, String.valueOf(DEFAULT_PATTERN_SIZE)));
        removeBackground.setChecked(state.getBoolean(STATE_REMOVE_BACKGROUND, true));
        outlineEnabled.setChecked(state.getBoolean(STATE_OUTLINE_ENABLED, true));
        patternView.setSelectedColor(selectedColorIndex);
        applyOutlineColorButtonStyle(false);
    }

    private BeadPattern restorePattern(Bundle state) {
        int width = state.getInt(STATE_PATTERN_WIDTH, -1);
        int height = state.getInt(STATE_PATTERN_HEIGHT, -1);
        int[] indexes = state.getIntArray(STATE_PATTERN_INDEXES);
        if (width <= 0 || height <= 0 || indexes == null) return null;
        try {
            return new BeadPattern(width, height, indexes);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void restoreSourceBitmap(Bundle state) {
        String uriText = state.getString(STATE_SOURCE_URI);
        if (uriText == null || uriText.trim().isEmpty()) return;
        sourceImageUri = Uri.parse(uriText);
        final Uri uri = sourceImageUri;
        final int beadWidth = parseSize(widthInput, DEFAULT_PATTERN_SIZE);
        final int beadHeight = parseSize(heightInput, DEFAULT_PATTERN_SIZE);
        final int token = ++generationToken;
        generatorExecutor.execute(() -> {
            try {
                Bitmap decoded = decodeSampledBitmap(uri, beadWidth, beadHeight);
                mainHandler.post(() -> {
                    if (token != generationToken) {
                        decoded.recycle();
                        return;
                    }
                    replaceSourceBitmap(decoded);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (token == generationToken) Toast.makeText(this, "导入图片未能恢复，可重新选择图片。", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private int clampPaletteIndex(int index) {
        return Math.max(0, Math.min(BeadPalette.colors().length - 1, index));
    }

    private int clampOpaquePaletteIndex(int index) {
        return Math.max(0, Math.min(BeadPalette.standardColorCount() - 1, index));
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        applySafeAreaPadding(root, 18, 18, 18, 18);

        TextView title = new TextView(this);
        title.setText("iPinDou 拼豆图纸工作台");
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(horizontalScroll(toolbar));
        toolbar.addView(button("导入图片", v -> openImagePicker()));
        toolbar.addView(button("生成图纸", v -> generatePattern(false)));
        toolbar.addView(button("保存图纸", v -> saveCurrentPattern("已保存图纸")));
        toolbar.addView(button("水平镜像", v -> mirrorHorizontal()));
        toolbar.addView(button("垂直镜像", v -> mirrorVertical()));

        LinearLayout settings = new LinearLayout(this);
        settings.setGravity(Gravity.CENTER);
        settings.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(horizontalScroll(settings));
        widthInput = input("29"); heightInput = input("29");
        removeBackground = new CheckBox(this); removeBackground.setText("去背景"); removeBackground.setChecked(true);
        outlineEnabled = new CheckBox(this); outlineEnabled.setText("描边"); outlineEnabled.setChecked(true);
        outlineColorButton = button("描边色：" + BeadPalette.colorAt(outlineColorIndex).code, v -> showOutlineColorPicker());
        CompoundButton.OnCheckedChangeListener previewListener = (buttonView, isChecked) -> refreshGeneratedPreview();
        removeBackground.setOnCheckedChangeListener(previewListener);
        outlineEnabled.setOnCheckedChangeListener(previewListener);
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

    private void configureCutoutHandling() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Window window = getWindow();
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
            window.setAttributes(attributes);
        }
    }

    private void applySafeAreaPadding(View view, int left, int top, int right, int bottom) {
        view.setPadding(left, top, right, bottom);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            view.setOnApplyWindowInsetsListener((v, insets) -> {
                int safeLeft = insets.getSystemWindowInsetLeft();
                int safeTop = insets.getSystemWindowInsetTop();
                int safeRight = insets.getSystemWindowInsetRight();
                int safeBottom = insets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    DisplayCutout cutout = insets.getDisplayCutout();
                    if (cutout != null) {
                        safeLeft = Math.max(safeLeft, cutout.getSafeInsetLeft());
                        safeTop = Math.max(safeTop, cutout.getSafeInsetTop());
                        safeRight = Math.max(safeRight, cutout.getSafeInsetRight());
                        safeBottom = Math.max(safeBottom, cutout.getSafeInsetBottom());
                    }
                }
                v.setPadding(left + safeLeft, top + safeTop, right + safeRight, bottom + safeBottom);
                return insets;
            });
        }
    }

    private Button button(String text, View.OnClickListener listener) { Button b = new Button(this); b.setText(text); b.setOnClickListener(listener); return b; }
    private HorizontalScrollView horizontalScroll(View child) { HorizontalScrollView scroll = new HorizontalScrollView(this); scroll.setFillViewport(true); scroll.addView(child); return scroll; }
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
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, OPEN_IMAGE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri == null) {
                showError("导入失败：未选择图片");
                return;
            }
            persistReadPermission(imageUri, data);
            sourceImageUri = imageUri;
            final int beadWidth = parseSize(widthInput, DEFAULT_PATTERN_SIZE);
            final int beadHeight = parseSize(heightInput, DEFAULT_PATTERN_SIZE);
            final int token = ++generationToken;
            Toast.makeText(this, "正在导入图片…", Toast.LENGTH_SHORT).show();
            generatorExecutor.execute(() -> {
                try {
                    Bitmap decoded = decodeSampledBitmap(imageUri, beadWidth, beadHeight);
                    mainHandler.post(() -> {
                        if (token != generationToken) { decoded.recycle(); return; }
                        replaceSourceBitmap(decoded);
                        generatePattern(false);
                    });
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        if (token == generationToken) showError("导入失败：" + e.getMessage());
                    });
                }
            });
        }
    }

    private void persistReadPermission(Uri uri, Intent data) {
        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (flags == 0) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (SecurityException ignored) {
            // Some document providers grant only transient access. The current import still works,
            // but the source image may need to be reselected after Activity recreation.
        }
    }

    private void replaceSourceBitmap(Bitmap decoded) {
        Bitmap old;
        synchronized (sourceBitmapLock) {
            old = sourceBitmap;
            sourceBitmap = decoded;
        }
        if (old != null && old != decoded && !old.isRecycled()) old.recycle();
    }

    private Bitmap decodeSampledBitmap(Uri uri, int beadWidth, int beadHeight) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IllegalArgumentException("无法读取图片尺寸");

        int maxDecodeWidth = Math.max(512, Math.min(2048, beadWidth * 16));
        int maxDecodeHeight = Math.max(512, Math.min(2048, beadHeight * 16));
        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
        decode.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDecodeWidth, maxDecodeHeight);
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(in, null, decode);
            if (bitmap == null) throw new IllegalArgumentException("图片解码失败");
            return bitmap;
        }
    }

    private int calculateInSampleSize(int width, int height, int reqWidth, int reqHeight) {
        int inSampleSize = 1;
        while (width / (inSampleSize * 2) >= reqWidth && height / (inSampleSize * 2) >= reqHeight) {
            inSampleSize *= 2;
        }
        return inSampleSize;
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        generationToken++;
        generatorExecutor.shutdownNow();
        saveExecutor.shutdownNow();
        synchronized (sourceBitmapLock) {
            if (sourceBitmap != null && !sourceBitmap.isRecycled()) sourceBitmap.recycle();
            sourceBitmap = null;
        }
    }

    private void generatePattern(boolean saveAfterGenerate) {
        generatePattern(saveAfterGenerate, false);
    }

    private void generatePattern(boolean saveAfterGenerate, boolean silentPreview) {
        int w = parseSize(widthInput, DEFAULT_PATTERN_SIZE), h = parseSize(heightInput, DEFAULT_PATTERN_SIZE);
        SourcePixels source = snapshotSourcePixels();
        if (source == null) {
            patternView.setPattern(blankPattern(w, h));
            updateStats();
            if (saveAfterGenerate) saveCurrentPattern("已生成并保存空白 " + w + "x" + h + " 图纸。");
            else if (!silentPreview) Toast.makeText(this, "已生成空白 " + w + "x" + h + " 图纸，可直接手绘或先导入图片。", Toast.LENGTH_LONG).show();
            return;
        }
        final boolean removeBg = removeBackground.isChecked();
        final boolean addOutline = outlineEnabled.isChecked();
        final int outlineIndex = outlineColorIndex;
        final int token = ++generationToken;
        if (!silentPreview) Toast.makeText(this, "正在生成 " + w + "x" + h + " 图纸…", Toast.LENGTH_SHORT).show();
        generatorExecutor.execute(() -> {
            try {
                BeadPattern generated = PatternGenerator.fromPixels(source.pixels, source.width, source.height, w, h, removeBg);
                final BeadPattern result = addOutline ? PatternGenerator.withOutline(generated, outlineIndex) : generated;
                mainHandler.post(() -> {
                    if (token != generationToken) return;
                    patternView.setPattern(result);
                    updateStats();
                    if (saveAfterGenerate) saveCurrentPattern("已生成并保存 " + w + "x" + h + " 图纸。");
                    else if (!silentPreview) Toast.makeText(this, "已生成 " + w + "x" + h + " 图纸。", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (token == generationToken) showError("生成失败：" + e.getMessage());
                });
            }
        });
    }

    private SourcePixels snapshotSourcePixels() {
        synchronized (sourceBitmapLock) {
            if (sourceBitmap == null || sourceBitmap.isRecycled()) return null;
            int width = sourceBitmap.getWidth();
            int height = sourceBitmap.getHeight();
            int[] pixels = new int[width * height];
            sourceBitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            return new SourcePixels(pixels, width, height);
        }
    }

    private static final class SourcePixels {
        final int[] pixels;
        final int width;
        final int height;

        SourcePixels(int[] pixels, int width, int height) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
        }
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

    private void refreshGeneratedPreview() {
        if (hasSourceBitmap()) generatePattern(false, true);
    }

    private boolean hasSourceBitmap() {
        synchronized (sourceBitmapLock) {
            return sourceBitmap != null && !sourceBitmap.isRecycled();
        }
    }

    private void showOutlineColorPicker() {
        BeadColor[] colors = BeadPalette.colors();
        int colorCount = BeadPalette.standardColorCount();
        String[] names = new String[colorCount];
        int[] indexes = new int[colorCount];
        int selected = 0;
        for (int i = 0; i < colorCount; i++) {
            BeadColor color = colors[i];
            int item = i;
            indexes[item] = i;
            names[item] = color.code + "  " + color.name;
            if (i == outlineColorIndex) selected = item;
        }
        new AlertDialog.Builder(this)
                .setTitle("选择描边颜色")
                .setSingleChoiceItems(names, selected, (dialog, which) -> {
                    outlineColorIndex = indexes[which];
                    applyOutlineColorButtonStyle(true);
                    refreshGeneratedPreview();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void applyOutlineColorButtonStyle(boolean showToast) {
        BeadColor color = BeadPalette.colorAt(outlineColorIndex);
        outlineColorButton.setText("描边色：" + color.code);
        outlineColorButton.setBackgroundColor(color.argb);
        int luminance = (((color.argb >>> 16) & 0xff) * 30 + ((color.argb >>> 8) & 0xff) * 59 + (color.argb & 0xff) * 11) / 100;
        outlineColorButton.setTextColor(luminance < 130 ? 0xffffffff : 0xff111111);
        if (showToast) Toast.makeText(this, "描边色：" + color.code + " " + color.name, Toast.LENGTH_SHORT).show();
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
            BeadPattern snapshot = new BeadPattern(p.width, p.height, p.copyIndexes());
            int cell = exportCellSize(snapshot);
            Toast.makeText(this, "正在后台保存图纸…", Toast.LENGTH_SHORT).show();
            saveExecutor.execute(() -> {
                Bitmap out = null;
                try {
                    out = renderPatternBitmap(snapshot, cell);
                    String name = "ipindou_" + System.currentTimeMillis() + ".png";
                    Uri uri = savePatternBitmap(out, name);
                    if (uri == null) throw new IllegalStateException("系统图库拒绝写入");
                    mainHandler.post(() -> Toast.makeText(this, successPrefix + "：" + name, Toast.LENGTH_LONG).show());
                } catch (OutOfMemoryError e) {
                    mainHandler.post(() -> showError("保存失败：图纸过大，请降低尺寸后重试"));
                } catch (Exception e) {
                    mainHandler.post(() -> showError("保存失败：" + e.getMessage()));
                } finally {
                    if (out != null && !out.isRecycled()) out.recycle();
                }
            });
        } catch (Exception e) { showError("保存失败：" + e.getMessage()); }
    }

    private int exportCellSize(BeadPattern pattern) {
        int longestSide = Math.max(pattern.width, pattern.height);
        if (longestSide <= 0) return MAX_EXPORT_CELL;
        return Math.max(MIN_EXPORT_CELL, Math.min(MAX_EXPORT_CELL, MAX_EXPORT_GRID_SIDE / longestSide));
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
        int transparent = BeadPalette.transparentIndex();
        int beadCount = p.nonTransparentCount();
        StringBuilder s = new StringBuilder(String.format(Locale.US, "尺寸：%d x %d，共 %d 颗。用量：", p.width, p.height, beadCount));
        for (int i = 0; i < BeadPalette.colors().length; i++) {
            if (i == transparent) continue;
            int count = p.countOf(i);
            if (count > 0) s.append(BeadPalette.colorAt(i).code).append('=').append(count).append(' ');
        }
        int emptyCount = p.countOf(transparent);
        if (emptyCount > 0) s.append("空格=").append(emptyCount);
        stats.setText(s.toString());
    }

    private void showError(String message) { new AlertDialog.Builder(this).setTitle("iPinDou").setMessage(message).setPositiveButton("知道了", null).show(); }
}
