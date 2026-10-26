package com.breno.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuracao em arquivos de texto simples, editaveis pelo ADB:
 *   /sdcard/Android/data/com.breno.tvhome/files/dock.txt     um pacote por linha, na ordem do dock
 *   /sdcard/Android/data/com.breno.tvhome/files/hidden.txt   pacotes que nao aparecem na grade
 *   /sdcard/Android/data/com.breno.tvhome/files/wallpaper.jpg (ou .png) papel de parede opcional
 *   /sdcard/Android/data/com.breno.tvhome/files/settings.txt chave=valor (name=, columns=)
 */
final class Config {
    static final String[] DEFAULT_DOCK = {
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.spotify.tv.android",
            "com.tvwebbrowser.v22",
    };

    final File dir;
    private final SharedPreferences prefs;

    Config(Context c) {
        File d = c.getExternalFilesDir(null);
        if (d == null) d = c.getFilesDir();
        dir = d;
        prefs = c.getSharedPreferences("state", Context.MODE_PRIVATE);
    }

    List<String> dock() {
        List<String> l = readLines("dock.txt");
        if (l == null) {
            l = new ArrayList<>();
            for (String s : DEFAULT_DOCK) l.add(s);
        }
        return l;
    }

    void saveDock(List<String> pkgs) { writeLines("dock.txt", pkgs); }

    List<String> hidden() {
        List<String> l = readLines("hidden.txt");
        return l == null ? new ArrayList<String>() : l;
    }

    void saveHidden(List<String> pkgs) { writeLines("hidden.txt", pkgs); }

    String setting(String key, String def) {
        List<String> l = readLines("settings.txt");
        if (l != null) for (String s : l) {
            int i = s.indexOf('=');
            if (i > 0 && s.substring(0, i).trim().equals(key)) return s.substring(i + 1).trim();
        }
        return def;
    }

    File wallpaper() {
        for (String n : new String[]{"wallpaper.jpg", "wallpaper.jpeg", "wallpaper.png", "wallpaper.webp"}) {
            File f = new File(dir, n);
            if (f.isFile() && f.length() > 0) return f;
        }
        return null;
    }

    long stamp() {
        long s = 0;
        for (String n : new String[]{"dock.txt", "hidden.txt", "settings.txt", "wallpaper.jpg", "wallpaper.jpeg",
                "wallpaper.png", "wallpaper.webp"}) {
            File f = new File(dir, n);
            if (f.exists()) s = s * 31 + f.lastModified() + f.length();
        }
        return s;
    }

    String lastSelected() { return prefs.getString("last", null); }

    void setLastSelected(String key) { prefs.edit().putString("last", key).apply(); }

    // ------------------------------------------------------------------ arquivos
    private List<String> readLines(String name) {
        File f = new File(dir, name);
        if (!f.isFile()) return null;
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
            String s;
            while ((s = r.readLine()) != null) {
                s = s.trim();
                if (s.length() > 0 && !s.startsWith("#")) out.add(s);
            }
        } catch (Exception e) {
            return null;
        }
        return out;
    }

    private void writeLines(String name, List<String> lines) {
        try {
            dir.mkdirs();
            File tmp = new File(dir, name + ".tmp");
            try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8")) {
                for (String s : lines) w.write(s + "\n");
            }
            tmp.renameTo(new File(dir, name));
        } catch (Exception ignored) {
        }
    }
}
