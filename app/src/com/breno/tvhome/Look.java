package com.breno.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/** Ajustes visuais do launcher (menu "Ajustes do Vidro"), persistidos em SharedPreferences. */
final class Look {
    static final String[] WALLPAPERS = {"aurora", "ambar", "lagoa", "grafite", "preto", "personalizado"};
    static final String[] WALLPAPER_NAMES = {"Aurora", "Âmbar", "Lagoa", "Grafite", "Preto", "Personalizado"};
    static final String[] INTENSITY = {"Suave", "Média", "Intensa"};
    static final String[] ANIM = {"Fluidas", "Reduzidas", "Desligadas"};
    static final String[] MODE = {"Vidro real (desfoque)", "Vidro leve (sem desfoque)"};

    int wallpaper = 0;     // indice em WALLPAPERS
    int transparency = 60; // 0 = vidro leitoso, 100 = quase transparente
    int intensity = 1;     // 0..2
    int anim = 0;          // 0 fluidas, 1 reduzidas, 2 desligadas
    int mode = 0;          // 0 real, 1 leve
    int slots = 7;         // espacos no dock (4..7)
    int version = 0;       // muda a cada ajuste: drawables recriam shaders

    private final SharedPreferences p;

    Look(Context c) {
        p = c.getSharedPreferences("look", Context.MODE_PRIVATE);
        wallpaper = clamp(p.getInt("wp", 0), 0, WALLPAPERS.length - 1);
        transparency = clamp(p.getInt("tr", 60), 0, 100);
        intensity = clamp(p.getInt("in", 1), 0, 2);
        anim = clamp(p.getInt("an", 0), 0, 2);
        mode = clamp(p.getInt("mo", 0), 0, 1);
        slots = clamp(p.getInt("sl", 7), 4, 7);
    }

    void save() {
        version++;
        p.edit().putInt("wp", wallpaper).putInt("tr", transparency).putInt("in", intensity)
                .putInt("an", anim).putInt("mo", mode).putInt("sl", slots).apply();
    }

    void reset() {
        wallpaper = 0; transparency = 60; intensity = 1; anim = 0; mode = 0; slots = 7;
        save();
    }

    boolean realGlass() { return mode == 0; }

    /** alfa do "leite" branco sobre o vidro (0..255). */
    int tintAlpha() { return Math.round(10 + (100 - transparency) * 0.95f); }

    int blurRadius() { return intensity == 0 ? 2 : (intensity == 1 ? 3 : 5); }

    float spec() { return intensity == 0 ? 0.55f : (intensity == 1 ? 0.8f : 1f); }

    /** fator de duracao das animacoes. */
    float animFactor() { return anim == 0 ? 1f : (anim == 1 ? 0.45f : 0f); }

    long dur(long ms) { return Math.round(ms * animFactor()); }

    static int clamp(int v, int a, int b) { return Math.max(a, Math.min(b, v)); }
}
