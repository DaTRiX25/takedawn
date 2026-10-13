package io.github.xenophobentx.takedawn;

import android.content.Context;
import android.os.Bundle;
import android.system.Os;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.libsdl.app.SDLActivity;

/**
 * The game: SDL2's activity with the recompiled title in libmain.so.
 *
 * Before SDL loads the libraries it puts the app's directories into the
 * environment, where the runtime looks for them (the toolkit's
 * src/platform/android_host.h):
 *
 *   RECOMP_ANDROID_DIR  the app's external files directory, the game's
 *                       working directory:
 *                         Burnout 3 Takedown/  the user's disc, unpacked
 *                         takedawn/            saves, settings.toml, crash reports
 *                         enhance.toml         optional
 *                         env.txt              optional, NAME=value lines
 *                         driver/              optional, a Vulkan driver to try (GpuDriver)
 *                         takedawn.log         the log (the one before: .1)
 *   XDG_DATA_HOME       the same directory (the save folder goes under it)
 *   XDG_CACHE_HOME      the app's cache directory (the shader cache)
 *   RECOMP_ANDROID_DRIVER_DIR / _NAME, RECOMP_ANDROID_LIB_DIR
 *                       the Vulkan driver to load in place of the device's
 *
 * env.txt stands in for a shell's environment: every RECOMP_* or BO3_*
 * variable of docs/running.md can be set there for a test.
 */
public class GameActivity extends SDLActivity {
    static final String TAG = "takedawn";
    static final String GAME_DIR = "Burnout 3 Takedown";

    /** The app's files directory: external, so adb and MTP reach it. */
    static File filesDir(Context c) {
        File f = c.getExternalFilesDir(null);
        return f != null ? f : c.getFilesDir();
    }

    @Override
    protected void onCreate(Bundle state) {
        File files = filesDir(this);
        try {
            String dir = files.getAbsolutePath();
            Os.setenv("RECOMP_ANDROID_DIR", dir, true);
            Os.setenv("XDG_DATA_HOME", dir, true);
            Os.setenv("XDG_CACHE_HOME", getCacheDir().getAbsolutePath(), true);
            Os.setenv("HOME", getFilesDir().getAbsolutePath(), true);
            Os.setenv("RECOMP_ANDROID_LIB_DIR", getApplicationInfo().nativeLibraryDir, true);
            java.util.Map<String, String> env = readEnvFile(new File(files, "env.txt"));
            // A test script's variables (tools/android/scene-launch.sh): their
            // own file, used for this start only and removed, so a script
            // that dies leaves nothing behind for the player's next start.
            File scene = new File(files, "scene-env.txt");
            if (scene.isFile()) {
                env.putAll(readEnvFile(scene));
                scene.delete();
                Log.i(TAG, "scene-env.txt: a test script's settings, for this start only");
            }
            // The Vulkan driver (GpuDriver): the device's own, or a replacement
            // the runtime opens through libadrenotools.
            GpuDriver d = GpuDriver.choose(this, env.get("RECOMP_ANDROID_DRIVER"));
            startedWith = GpuDriver.describe(this, GpuDriver.choice(this));
            startedBundled = GpuDriver.BUNDLED.equals(d.name);
            Log.i(TAG, "GPU driver: " + d.what);
            if (d.name != null) {
                Os.setenv("RECOMP_ANDROID_DRIVER_DIR", d.dir, true);
                Os.setenv("RECOMP_ANDROID_DRIVER_NAME", d.name, true);
            } else {
                Os.unsetenv("RECOMP_ANDROID_DRIVER_DIR");
                Os.unsetenv("RECOMP_ANDROID_DRIVER_NAME");
            }
            // What the game says back: the driver it got (for the driver
            // page), and that a driver on trial has shown frames.
            Os.setenv("RECOMP_ANDROID_DRIVER_INFO", GpuDriver.infoFile(this).getAbsolutePath(), true);
            Os.unsetenv("RECOMP_ANDROID_DRIVER_TRIAL");
            Os.unsetenv("RECOMP_ANDROID_DRIVER_FALLBACK_DIR");
            if (d.trial) {
                File trial = GpuDriver.trialFile(this);
                try (java.io.FileOutputStream o = new java.io.FileOutputStream(trial)) {
                    o.write(d.what.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                Os.setenv("RECOMP_ANDROID_DRIVER_TRIAL", trial.getAbsolutePath(), true);
            }
            if (d.name != null && !GpuDriver.BUNDLED.equals(d.name) && GpuDriver.hasBundled(this)) {
                // A package the loader cannot open: the bundled one, at once.
                Os.setenv("RECOMP_ANDROID_DRIVER_FALLBACK_DIR",
                        getApplicationInfo().nativeLibraryDir, true);
                Os.setenv("RECOMP_ANDROID_DRIVER_FALLBACK_NAME", GpuDriver.BUNDLED, true);
            }
            if (d.note != null) {
                android.widget.Toast.makeText(this, d.note, android.widget.Toast.LENGTH_LONG).show();
                Log.i(TAG, "GPU driver: " + d.note);
            }
            for (java.util.Map.Entry<String, String> e : env.entrySet()) {
                Os.setenv(e.getKey(), e.getValue(), true);
                Log.i(TAG, "env.txt: " + e.getKey() + "=" + e.getValue());
            }
        } catch (Exception e) {
            Log.e(TAG, "environment: " + e);
        }
        super.onCreate(state);
    }

    /**
     * The system Back (button or gesture) must not end the game mid-race:
     * SDL's own handling finishes the activity and the present loop then
     * exits. It is passed to the game as SDL's Back key (SDLK_AC_BACK), which
     * the settings overlay takes: closed, it opens the overlay (as F1 or
     * Select+Start do); open, it goes back like B. Leaving the game is the
     * system's app switcher (the overlay has Restart game, no quit row). If
     * the native side is not up (the call fails), the system's own behaviour
     * stays.
     */
    @Override
    public void onBackPressed() {
        try {
            SDLActivity.onNativeKeyDown(android.view.KeyEvent.KEYCODE_BACK);
            SDLActivity.onNativeKeyUp(android.view.KeyEvent.KEYCODE_BACK);
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            Log.w(TAG, "back: " + e);
            super.onBackPressed();
        }
    }

    private String startedWith = "";
    private boolean startedBundled;

    /** Ends this process; a page in another process starts the app again. */
    private void restartApp() {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                startActivity(new android.content.Intent(GameActivity.this, RestartActivity.class));
                new android.os.Handler().postDelayed(new Runnable() {
                    @Override public void run() {
                        finishAndRemoveTask();
                        System.exit(0);
                    }
                }, 400);
            }
        });
    }

    /**
     * The overlay's "Restart game" (the toolkit's recomp_restart, through
     * the window layer's JNI call; BO-124): the app starts again, the same
     * way as after a driver change. Returns 1.
     */
    @SuppressWarnings("unused")
    int restartGame() {
        Log.i(TAG, "restart asked for from the game");
        restartApp();
        return 1;
    }

    /**
     * The start-up warning's "Reset back to Turnip" (the runtime's window
     * when no usable Vulkan driver was found, fb_present_sdl.c; BO-121): on
     * a driver that cannot run the game there is no settings overlay to
     * choose another one from, so the way back is on that window. doIt 0:
     * whether there is something to go back to (the driver in the app, when
     * this start was not on it). doIt 1: choose it and start again.
     */
    @SuppressWarnings("unused")
    int resetGraphicsDriver(int doIt) {
        if (startedBundled || !GpuDriver.hasBundled(this)) {
            return 0;
        }
        if (doIt != 0) {
            GpuDriver.setChoice(this, "bundled");
            Log.i(TAG, "GPU driver: reset to the Mesa Turnip driver in the app; restarting");
            restartApp();
        }
        return 1;
    }

    /**
     * The overlay's driver rows (main.c's driver_row, on the overlay's
     * thread). Row 0: the driver the next start loads; activated, the next
     * of the choices. Row 1: the driver page, on top of the held game.
     * Row 2: only when the choice is no longer what this start loaded: a
     * restart. The driver is loaded before the title starts, so a change
     * here is for the next start, and the rows say so.
     */
    @SuppressWarnings("unused")
    String driverMenu(int row, int activate) {
        String now = GpuDriver.describe(this, GpuDriver.choice(this));
        if (row == 0) {
            if (activate != 0) {
                GpuDriver.setChoice(this, GpuDriver.nextChoice(this));
                now = GpuDriver.describe(this, GpuDriver.choice(this));
            }
            return "Driver at the next start: " + now;
        }
        if (row == 1) {
            if (activate != 0) {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        startActivity(new android.content.Intent(GameActivity.this, DriverActivity.class));
                    }
                });
            }
            return "Driver packages... (pick a .zip; the game waits)";
        }
        if (row == 2) {
            if (now.equals(startedWith)) {
                return "";
            }
            if (activate != 0) {
                restartApp();
            }
            return "Restart now to use it (the race in hand is lost)";
        }
        return "";
    }

    /** NAME=value lines; '#' starts a comment line. */
    private static java.util.Map<String, String> readEnvFile(File f) throws Exception {
        java.util.Map<String, String> env = new java.util.LinkedHashMap<>();
        if (!f.isFile()) {
            return env;
        }
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                int eq = line.indexOf('=');
                if (line.isEmpty() || line.startsWith("#") || eq <= 0) {
                    continue;
                }
                env.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        }
        return env;
    }

    // The whole panel: no status or navigation bar (immersive sticky), into
    // the display cutout. SDL does this itself for a fullscreen window; it is
    // repeated here whenever the window gets the focus back, because a
    // swipe from the edge, a dialog or the volume panel brings the bars back.
    private void immersive() {
        android.view.Window w = getWindow();
        w.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
                | android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        android.view.WindowManager.LayoutParams lp = w.getAttributes();
        lp.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        w.setAttributes(lp);
        w.getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    // The device's vibrator (the runtime's android_vibration.c drives it for
    // rumble) stops at once when the game leaves the screen.
    @Override
    protected void onPause() {
        stopVibrator();
        super.onPause();
    }

    // Also when the activity is stopped or destroyed without a pause that
    // got through (the system reclaiming it, finish() from another path).
    @Override
    protected void onStop() {
        stopVibrator();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopVibrator();
        super.onDestroy();
    }

    private void stopVibrator() {
        try {
            ((android.os.Vibrator) getSystemService(VIBRATOR_SERVICE)).cancel();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Log.i(TAG, "window focus " + hasFocus);
        if (hasFocus) {
            immersive();
        }
    }

    // And whenever the bars come back for any other reason (SDL's own
    // window-style command, a start over an app that shows them): hide them
    // again a moment later.
    @Override
    protected void onResume() {
        super.onResume();
        final android.view.View decor = getWindow().getDecorView();
        decor.setOnSystemUiVisibilityChangeListener(
                new android.view.View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                Log.i(TAG, "system bars " + ((visibility
                        & android.view.View.SYSTEM_UI_FLAG_FULLSCREEN) == 0 ? "shown" : "hidden"));
                if ((visibility & android.view.View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    decor.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            immersive();
                        }
                    }, 300);
                }
            }
        });
        immersive();
    }

    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL2", "main" };
    }

    // Landscape either way up, whatever SDL works out from the window size.
    @Override
    public void setOrientationBis(int w, int h, boolean resizable, String hint) {
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }
}
