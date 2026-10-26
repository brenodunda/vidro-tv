package com.globo.globotv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Abre o YouTube e fecha na hora (sem interface). */
public class OpenYouTube extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String pkg = "com.google.android.youtube.tv";
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try { startActivity(i); } catch (Exception ignored) { }
        }
        finish();
    }
}
