package com.breno.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Drawable de "vidro liquido".
 *  - frost=true : amostra o papel de parede desfocado atras do painel (vidro fosco)
 *                 ou, no modo leve, um preenchimento translucido simples;
 *  - sempre     : "leite" branco (transparencia), reflexo especular no topo,
 *                 borda iluminada (mais forte em cima e embaixo, como uma lente).
 * glow (0..1) intensifica reflexo e borda (foco/selecao).
 */
final class Frost extends Drawable {
    private final Backdrop bd;
    private final Look look;
    private final View host;
    private final boolean frost;
    float radius;
    float glow = 0f;
    int tint = 0xFFFFFFFF;         // cor do "leite" (branco ou cor do app)
    float tintScale = 1f;

    private final Paint pBlur = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSpec = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pShade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private final int[] loc = new int[2];
    private final RectF r = new RectF();
    private Bitmap shaderFor;
    private BitmapShader shader;
    private int builtVersion = -1, builtH = -1;
    private float builtGlow = -1;

    Frost(Backdrop bd, Look look, View host, float radius, boolean frost) {
        this.bd = bd;
        this.look = look;
        this.host = host;
        this.radius = radius;
        this.frost = frost;
        pRim.setStyle(Paint.Style.STROKE);
    }

    private void rebuild(Rect b) {
        float k = look.spec() * (0.75f + 0.6f * glow);
        int h = Math.max(1, b.height());
        pSpec.setShader(new LinearGradient(0, b.top, 0, b.top + h * 0.55f,
                argb(Math.min(255, 70 * k), 255, 255, 255), 0x00FFFFFF, Shader.TileMode.CLAMP));
        pShade.setShader(new LinearGradient(0, b.bottom - h * 0.35f, 0, b.bottom,
                0x00000000, argb(frost ? 40 : 24, 0, 0, 0), Shader.TileMode.CLAMP));
        float sw = Math.max(1.2f, h * 0.012f + 0.6f) * (1f + glow * 0.6f);
        pRim.setStrokeWidth(sw);
        pRim.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                new int[]{argb(Math.min(255, 175 * k), 255, 255, 255), argb(Math.min(255, 38 * k), 255, 255, 255),
                        argb(Math.min(255, 20 * k), 255, 255, 255), argb(Math.min(255, 95 * k), 255, 255, 255)},
                new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        builtVersion = look.version;
        builtH = h;
        builtGlow = glow;
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        if (builtVersion != look.version || builtH != b.height() || builtGlow != glow) rebuild(b);
        r.set(b);
        float rad = Math.min(radius, r.height() / 2f);

        if (frost) {
            if (look.realGlass() && bd.blur != null) {
                if (shaderFor != bd.blur) {
                    shader = new BitmapShader(bd.blur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    shaderFor = bd.blur;
                }
                host.getLocationInWindow(loc);
                float s = bd.screenW / (float) bd.blur.getWidth();
                m.setScale(s, s);
                m.postTranslate(-loc[0], -loc[1]);
                shader.setLocalMatrix(m);
                pBlur.setShader(shader);
                c.drawRoundRect(r, rad, rad, pBlur);
            } else {
                // modo leve / fundo preto: vidro escuro translucido, sem desfoque
                pFill.setColor(bd.full == null ? 0x2EFFFFFF : 0x8C14161C);
                c.drawRoundRect(r, rad, rad, pFill);
            }
            int a = Math.round(look.tintAlpha() * tintScale);
            pFill.setColor((tint & 0x00FFFFFF) | (Math.min(255, a) << 24));
            c.drawRoundRect(r, rad, rad, pFill);
            c.drawRoundRect(r, rad, rad, pShade);
        }
        c.drawRoundRect(r, rad, rad, pSpec);
        float h = pRim.getStrokeWidth() / 2f;
        r.inset(h, h);
        c.drawRoundRect(r, rad - h, rad - h, pRim);
    }

    private static int argb(float a, int r, int g, int b) {
        return (Math.round(Math.max(0, Math.min(255, a))) << 24) | (r << 16) | (g << 8) | b;
    }

    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
