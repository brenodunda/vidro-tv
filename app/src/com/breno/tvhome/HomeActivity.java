package com.breno.tvhome;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * "Vidro" - launcher de TV com acabamento de vidro liquido (identidade propria).
 * Android 9 / API 28: vidro fosco por amostragem do papel de parede pre-desfocado,
 * modo leve sem desfoque, animacoes com "mola", navegacao deterministica por D-pad.
 */
public class HomeActivity extends Activity {

    private static final int TOP = 0, DOCK = 1, CHIPS = 2, GRID = 3;
    private static final Locale PT = new Locale("pt", "BR");
    private static final String[] CATS = {"Todos", "Vídeo", "Música", "Jogos", "Ferramentas"};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Map<String, Drawable[]> artCache = new HashMap<>();
    private final Map<String, Integer> colorCache = new HashMap<>();

    private Config cfg;
    private Look look;
    private final Backdrop bd = new Backdrop();
    private float U;
    private int screenW, screenH;
    private Typeface fThin, fLight, fMedium, fSemi;
    private boolean homeMode;

    private FrameLayout root, content;
    private ImageView wall;
    private View scrim, bloom, shelf, lens;
    private TextView greeting, clock, date, title, toast;
    private LinearLayout pills, chipsRow;
    private final List<View> frostHosts = new ArrayList<>();
    private final View[] pillViews = new View[4];
    private final TextView[] pillText = new TextView[4];
    private final Icons[] pillIcons = new Icons[4];
    private final List<TextView> chipViews = new ArrayList<>();
    private final List<Integer> chipCats = new ArrayList<>();

    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> dockApps = new ArrayList<>();
    private final List<AppEntry> gridApps = new ArrayList<>();
    private final List<Tile> dockTiles = new ArrayList<>();
    private final List<Tile> gridTiles = new ArrayList<>();

    private int cols;
    private float M = 48, tileW, tileH, gap, pad, shelfX, shelfY, shelfH, chipsY, gridY0, rowPitch, labelH;
    private int area = DOCK, topIdx = 0, dockIdx = 0, chipIdx = 0, gridRow = 0, gridCol = 0;
    private boolean centerLong;
    private Panel panel;          // menu aberto (entradas / opcoes / aparencia) ou null
    private long cfgStamp;

    // ================================================================ ciclo de vida
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        cfg = new Config(this);
        look = new Look(this);
        Intent it = getIntent();
        homeMode = it != null && it.hasCategory(Intent.CATEGORY_HOME);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = Math.max(dm.widthPixels, dm.heightPixels);
        screenH = Math.min(dm.widthPixels, dm.heightPixels);
        U = screenW / 960f;
        fThin = font("outfit_200.ttf");
        fLight = font("outfit_300.ttf");
        fMedium = font("outfit_500.ttf");
        fSemi = font("outfit_600.ttf");

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setClipChildren(false);
        wall = new ImageView(this);
        wall.setScaleType(ImageView.ScaleType.FIT_XY);
        root.addView(wall, new FrameLayout.LayoutParams(-1, -1));
        scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x8C000000, 0x14000000, 0x00000000, 0x33000000, 0xA6000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        content = new FrameLayout(this);
        content.setClipChildren(false);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        toast = text(fMedium, 13, 0xFFFFFFFF);
        toast.setPadding(px(18), px(9), px(18), px(9));
        toast.setAlpha(0f);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        tl.bottomMargin = px(30);
        root.addView(toast, tl);
        toast.setBackground(new Frost(bd, look, toast, px(20), true));
        setContentView(root);
        root.setFocusable(true);
        root.requestFocus();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(pkgReceiver, pf);
        IntentFilter tf = new IntentFilter();
        tf.addAction(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, tf);

        loadWallpaper();
        rebuild();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateClock();
        if (cfg.stamp() != cfgStamp) { loadWallpaper(); rebuild(); }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        closePanel();
        if (!dockTiles.isEmpty()) setFocus(DOCK, dockIdx, 0, true);
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) { }
        try { unregisterReceiver(timeReceiver); } catch (Exception ignored) { }
        bg.shutdownNow();
        super.onDestroy();
    }

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { ui.removeCallbacks(reload); ui.postDelayed(reload, 800); }
    };
    private final Runnable reload = new Runnable() { @Override public void run() { rebuild(); } };
    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); }
    };

    private Typeface font(String n) {
        try { return Typeface.createFromAsset(getAssets(), "fonts/" + n); } catch (Exception e) { return Typeface.DEFAULT; }
    }

    private int px(float units) { return Math.round(units * U); }

    private TextView text(Typeface tf, float size, int color) {
        TextView t = new TextView(this);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * U);
        t.setIncludeFontPadding(false);
        return t;
    }

    private void place(View v, float x, float y, float w, float h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                w < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : px(w),
                h < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : px(h));
        lp.leftMargin = px(x);
        lp.topMargin = px(y);
        content.addView(v, lp);
    }

    private Frost frost(View host, float radiusUnits, boolean frosted) {
        Frost f = new Frost(bd, look, host, px(radiusUnits), frosted);
        host.setBackground(f);
        if (frosted) frostHosts.add(host);
        return f;
    }

    private void invalidateGlass() {
        for (View v : frostHosts) v.invalidate();
        if (panel != null) panel.box.invalidate();
        toast.invalidate();
    }

    // ================================================================ papel de parede
    private void loadWallpaper() {
        bd.load(this, look, cfg.wallpaper(), screenW, screenH);
        wall.setImageBitmap(bd.full);
        scrim.setVisibility(bd.full == null ? View.GONE : View.VISIBLE);
    }

    // ================================================================ montagem
    private void rebuild() {
        cfgStamp = cfg.stamp();
        String keep = currentKey();
        if (keep == null) keep = cfg.lastSelected();
        closePanel();
        content.removeAllViews();
        content.setTranslationY(0);
        frostHosts.clear();
        dockTiles.clear();
        gridTiles.clear();
        chipViews.clear();
        loadApps();

        cols = look.slots;
        float W = 960f, H = screenH / U;
        pad = cols <= 4 ? 18f : 14f;
        gap = cols <= 4 ? 18f : (cols <= 5 ? 16f : 12f);
        shelfX = M;
        float shelfW = W - 2 * M;
        tileW = (shelfW - 2 * pad - (cols - 1) * gap) / cols;
        tileH = tileW * 9f / 16f;
        labelH = 24f;
        shelfH = pad + tileH + labelH + 2f;
        shelfY = H - 22f - shelfH;
        chipsY = shelfY + shelfH + 26f;
        gridY0 = chipsY + 30f + 24f;
        rowPitch = tileH + labelH + 16f;

        // ---- cabecalho
        greeting = text(fLight, 15, 0xE6FFFFFF);
        clock = text(fThin, 66, 0xFFFFFFFF);
        clock.setLetterSpacing(-0.01f);
        clock.setShadowLayer(px(10), 0, 0, 0x40000000);
        date = text(fLight, 15, 0xCCFFFFFF);
        place(greeting, M + 2, 32, -2, -2);
        place(clock, M - 2, 50, -2, -2);
        place(date, M + 2, 128, -2, -2);
        updateClock();

        // ---- pilulas de vidro
        pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Entradas", "Wi-Fi", "Aparência", "Configurações"};
        int[] kinds = {Icons.INPUT, Icons.WIFI, Icons.DROP, Icons.GEAR};
        for (int i = 0; i < 4; i++) {
            LinearLayout p = new LinearLayout(this);
            p.setOrientation(LinearLayout.HORIZONTAL);
            p.setGravity(Gravity.CENTER_VERTICAL);
            p.setPadding(px(13), 0, px(15), 0);
            ImageView ic = new ImageView(this);
            pillIcons[i] = new Icons(kinds[i], 0xF2FFFFFF);
            ic.setImageDrawable(pillIcons[i]);
            p.addView(ic, new LinearLayout.LayoutParams(px(15), px(15)));
            TextView t = text(fMedium, 12.5f, 0xF2FFFFFF);
            t.setText(names[i]);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
            tlp.leftMargin = px(7);
            p.addView(t, tlp);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, px(34));
            if (i > 0) lp.leftMargin = px(10);
            pills.addView(p, lp);
            pillViews[i] = p;
            pillText[i] = t;
            frost(p, 17, true);
        }
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        pl.topMargin = px(36);
        pl.rightMargin = px(M);
        content.addView(pills, pl);

        // ---- luz ambiente + titulo
        bloom = new View(this);
        place(bloom, W / 2 - 300, shelfY - 46 - 300, 600, 600);
        bloom.setScaleY(0.40f);
        title = text(fSemi, 38, 0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setShadowLayer(px(18), 0, px(2), 0x73000000);
        place(title, M, shelfY - 78, W - 2 * M, 60);

        // ---- prateleira de vidro (favoritos)
        shelf = new View(this);
        place(shelf, shelfX, shelfY, shelfW, shelfH);
        frost(shelf, 26, true);
        for (int i = 0; i < dockApps.size(); i++)
            dockTiles.add(new Tile(dockApps.get(i), shelfX + pad + i * (tileW + gap), shelfY + pad));
        if (dockApps.size() < cols)
            dockTiles.add(new Tile(null, shelfX + pad + dockApps.size() * (tileW + gap), shelfY + pad));

        // ---- categorias (somente as que tem apps)
        chipsRow = new LinearLayout(this);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipCats.clear();
        for (int c = 0; c < CATS.length; c++) {
            if (c > 0 && countCat(c) == 0) continue;
            chipCats.add(c);
            TextView t = text(fMedium, 12.5f, 0xF2FFFFFF);
            t.setText(CATS[c]);
            t.setGravity(Gravity.CENTER);
            t.setPadding(px(16), 0, px(16), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, px(30));
            if (!chipViews.isEmpty()) lp.leftMargin = px(8);
            chipsRow.addView(t, lp);
            frost(t, 15, true);
            chipViews.add(t);
        }
        chipIdx = Math.max(0, Math.min(chipIdx, chipViews.size() - 1));
        // altura fixa: fora da area inicial da tela, WRAP_CONTENT seria medido com altura 0
        place(chipsRow, shelfX + pad - 2, chipsY, -2, 34);

        // ---- lente de vidro que desliza entre os blocos
        lens = new View(this);
        Frost lf = frost(lens, 11, false);
        lf.glow = 1f;
        place(lens, 0, 0, tileW, tileH);
        lens.setScaleX(1.1f);
        lens.setScaleY(1.1f);
        lens.setTranslationZ(px(14));
        lens.setAlpha(0f);

        buildGrid();
        styleChips();

        if (!restoreFocus(keep)) {
            if (!dockTiles.isEmpty()) setFocus(DOCK, 0, 0, false);
            else setFocus(TOP, 0, 0, false);
        }
    }

    private void buildGrid() {
        for (Tile t : gridTiles) t.remove();
        gridTiles.clear();
        int cat = chipCats.isEmpty() ? 0 : chipCats.get(Math.max(0, chipIdx));
        int k = 0;
        for (AppEntry a : gridApps) {
            if (cat != 0 && a.category != cat) continue;
            int r = k / cols, c = k % cols;
            gridTiles.add(new Tile(a, shelfX + pad + c * (tileW + gap), gridY0 + r * rowPitch));
            k++;
        }
        lens.bringToFront();
    }

    // ================================================================ blocos
    private final class Tile {
        final AppEntry app;    // null = "Adicionar"
        final FrameLayout card;
        final View rim, glow;
        final TextView label;
        final Frost rimFrost;
        final float x, y;

        Tile(AppEntry a, float x, float y) {
            app = a;
            this.x = x;
            this.y = y;
            glow = new View(HomeActivity.this);
            place(glow, x - tileW * 0.35f, y - tileH * 0.35f, tileW * 1.7f, tileH * 1.7f);
            glow.setAlpha(0f);
            card = new FrameLayout(HomeActivity.this);
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(11));
                }
            });
            card.setClipToOutline(true);
            place(card, x, y, tileW, tileH);
            rim = new View(HomeActivity.this);
            rimFrost = new Frost(bd, look, rim, px(11), false);
            rim.setBackground(rimFrost);
            label = text(fMedium, 12f, 0xFFFFFFFF);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setShadowLayer(px(6), 0, 0, 0x80000000);
            label.setAlpha(0f);
            place(label, x - tileW * 0.15f, y + tileH * 1.06f + 5, tileW * 1.3f, labelH - 4);
            if (a == null) {
                label.setText("Adicionar");
                card.setBackground(new Frost(bd, look, card, px(11), true));
                frostHosts.add(card);
                ImageView plus = new ImageView(HomeActivity.this);
                plus.setImageDrawable(new Icons(Icons.PLUS, 0xCCFFFFFF));
                int s = Math.round(tileH * 0.34f * U);
                card.addView(plus, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
                card.addView(rim, new FrameLayout.LayoutParams(-1, -1));
            } else {
                label.setText(a.label);
                card.setBackgroundColor(0xFF1C1C1E);
                bindArt();
            }
        }

        void remove() {
            content.removeView(glow);
            content.removeView(card);
            content.removeView(label);
            frostHosts.remove(card);
        }

        void bindArt() {
            Drawable[] art = artCache.get(app.pkg);
            if (art != null) { show(art); return; }
            bg.execute(new Runnable() {
                @Override public void run() {
                    final Drawable[] d = loadArt(app);
                    ui.post(new Runnable() {
                        @Override public void run() {
                            show(d);
                            if (focusedTile() == Tile.this) updateTitle(app);
                        }
                    });
                }
            });
        }

        void show(Drawable[] art) {
            card.removeAllViews();
            app.banner = art[0];
            app.icon = art[1];
            Integer col = colorCache.get(app.pkg);
            if (col != null) app.color = col;
            ImageView iv = new ImageView(HomeActivity.this);
            if (art[0] != null) {
                iv.setScaleType(ImageView.ScaleType.FIT_XY);
                iv.setImageDrawable(art[0]);
                card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            } else {
                // sem banner: icone sobre vidro tingido com a cor do app
                card.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        new int[]{shade(app.color, 0.62f), shade(app.color, 0.32f)}));
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setImageDrawable(art[1]);
                int s = Math.round(tileH * 0.56f * U);
                card.addView(iv, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
            }
            card.addView(rim, new FrameLayout.LayoutParams(-1, -1));   // reflexo + borda de vidro
            GradientDrawable g = new GradientDrawable();
            g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            g.setGradientRadius(tileW * 0.85f * U);
            g.setColors(new int[]{(app.color & 0xFFFFFF) | 0x8C000000, (app.color & 0xFFFFFF) | 0x26000000, 0});
            glow.setBackground(g);
        }

        void setFocused(boolean f) {
            float s = f ? 1.1f : 1f;
            long d = look.dur(f ? 320 : 200);
            card.animate().scaleX(s).scaleY(s).translationZ(f ? px(10) : 0).setDuration(d)
                    .setInterpolator(f ? new OvershootInterpolator(1.4f) : new DecelerateInterpolator()).start();
            label.animate().alpha(f ? 1f : 0f).translationY(f ? 0 : -px(4)).setDuration(look.dur(f ? 220 : 120)).start();
            glow.animate().alpha(f ? 1f : 0f).scaleX(f ? 1f : 0.8f).scaleY(f ? 1f : 0.8f).setDuration(look.dur(360)).start();
            rimFrost.glow = f ? 1f : 0f;
            rim.invalidate();
        }
    }

    private Drawable[] loadArt(AppEntry a) {
        PackageManager pm = getPackageManager();
        Drawable banner = null, icon = null;
        try {
            ActivityInfo ai = pm.getActivityInfo(a.component, 0);
            banner = ai.loadBanner(pm);
            if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
            icon = ai.loadIcon(pm);
        } catch (Exception ignored) { }
        if (icon == null) icon = pm.getDefaultActivityIcon();
        int c = dominant(banner != null ? banner : icon);
        synchronized (colorCache) { colorCache.put(a.pkg, c); }
        Drawable[] r = {banner, icon};
        synchronized (artCache) { artCache.put(a.pkg, r); }
        return r;
    }

    private static int dominant(Drawable d) {
        try {
            Bitmap b = Bitmap.createBitmap(24, 14, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, 24, 14);
            d.draw(new Canvas(b));
            double r = 0, g = 0, bl = 0, ws = 0;
            float[] hsv = new float[3];
            for (int y = 0; y < 14; y++)
                for (int x = 0; x < 24; x++) {
                    int p = b.getPixel(x, y);
                    if (Color.alpha(p) < 128) continue;
                    Color.colorToHSV(p, hsv);
                    double w = 0.06 + hsv[1] * hsv[2] * 1.5;
                    r += Color.red(p) * w; g += Color.green(p) * w; bl += Color.blue(p) * w; ws += w;
                }
            b.recycle();
            if (ws == 0) return 0xFF6E7380;
            int col = Color.rgb((int) (r / ws), (int) (g / ws), (int) (bl / ws));
            Color.colorToHSV(col, hsv);
            hsv[1] = Math.min(1f, hsv[1] * 1.3f);
            hsv[2] = Math.max(0.6f, Math.min(1f, hsv[2] * 1.2f));
            return Color.HSVToColor(hsv);
        } catch (Exception e) {
            return 0xFF6E7380;
        }
    }

    private static int shade(int c, float f) {
        return Color.rgb((int) (Color.red(c) * f), (int) (Color.green(c) * f), (int) (Color.blue(c) * f));
    }

    // ================================================================ apps e categorias
    private void loadApps() {
        PackageManager pm = getPackageManager();
        Map<String, AppEntry> byPkg = new HashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), PackageManager.GET_META_DATA)) add(byPkg, ri, true, pm);
        for (ResolveInfo ri : pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.GET_META_DATA)) add(byPkg, ri, false, pm);
        byPkg.remove(getPackageName());
        allApps.clear();
        allApps.addAll(byPkg.values());

        List<String> hidden = cfg.hidden();
        dockApps.clear();
        gridApps.clear();
        for (String p : cfg.dock()) {
            AppEntry a = byPkg.get(p);
            if (a != null && !dockApps.contains(a) && dockApps.size() < look.slots) dockApps.add(a);
        }
        for (AppEntry a : allApps)
            if (!dockApps.contains(a) && !hidden.contains(a.pkg)) gridApps.add(a);
        Collections.sort(gridApps, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry x, AppEntry y) { return x.label.compareToIgnoreCase(y.label); }
        });
    }

    private static void add(Map<String, AppEntry> m, ResolveInfo ri, boolean leanback, PackageManager pm) {
        ActivityInfo ai = ri.activityInfo;
        if (ai == null || m.containsKey(ai.packageName)) return;
        // atalhos de botao do controle (ex.: Globoplay -> YouTube) nao aparecem na lista
        if (ai.applicationInfo.metaData != null && ai.applicationInfo.metaData.getBoolean("com.breno.tvhome.HIDE")) return;
        // apps sem versao para TV so entram se foram instalados pelo usuario
        if (!leanback && (ai.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) return;
        CharSequence lbl = ri.loadLabel(pm);
        AppEntry e = new AppEntry(ai.packageName, new ComponentName(ai.packageName, ai.name),
                leanback, lbl == null ? ai.packageName : lbl.toString().trim());
        e.category = categoryOf(ai.applicationInfo);
        m.put(ai.packageName, e);
    }

    /** Categoria pela API do sistema (ApplicationInfo.category / FLAG_IS_GAME), com ajustes conhecidos. */
    private static int categoryOf(ApplicationInfo ai) {
        String p = ai.packageName;
        if (p.contains("spotify") || p.contains("music") || p.contains("deezer") || p.contains("audio")) return 2;
        if (p.contains("snes") || p.contains("games") || p.contains("emu")) return 3;
        if (p.contains("netflix") || p.contains("youtube") || p.contains("pluto") || p.contains("nba")
                || p.contains("video") || p.equals("com.tcl.tv") || p.contains("globo") || p.contains("disney")
                || p.contains("hbo") || p.contains("primevideo") || p.contains("amazonvideo")) return 1;
        if ((ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0) return 3;
        switch (ai.category) {
            case ApplicationInfo.CATEGORY_GAME: return 3;
            case ApplicationInfo.CATEGORY_AUDIO: return 2;
            case ApplicationInfo.CATEGORY_VIDEO: return 1;
            default: return 4;
        }
    }

    private int countCat(int c) {
        int n = 0;
        for (AppEntry a : gridApps) if (a.category == c) n++;
        return n;
    }

    // ================================================================ relogio
    private void updateClock() {
        if (clock == null) return;
        Date now = new Date();
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String hi = h >= 5 && h < 12 ? "Bom dia" : (h >= 12 && h < 18 ? "Boa tarde" : "Boa noite");
        String name = cfg.setting("name", "");
        greeting.setText(name.length() > 0 ? hi + ", " + name : hi);
        clock.setText(new SimpleDateFormat("HH:mm", PT).format(now));
        String d = new SimpleDateFormat("EEEE, d 'de' MMMM", PT).format(now);
        date.setText(d.substring(0, 1).toUpperCase(PT) + d.substring(1));
    }

    // ================================================================ foco e navegacao
    private Tile tileAt(int a, int i, int r) {
        if (a == DOCK) return i >= 0 && i < dockTiles.size() ? dockTiles.get(i) : null;
        if (a == GRID) {
            int k = r * cols + i;
            return i >= 0 && i < cols && k >= 0 && k < gridTiles.size() ? gridTiles.get(k) : null;
        }
        return null;
    }

    private Tile focusedTile() {
        if (area == DOCK) return tileAt(DOCK, dockIdx, 0);
        if (area == GRID) return tileAt(GRID, gridCol, gridRow);
        return null;
    }

    private int gridRows() { return (gridTiles.size() + cols - 1) / cols; }

    private int rowLen(int r) { return Math.max(0, Math.min(cols, gridTiles.size() - r * cols)); }

    private String currentKey() {
        Tile t = focusedTile();
        return t == null || t.app == null ? null : t.app.pkg;
    }

    private boolean restoreFocus(String pkg) {
        if (pkg == null) return false;
        for (int i = 0; i < dockTiles.size(); i++) {
            AppEntry a = dockTiles.get(i).app;
            if (a != null && a.pkg.equals(pkg)) { setFocus(DOCK, i, 0, false); return true; }
        }
        for (int i = 0; i < gridTiles.size(); i++)
            if (gridTiles.get(i).app.pkg.equals(pkg)) { setFocus(GRID, i % cols, i / cols, false); return true; }
        return false;
    }

    private void setFocus(int a, int idx, int row, boolean animate) {
        Tile old = focusedTile();
        if (area == TOP) stylePill(topIdx, false);
        area = a;
        if (a == TOP) topIdx = idx;
        else if (a == DOCK) dockIdx = idx;
        else if (a == CHIPS) chipIdx = idx;
        else { gridCol = idx; gridRow = row; }
        Tile now = focusedTile();
        if (old != null && old != now) old.setFocused(false);
        styleChips();

        if (a == TOP) stylePill(topIdx, true);
        if (now != null) {
            now.setFocused(true);
            float tx = now.x * U, ty = now.y * U;
            if (animate && lens.getAlpha() > 0.5f && look.animFactor() > 0) {
                lens.animate().translationX(tx).translationY(ty).alpha(1f).setDuration(look.dur(340))
                        .setInterpolator(new OvershootInterpolator(0.9f)).start();
            } else {
                lens.animate().cancel();
                lens.setTranslationX(tx);
                lens.setTranslationY(ty);
                lens.animate().alpha(1f).setDuration(look.dur(160)).start();
            }
            if (now.app != null) {
                updateTitle(now.app);
                cfg.setLastSelected(now.app.pkg);
            } else {
                title.setText("Adicionar favorito");
            }
        } else {
            lens.animate().alpha(0f).setDuration(look.dur(140)).start();
        }

        // rolagem: favoritos embaixo; categorias/grade sobem uma linha por vez
        float target = 0;
        if (a == CHIPS || a == GRID) {
            target = -(shelfY - 28f);
            if (a == GRID) target -= Math.max(0, gridRow - 1) * rowPitch;
        }
        target *= U;
        if (animate && look.animFactor() > 0) {
            content.animate().translationY(target).setDuration(look.dur(420))
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .setUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                        @Override public void onAnimationUpdate(android.animation.ValueAnimator va) { invalidateGlass(); }
                    }).start();
        } else {
            content.setTranslationY(target);
            ui.post(new Runnable() { @Override public void run() { invalidateGlass(); } });
        }
        boolean low = a == CHIPS || a == GRID;
        for (View v : new View[]{greeting, clock, date, pills})
            v.animate().alpha(low ? 0f : 1f).setDuration(look.dur(220)).start();
        title.animate().alpha(low ? 0f : 1f).setDuration(look.dur(200)).start();
        bloom.animate().alpha(low ? 0f : 1f).setDuration(look.dur(260)).start();
    }

    private void stylePill(int i, boolean focused) {
        Frost f = (Frost) pillViews[i].getBackground();
        f.glow = focused ? 1f : 0f;
        f.tintScale = focused ? 3.2f : 1f;
        pillText[i].setTextColor(focused ? 0xFF101012 : 0xF2FFFFFF);
        pillIcons[i].setColor(focused ? 0xFF101012 : 0xF2FFFFFF);
        pillViews[i].invalidate();
        pillViews[i].animate().scaleX(focused ? 1.08f : 1f).scaleY(focused ? 1.08f : 1f)
                .setDuration(look.dur(240)).setInterpolator(new OvershootInterpolator(1.6f)).start();
    }

    private void styleChips() {
        for (int i = 0; i < chipViews.size(); i++) {
            TextView t = chipViews.get(i);
            Frost f = (Frost) t.getBackground();
            boolean sel = i == chipIdx;
            boolean foc = sel && area == CHIPS;
            f.glow = sel ? 1f : 0f;
            f.tintScale = foc ? 3.2f : (sel ? 1.8f : 1f);
            t.setTextColor(foc ? 0xFF101012 : 0xF2FFFFFF);
            t.invalidate();
            t.animate().scaleX(foc ? 1.08f : 1f).scaleY(foc ? 1.08f : 1f).setDuration(look.dur(220)).start();
        }
    }

    private void updateTitle(AppEntry a) {
        title.setText(a.label);
        if (look.animFactor() > 0) {
            title.setAlpha(0.35f);
            title.setTranslationY(px(6));
            title.animate().alpha(area == GRID || area == CHIPS ? 0f : 1f).translationY(0)
                    .setDuration(look.dur(260)).start();
        }
        int c = a.color;
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(px(299));
        g.setColors(new int[]{(c & 0xFFFFFF) | 0x99000000, (c & 0xFFFFFF) | 0x33000000, 0});
        bloom.setBackground(g);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean center = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER || k == KeyEvent.KEYCODE_BUTTON_A;
        boolean nav = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (!center && !nav && k != KeyEvent.KEYCODE_MENU && k != KeyEvent.KEYCODE_BACK)
            return super.dispatchKeyEvent(e);

        if (k == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) back();
            return true;
        }
        if (center) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) centerLong = false;
                else if (!centerLong && e.getRepeatCount() >= 6) {
                    centerLong = true;
                    if (panel == null) openTileOptions();
                }
            } else if (e.getAction() == KeyEvent.ACTION_UP && !centerLong) {
                if (panel != null) panel.activate(); else activate();
            }
            return true;
        }
        if (k == KeyEvent.KEYCODE_MENU) {
            if (e.getAction() == KeyEvent.ACTION_UP) { if (panel != null) closePanel(); else openTileOptions(); }
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (panel != null) { panel.key(k); return true; }
        move(k);
        return true;
    }

    private void back() {
        if (panel != null) { closePanel(); return; }
        if (area == GRID) { setFocus(CHIPS, chipIdx, 0, true); return; }
        if (area == CHIPS || area == TOP) {
            if (!dockTiles.isEmpty()) setFocus(DOCK, Math.min(dockIdx, dockTiles.size() - 1), 0, true);
            return;
        }
        if (!homeMode) finish();     // prototipo: Voltar nos favoritos sai do app
    }

    private void move(int k) {
        boolean L = k == KeyEvent.KEYCODE_DPAD_LEFT, R = k == KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean Up = k == KeyEvent.KEYCODE_DPAD_UP, Dn = k == KeyEvent.KEYCODE_DPAD_DOWN;
        switch (area) {
            case TOP:
                if (L && topIdx > 0) setFocus(TOP, topIdx - 1, 0, true);
                else if (R && topIdx < 3) setFocus(TOP, topIdx + 1, 0, true);
                else if (Dn && !dockTiles.isEmpty()) setFocus(DOCK, dockIdx, 0, true);
                break;   // extremidades da barra nao atravessam
            case DOCK:
                if (L && dockIdx > 0) setFocus(DOCK, dockIdx - 1, 0, true);
                else if (R && dockIdx < dockTiles.size() - 1) setFocus(DOCK, dockIdx + 1, 0, true);
                else if (Up) setFocus(TOP, topIdx, 0, true);
                else if (Dn && !chipViews.isEmpty()) setFocus(CHIPS, chipIdx, 0, true);
                break;
            case CHIPS:
                if (L && chipIdx > 0) { chipIdx--; buildGrid(); setFocus(CHIPS, chipIdx, 0, true); }
                else if (R && chipIdx < chipViews.size() - 1) { chipIdx++; buildGrid(); setFocus(CHIPS, chipIdx, 0, true); }
                else if (Up) setFocus(DOCK, dockIdx, 0, true);
                else if (Dn && !gridTiles.isEmpty()) setFocus(GRID, Math.min(gridCol, rowLen(0) - 1), 0, true);
                break;
            case GRID:
                if (L && gridCol > 0) setFocus(GRID, gridCol - 1, gridRow, true);
                else if (R && gridCol < rowLen(gridRow) - 1) setFocus(GRID, gridCol + 1, gridRow, true);
                else if (Dn && gridRow < gridRows() - 1)
                    setFocus(GRID, Math.min(gridCol, rowLen(gridRow + 1) - 1), gridRow + 1, true);
                else if (Up) {
                    if (gridRow > 0) setFocus(GRID, gridCol, gridRow - 1, true);
                    else setFocus(CHIPS, chipIdx, 0, true);
                }
                break;
        }
    }

    private void activate() {
        if (area == TOP) {
            if (topIdx == 0) openInputs();
            else if (topIdx == 1) start(new Intent(Settings.ACTION_WIFI_SETTINGS));
            else if (topIdx == 2) openAppearance();
            else start(new Intent(Settings.ACTION_SETTINGS));
            return;
        }
        if (area == CHIPS) { if (!gridTiles.isEmpty()) setFocus(GRID, 0, 0, true); return; }
        Tile t = focusedTile();
        if (t == null) return;
        if (t.app == null) { showToast("Segure OK sobre um app da lista para fixá-lo aqui"); return; }
        start(t.app.launchIntent());
    }

    private void start(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            if (i.getComponent() != null) {
                String p = i.getComponent().getPackageName();
                Intent alt = getPackageManager().getLeanbackLaunchIntentForPackage(p);
                if (alt == null) alt = getPackageManager().getLaunchIntentForPackage(p);
                if (alt != null) try { startActivity(alt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
            }
        }
    }

    private void showToast(String s) {
        toast.setText(s);
        toast.invalidate();
        toast.animate().cancel();
        toast.setTranslationY(px(10));
        toast.animate().alpha(1f).translationY(0).setStartDelay(0).setDuration(look.dur(220)).withEndAction(new Runnable() {
            @Override public void run() {
                toast.animate().alpha(0f).setStartDelay(1800).setDuration(look.dur(300)).withEndAction(null).start();
            }
        }).start();
    }

    // ================================================================ paineis de vidro
    private interface Action { void run(); }

    private interface Stepper { void step(int dir); }

    /** Painel de vidro com linhas; linhas com Stepper mudam valor com esquerda/direita. */
    private final class Panel {
        final LinearLayout box;
        final List<LinearLayout> rows = new ArrayList<>();
        final List<TextView> labels = new ArrayList<>();
        final List<TextView> values = new ArrayList<>();
        final List<Action> actions = new ArrayList<>();
        final List<Stepper> steppers = new ArrayList<>();
        int sel = 0;
        final float rowH;

        Panel(float rowH, String heading) {
            this.rowH = rowH;
            box = new LinearLayout(HomeActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(px(8), px(heading == null ? 8 : 16), px(8), px(8));
            Frost f = new Frost(bd, look, box, px(22), true);
            f.tintScale = 1.3f;
            box.setBackground(f);
            box.setTranslationZ(px(30));
            if (heading != null) {
                TextView h = text(fSemi, 19, 0xFFFFFFFF);
                h.setText(heading);
                h.setPadding(px(14), 0, px(14), px(12));
                box.addView(h);
            }
        }

        void add(String label, String value, Action a, Stepper s) {
            LinearLayout row = new LinearLayout(HomeActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(px(14), 0, px(14), 0);
            TextView l = text(fMedium, 13.5f, 0xFFFFFFFF);
            l.setText(label);
            row.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView v = text(fLight, 13.5f, 0xD9FFFFFF);
            row.addView(v, new LinearLayout.LayoutParams(-2, -2));
            box.addView(row, new LinearLayout.LayoutParams(-1, px(rowH)));
            rows.add(row); labels.add(l); values.add(v); actions.add(a); steppers.add(s);
            if (value != null) setValue(rows.size() - 1, value);
        }

        void setValue(int i, String v) { values.get(i).setText(steppers.get(i) != null ? "‹  " + v + "  ›" : v); }

        void select(int i) {
            sel = Math.max(0, Math.min(rows.size() - 1, i));
            for (int j = 0; j < rows.size(); j++) {
                boolean s = j == sel;
                if (s) {
                    GradientDrawable g = new GradientDrawable();
                    g.setCornerRadius(px(12));
                    g.setColor(0xF2FFFFFF);
                    rows.get(j).setBackground(g);
                } else rows.get(j).setBackground(null);
                labels.get(j).setTextColor(s ? 0xFF101012 : 0xFFFFFFFF);
                values.get(j).setTextColor(s ? 0xCC101012 : 0xD9FFFFFF);
            }
        }

        void key(int k) {
            if (k == KeyEvent.KEYCODE_DPAD_UP && sel > 0) select(sel - 1);
            else if (k == KeyEvent.KEYCODE_DPAD_DOWN && sel < rows.size() - 1) select(sel + 1);
            else if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                Stepper s = steppers.get(sel);
                if (s != null) s.step(k == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                else closePanel();
            }
        }

        void activate() {
            Action a = actions.get(sel);
            if (a != null) a.run();
            else if (steppers.get(sel) != null) steppers.get(sel).step(1);
        }
    }

    private void showPanel(Panel p, int gravity, int x, int y, int w) {
        closePanel();
        if (p.rows.isEmpty()) return;
        panel = p;
        boolean right = (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.RIGHT;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, -2, gravity);
        lp.leftMargin = right ? 0 : x;
        lp.rightMargin = right ? x : 0;
        lp.topMargin = y;
        root.addView(p.box, lp);
        p.select(p.sel);
        if (look.animFactor() > 0) {
            p.box.setAlpha(0f);
            p.box.setScaleX(0.96f);
            p.box.setScaleY(0.96f);
            p.box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(look.dur(260))
                    .setInterpolator(new OvershootInterpolator(1.1f)).start();
        }
    }

    private void closePanel() {
        if (panel != null) {
            root.removeView(panel.box);
            panel = null;
        }
    }

    /** Entradas fisicas pela TV Input Framework (HDMI, AV, sintonizador). */
    private void openInputs() {
        Panel m = new Panel(34, null);
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            List<TvInputInfo> list = new ArrayList<>(tim.getTvInputList());
            Collections.sort(list, new Comparator<TvInputInfo>() {
                @Override public int compare(TvInputInfo a, TvInputInfo b) { return order(a) - order(b); }
            });
            boolean hasTuner = false;
            for (final TvInputInfo in : list) {
                if (in.getParentId() != null || in.isHidden(this) || in.getType() == TvInputInfo.TYPE_OTHER) continue;
                final boolean tuner = in.getType() == TvInputInfo.TYPE_TUNER;
                if (tuner && (hasTuner || !isSystem(in.getServiceInfo().packageName))) continue;
                if (tuner) hasTuner = true;
                CharSequence l = in.loadCustomLabel(this);
                if (l == null || l.length() == 0) l = in.loadLabel(this);
                m.add(tuner ? "TV ao vivo" : l.toString(), null, new Action() {
                    @Override public void run() { closePanel(); tuneTo(in, tuner); }
                }, null);
            }
        } catch (Exception ignored) { }
        if (m.rows.isEmpty()) m.add("TV ao vivo", null, new Action() {
            @Override public void run() { closePanel(); openLiveTv(); } }, null);
        int x = pills.getLeft() + pillViews[0].getLeft();
        int y = Math.round(pills.getBottom() + content.getTranslationY()) + px(10);
        showPanel(m, Gravity.TOP | Gravity.LEFT, x, y, px(210));
    }

    private boolean isSystem(String pkg) {
        try { return (getPackageManager().getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_SYSTEM) != 0; }
        catch (Exception e) { return false; }
    }

    private static int order(TvInputInfo i) {
        switch (i.getType()) {
            case TvInputInfo.TYPE_HDMI: return 0;
            case TvInputInfo.TYPE_COMPONENT: return 1;
            case TvInputInfo.TYPE_COMPOSITE: return 2;
            case TvInputInfo.TYPE_TUNER: return 3;
            default: return 4;
        }
    }

    private void tuneTo(TvInputInfo in, boolean tuner) {
        Uri uri = tuner ? TvContract.buildChannelsUriForInput(in.getId())
                : TvContract.buildChannelUriForPassthroughInput(in.getId());
        Intent i = new Intent(Intent.ACTION_VIEW, uri);
        i.setPackage(cfg.setting("livetv", "com.tcl.tv"));
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { if (tuner) openLiveTv(); }
    }

    private void openLiveTv() {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(cfg.setting("livetv", "com.tcl.tv"));
        if (i == null) i = new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI);
        start(i);
    }

    /** Menu proprio "Aparencia": papel de parede, transparencia, efeitos, animacoes, modo do vidro, favoritos. */
    private void openAppearance() {
        final Panel p = new Panel(40, "Aparência");
        p.add("Papel de parede", Look.WALLPAPER_NAMES[look.wallpaper], null, new Stepper() {
            @Override public void step(int d) {
                int n = Look.WALLPAPERS.length;
                int w = look.wallpaper;
                do { w = (w + d + n) % n; } while (Look.WALLPAPERS[w].equals("personalizado") && cfg.wallpaper() == null);
                look.wallpaper = w;
                look.save();
                loadWallpaper();
                p.setValue(0, Look.WALLPAPER_NAMES[w]);
                invalidateAll();
            }
        });
        p.add("Transparência", look.transparency + "%", null, new Stepper() {
            @Override public void step(int d) {
                look.transparency = Look.clamp(look.transparency + d * 10, 0, 100);
                look.save();
                p.setValue(1, look.transparency + "%");
                invalidateAll();
            }
        });
        p.add("Intensidade dos efeitos", Look.INTENSITY[look.intensity], null, new Stepper() {
            @Override public void step(int d) {
                look.intensity = Look.clamp(look.intensity + d, 0, 2);
                look.save();
                bd.rebuildBlur(look);
                p.setValue(2, Look.INTENSITY[look.intensity]);
                invalidateAll();
            }
        });
        p.add("Animações", Look.ANIM[look.anim], null, new Stepper() {
            @Override public void step(int d) {
                look.anim = Look.clamp(look.anim + d, 0, 2);
                look.save();
                p.setValue(3, Look.ANIM[look.anim]);
            }
        });
        p.add("Modo do vidro", look.mode == 0 ? "Real" : "Leve", null, new Stepper() {
            @Override public void step(int d) {
                look.mode = 1 - look.mode;
                look.save();
                p.setValue(4, look.mode == 0 ? "Real" : "Leve");
                invalidateAll();
            }
        });
        p.add("Espaços nos favoritos", String.valueOf(look.slots), null, new Stepper() {
            @Override public void step(int d) {
                int s = Look.clamp(look.slots + d, 4, 7);
                if (s == look.slots) return;
                look.slots = s;
                look.save();
                p.setValue(5, String.valueOf(s));
                root.removeView(p.box);
                panel = null;
                rebuild();
                reattach(p);
            }
        });
        p.add("Restaurar padrões", null, new Action() {
            @Override public void run() {
                look.reset();
                closePanel();
                loadWallpaper();
                rebuild();
                openAppearance();
            }
        }, null);
        p.add("Fechar", null, new Action() { @Override public void run() { closePanel(); } }, null);
        showPanel(p, Gravity.TOP | Gravity.RIGHT, px(M), px(84), px(330));
    }

    private void reattach(Panel p) {
        panel = p;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(330), -2, Gravity.TOP | Gravity.RIGHT);
        lp.rightMargin = px(M);
        lp.topMargin = px(84);
        root.addView(p.box, lp);
        p.select(p.sel);
    }

    private void invalidateAll() {
        for (View v : pillViews) if (v != null) v.invalidate();
        for (Tile t : dockTiles) { t.rim.invalidate(); t.card.invalidate(); }
        for (Tile t : gridTiles) t.rim.invalidate();
        lens.invalidate();
        invalidateGlass();
    }

    /** Segurar OK (ou MENU) sobre um bloco: fixar/remover/mover nos favoritos, ocultar, informacoes. */
    private void openTileOptions() {
        final Tile t = focusedTile();
        if (t == null || t.app == null) return;
        final AppEntry a = t.app;
        Panel m = new Panel(34, null);
        final List<String> dock = new ArrayList<>();
        for (AppEntry d : dockApps) dock.add(d.pkg);
        if (dock.contains(a.pkg)) {
            final int i = dock.indexOf(a.pkg);
            if (i > 0) m.add("Mover para a esquerda", null, new Action() { @Override public void run() {
                closePanel(); Collections.swap(dock, i, i - 1); saveDock(dock, a.pkg); } }, null);
            if (i < dock.size() - 1) m.add("Mover para a direita", null, new Action() { @Override public void run() {
                closePanel(); Collections.swap(dock, i, i + 1); saveDock(dock, a.pkg); } }, null);
            m.add("Remover dos favoritos", null, new Action() { @Override public void run() {
                closePanel(); dock.remove(a.pkg); saveDock(dock, a.pkg); } }, null);
        } else {
            if (dock.size() < look.slots) m.add("Fixar nos favoritos", null, new Action() { @Override public void run() {
                closePanel(); dock.add(a.pkg); saveDock(dock, a.pkg); } }, null);
            else m.add("Substituir o último favorito", null, new Action() { @Override public void run() {
                closePanel(); dock.set(dock.size() - 1, a.pkg); saveDock(dock, a.pkg); } }, null);
            m.add("Ocultar da lista", null, new Action() { @Override public void run() {
                closePanel(); List<String> h = cfg.hidden(); h.add(a.pkg); cfg.saveHidden(h); rebuild(); } }, null);
        }
        m.add("Informações do app", null, new Action() { @Override public void run() {
            closePanel(); start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg))); } }, null);
        int x = Math.round((t.x + tileW / 2) * U) - px(115);
        int y = Math.round((t.y + tileH * 0.6f) * U + content.getTranslationY());
        y = Math.min(y, screenH - px(36) * (m.rows.size() + 1));
        showPanel(m, Gravity.TOP | Gravity.LEFT, Math.max(px(20), x), Math.max(px(20), y), px(230));
    }

    private void saveDock(List<String> dock, String focusPkg) {
        cfg.saveDock(dock);
        cfg.setLastSelected(focusPkg);
        rebuild();
        restoreFocus(focusPkg);
    }
}
