package com.breno.tvhome;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;

/** Um aplicativo exibido no launcher (dock ou grade). */
final class AppEntry {
    final String pkg;
    final ComponentName component;
    final boolean leanback;
    final String label;
    Drawable banner;      // banner 16:9 (TV) ou null
    Drawable icon;        // icone quadrado (fallback)
    int color = 0xFF3A3A3C; // cor dominante (brilho ambiente)
    int category = 4;       // 1 video, 2 musica, 3 jogos, 4 ferramentas

    AppEntry(String pkg, ComponentName component, boolean leanback, String label) {
        this.pkg = pkg;
        this.component = component;
        this.leanback = leanback;
        this.label = label;
    }

    Intent launchIntent() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER);
        i.setComponent(component);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return i;
    }
}
