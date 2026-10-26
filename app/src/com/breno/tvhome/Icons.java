package com.breno.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Icones vetoriais minimos desenhados no Canvas (entradas, wi-fi, engrenagem). */
final class Icons extends Drawable {
    static final int INPUT = 0, WIFI = 1, GEAR = 2, DROP = 3, PLUS = 4;
    private final int kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    Icons(int kind, int color) {
        this.kind = kind;
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    void setColor(int c) { p.setColor(c); invalidateSelf(); }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        float x = b.left + (b.width() - s) / 2f, y = b.top + (b.height() - s) / 2f;
        p.setStrokeWidth(s * 0.1f);
        if (kind == INPUT) {
            // tela com seta entrando
            RectF r = new RectF(x + s * .12f, y + s * .18f, x + s * .88f, y + s * .70f);
            c.drawRoundRect(r, s * .08f, s * .08f, p);
            c.drawLine(x + s * .35f, y + s * .86f, x + s * .65f, y + s * .86f, p);
            Path a = new Path();
            a.moveTo(x + s * .30f, y + s * .44f); a.lineTo(x + s * .62f, y + s * .44f);
            a.moveTo(x + s * .50f, y + s * .32f); a.lineTo(x + s * .62f, y + s * .44f);
            a.lineTo(x + s * .50f, y + s * .56f);
            c.drawPath(a, p);
        } else if (kind == WIFI) {
            float cx = x + s / 2f, cy = y + s * .84f;
            for (int i = 1; i <= 3; i++) {
                float rr = s * (0.16f + 0.2f * i);
                c.drawArc(new RectF(cx - rr, cy - rr, cx + rr, cy + rr), 225, 90, false, p);
            }
            Paint d = new Paint(p); d.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy - s * .02f, s * .07f, d);
        } else if (kind == DROP) {
            // gota (aparencia / vidro)
            Path d = new Path();
            float cx = x + s / 2f;
            d.moveTo(cx, y + s * .10f);
            d.cubicTo(cx + s * .34f, y + s * .45f, cx + s * .34f, y + s * .88f, cx, y + s * .88f);
            d.cubicTo(cx - s * .34f, y + s * .88f, cx - s * .34f, y + s * .45f, cx, y + s * .10f);
            c.drawPath(d, p);
            c.drawArc(new RectF(cx - s * .17f, y + s * .52f, cx + s * .05f, y + s * .74f), 100, 80, false, p);
        } else if (kind == PLUS) {
            c.drawLine(x + s * .5f, y + s * .2f, x + s * .5f, y + s * .8f, p);
            c.drawLine(x + s * .2f, y + s * .5f, x + s * .8f, y + s * .5f, p);
        } else {
            float cx = x + s / 2f, cy = y + s / 2f;
            c.drawCircle(cx, cy, s * .17f, p);
            Path g = new Path();
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                g.moveTo(cx + (float) Math.cos(a) * s * .30f, cy + (float) Math.sin(a) * s * .30f);
                g.lineTo(cx + (float) Math.cos(a) * s * .42f, cy + (float) Math.sin(a) * s * .42f);
            }
            c.drawPath(g, p);
            c.drawCircle(cx, cy, s * .30f, p);
        }
    }

    @Override public void setAlpha(int a) { p.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
