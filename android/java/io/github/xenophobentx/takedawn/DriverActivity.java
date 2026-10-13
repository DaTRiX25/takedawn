package io.github.xenophobentx.takedawn;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.Map;

/**
 * "Takedawn: graphics driver", a second entry in the launcher: which Vulkan
 * driver the game opens (GpuDriver), chosen before the game starts. The Mesa
 * Turnip driver in the app, the device's own, or a driver package picked with
 * the system's file picker (the .zip with meta.json that the Android
 * emulators' driver pages distribute, or a bare .so). The pad's d-pad and A
 * work the buttons.
 */
public class DriverActivity extends Activity {
    private static final int PICK = 1;
    private LinearLayout box;
    private String message;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.BLACK);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(64, 40, 64, 40);
        sv.addView(box);
        setContentView(sv);
        build();
        // From adb, for a test or a device without a file picker:
        //   am start -n <package>/.DriverActivity --es import <file under the app's files folder>
        //   am start -n <package>/.DriverActivity --es choose auto|bundled|system|custom|remove
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        message = null;
        build();
        handle(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void handle(Intent intent) {
        String imp = intent.getStringExtra("import");
        String ch = intent.getStringExtra("choose");
        if (imp != null) {
            try {
                java.io.File f = new java.io.File(imp).getCanonicalFile();
                String root = GameActivity.filesDir(this).getCanonicalPath() + "/";
                if (!f.getPath().startsWith(root)) {
                    throw new Exception("only a file under " + root + " is read this way");
                }
                take(new java.io.FileInputStream(f), f.getName());
            } catch (Exception e) {
                message = imp + " was not taken: " + e.getMessage() + ". Nothing is changed.";
                android.util.Log.e(GameActivity.TAG, "driver package " + imp + ": " + e);
                build();
            }
        } else if ("remove".equals(ch)) {
            GpuDriver.removeCustom(this);
            message = "The driver package is removed.";
            build();
        } else if (ch != null && (ch.equals("auto") || ch.equals("bundled") || ch.equals("system")
                || (ch.equals("custom") && !GpuDriver.customMeta(this).isEmpty()))) {
            choose(ch);
        }
    }

    /** Reads a package from a stream (closed here) on a worker thread and shows the result. */
    private void take(final InputStream stream, final String name) {
        message = "Reading " + name + "\u2026";
        build();
        new Thread(new Runnable() {
            @Override public void run() {
                String m;
                try (InputStream in = stream) {
                    String got = GpuDriver.importPackage(DriverActivity.this, in, name);
                    GpuDriver.setChoice(DriverActivity.this, "custom");
                    m = "Driver package \"" + got + "\" is in the app and chosen.";
                } catch (Exception e) {
                    m = name + " was not taken: " + e.getMessage() + ". Nothing is changed.";
                }
                android.util.Log.i(GameActivity.TAG, "driver package: " + m);
                final String msg = m;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        message = msg;
                        build();
                    }
                });
            }
        }).start();
    }

    private void text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setPadding(0, 10, 0, 10);
        box.addView(t);
    }

    private Button button(String label, boolean chosen, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText((chosen ? "●  " : "○  ") + label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        b.setOnClickListener(l);
        box.addView(b);
        return b;
    }

    private void choose(String c) {
        GpuDriver.setChoice(this, c);
        message = null;
        build();
    }

    private void build() {
        box.removeAllViews();
        String ch = GpuDriver.choice(this);
        int sys = GpuDriver.systemVulkanVersion(this);
        Map<String, String> custom = GpuDriver.customMeta(this);
        Map<String, String> last = GpuDriver.readKeys(GpuDriver.infoFile(this));
        String note = GpuDriver.prefs(this).getString("note", null);

        text("Graphics driver", 22, Color.WHITE);
        if (!last.isEmpty()) {
            text("Last start: " + last.get("device") + ", Vulkan " + last.get("vulkan")
                    + (last.get("mesa") != null && !last.get("mesa").isEmpty()
                        ? ", Mesa " + last.get("mesa") : "")
                    + " (" + last.get("driver") + ")", 14, 0xFFB0B0B0);
        }
        if (note != null) {
            text(note, 14, 0xFFF0C85A);
        }
        if (message != null) {
            text(message, 14, 0xFFF0C85A);
        }
        if (GpuDriver.BUNDLED.equals(last.get("driver")) && last.get("mesa") != null
                && !last.get("mesa").isEmpty()) {
            GpuDriver.prefs(this).edit().putString("bundledMesa", last.get("mesa")).apply();
        }
        String mesa = GpuDriver.prefs(this).getString("bundledMesa", null);
        Button first = button("Automatic: " + GpuDriver.describe(this, "auto"),
                "auto".equals(ch), new View.OnClickListener() {
                    @Override public void onClick(View v) { choose("auto"); }
                });
        if (GpuDriver.hasBundled(this)) {
            button("Mesa Turnip, in the app" + (mesa != null ? " (Mesa " + mesa + ")" : ""),
                    "bundled".equals(ch), new View.OnClickListener() {
                        @Override public void onClick(View v) { choose("bundled"); }
                    });
        }
        button("This device's driver (Vulkan " + GpuDriver.versionString(sys) + ")"
                        + (sys < 0x403000 ? ": too old for the game, which then draws on the CPU" : ""),
                "system".equals(ch), new View.OnClickListener() {
                    @Override public void onClick(View v) { choose("system"); }
                });
        if (!custom.isEmpty()) {
            button("Driver package: " + custom.get("name")
                            + (custom.get("version").isEmpty() ? "" : ", " + custom.get("version"))
                            + " (" + custom.get("lib") + ")",
                    "custom".equals(ch), new View.OnClickListener() {
                        @Override public void onClick(View v) { choose("custom"); }
                    });
        }
        text("", 6, Color.BLACK);
        button("Pick a driver package… (.zip with meta.json, or a .so)", false,
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("*/*");
                        try {
                            startActivityForResult(i, PICK);
                        } catch (Exception e) {
                            message = "This device has no file picker (" + e.getMessage()
                                    + "). Copy the package into "
                                    + GpuDriver.userDir(DriverActivity.this).getAbsolutePath()
                                    + " instead and choose Automatic.";
                            build();
                        }
                    }
                }).setText("Pick a driver package… (.zip with meta.json, or a .so)");
        if (!custom.isEmpty()) {
            button("Remove the driver package", false, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    GpuDriver.removeCustom(DriverActivity.this);
                    message = "The driver package is removed.";
                    build();
                }
            }).setText("Remove the driver package");
        }
        button("Start the game", false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(DriverActivity.this, StartActivity.class));
                finish();
            }
        }).setText("Start the game");
        button("Licences", false, new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(DriverActivity.this, LicensesActivity.class));
            }
        }).setText("Licences");
        text("A driver other than the one in the app is on trial at its first start: if the"
                + " game does not get to its first frames with it, the next start goes back to"
                + " the driver in the app and says so.", 13, 0xFF909090);
        first.requestFocus();
    }

    private String displayName(Uri u) {
        try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && c.getString(i) != null) {
                    return c.getString(i);
                }
            }
        } catch (Exception e) {
            // the last path segment will do
        }
        String s = u.getLastPathSegment();
        return s != null ? s : "driver";
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK || result != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri u = data.getData();
        try {
            take(getContentResolver().openInputStream(u), displayName(u));
        } catch (Exception e) {
            message = "The file could not be opened: " + e.getMessage();
            build();
        }
    }
}
