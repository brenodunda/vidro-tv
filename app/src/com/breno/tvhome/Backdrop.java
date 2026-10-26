package com.breno.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;

import java.io.File;
import java.io.InputStream;

/**
 * Papel de parede na resolucao da tela + uma copia pequena e desfocada.
 * Os paineis de vidro desenham o trecho da copia desfocada que fica atras deles:
 * vidro fosco "real" sem precisar de RenderEffect (indisponivel no Android 9).
 */
final class Backdrop {
    static final int DOWNSCALE = 8;
    Bitmap full;     // papel de parede recortado ao tamanho da tela (null = preto)
    Bitmap blur;     // versao reduzida (1/8) e desfocada, saturada
    int screenW, screenH;

    void load(Context c, Look look, File custom, int w, int h) {
        screenW = w;
        screenH = h;
        Bitmap src = null;
        String key = Look.WALLPAPERS[look.wallpaper];
        try {
            if (key.equals("personalizado") && custom != null) {
                src = decode(custom, null, w, h);
            } else if (!key.equals("preto") && !key.equals("personalizado")) {
                InputStream in = c.getAssets().open("wallpapers/" + key + ".jpg");
                src = BitmapFactory.decodeStream(in);
                in.close();
            }
        } catch (Exception ignored) { }
        if (full != null) full.recycle();
        if (blur != null) blur.recycle();
        full = null;
        blur = null;
        if (src == null) return;
        full = centerCrop(src, w, h);
        if (src != full) src.recycle();
        rebuildBlur(look);
    }

    void rebuildBlur(Look look) {
        if (full == null) return;
        if (blur != null) blur.recycle();
        int bw = Math.max(8, screenW / DOWNSCALE), bh = Math.max(8, screenH / DOWNSCALE);
        Bitmap small = Bitmap.createScaledBitmap(full, bw, bh, true);
        // saturacao e leve clareamento: o vidro "realca" as cores atras dele
        Bitmap out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.35f);
        ColorMatrix bright = new ColorMatrix(new float[]{
                1.06f, 0, 0, 0, 6,
                0, 1.06f, 0, 0, 6,
                0, 0, 1.06f, 0, 8,
                0, 0, 0, 1, 0});
        cm.postConcat(bright);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        new Canvas(out).drawBitmap(small, 0, 0, p);
        small.recycle();
        int r = look.blurRadius();
        for (int i = 0; i < 3; i++) boxBlur(out, r);
        blur = out;
    }

    private static Bitmap decode(File f, Object unused, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int s = 1;
        while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= h) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
    }

    private static Bitmap centerCrop(Bitmap src, int w, int h) {
        float sc = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        int cw = Math.round(w / sc), ch = Math.round(h / sc);
        int x = (src.getWidth() - cw) / 2, y = (src.getHeight() - ch) / 2;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(src, new Rect(x, y, x + cw, y + ch), new Rect(0, 0, w, h),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    /** Box blur separavel (horizontal + vertical), em CPU, numa imagem pequena. */
    private static void boxBlur(Bitmap b, int r) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h], tmp = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        pass(px, tmp, w, h, r, true);
        pass(tmp, px, w, h, r, false);
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    private static void pass(int[] in, int[] out, int w, int h, int r, boolean horiz) {
        int len = horiz ? w : h, lines = horiz ? h : w, div = 2 * r + 1;
        for (int l = 0; l < lines; l++) {
            int sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int c = in[idx(Math.max(0, Math.min(len - 1, i)), l, w, horiz)];
                sr += (c >> 16) & 255; sg += (c >> 8) & 255; sb += c & 255;
            }
            for (int i = 0; i < len; i++) {
                out[idx(i, l, w, horiz)] = 0xFF000000 | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int a = in[idx(Math.min(len - 1, i + r + 1), l, w, horiz)];
                int d = in[idx(Math.max(0, i - r), l, w, horiz)];
                sr += ((a >> 16) & 255) - ((d >> 16) & 255);
                sg += ((a >> 8) & 255) - ((d >> 8) & 255);
                sb += (a & 255) - (d & 255);
            }
        }
    }

    private static int idx(int i, int l, int w, boolean horiz) { return horiz ? l * w + i : i * w + l; }
}
