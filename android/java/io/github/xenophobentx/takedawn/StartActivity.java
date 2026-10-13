package io.github.xenophobentx.takedawn;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import java.io.File;

/**
 * What the icon opens: the game, when the user's disc data is in place and
 * there is a graphics driver to run it on; otherwise a page that says what is
 * missing. The game is built from the user's own disc on their PC
 * (docs/android.md); nothing of it is in the repository, and the data is not
 * in the APK.
 */
public class StartActivity extends Activity {
    private boolean canStartAnyway;
    private String pageText;        // what the page says, for a bug report

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        File files = GameActivity.filesDir(this);
        File game = new File(files, GameActivity.GAME_DIR);
        // The directories exist from the first start on, so a file manager or
        // MTP shows where to copy to.
        game.mkdirs();
        GpuDriver.userDir(this).mkdirs();
        if (!dataComplete(files)) {
            // No data yet, a copy made by hand that is not checked, or an
            // install for another build: the setup page (SetupActivity).
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        if (!GpuDriver.usable(this)) {
            // No silent fall to the CPU rasteriser at a few frames a second.
            canStartAnyway = true;
            page("This device's graphics driver offers Vulkan "
                    + GpuDriver.versionString(GpuDriver.systemVulkanVersion(this))
                    + "; the game needs Vulkan 1.3.\n\n"
                    + (GpuDriver.isAdreno()
                        ? "For an Adreno GPU, put a Mesa Turnip driver (a .so, or a driver"
                          + " package .zip as the emulators use) into\n\n"
                          + GpuDriver.userDir(this).getAbsolutePath() + "\n\nand open the app again.\n\n"
                        : "There is no replacement driver for this GPU.\n\n")
                    + "Tap, or press A, to start anyway: the game then draws on the CPU,"
                    + " a few frames per second.");
            return;
        }
        startGame();
    }

    /** The installer's marker is there and names this program's disc manifest. */
    static boolean dataComplete(File files) {
        File mark = new File(files, ".complete");
        if (!mark.isFile() || !new File(new File(files, GameActivity.GAME_DIR), "default.xbe").isFile()) {
            return false;
        }
        try (java.io.InputStream in = new java.io.FileInputStream(mark)) {
            String t = new String(GpuDriver.readAll(in), java.nio.charset.StandardCharsets.UTF_8);
            return t.contains(BuildInfo.MANIFEST_ID);
        } catch (Exception e) {
            return false;
        }
    }

    private void startGame() {
        startActivity(new Intent(this, GameActivity.class));
        finish();
    }

    private void page(String text) {
        TextView t = new TextView(this);
        t.setTextColor(Color.WHITE);
        t.setBackgroundColor(Color.BLACK);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setGravity(Gravity.CENTER);
        t.setPadding(48, 48, 48, 48);
        t.setText(text);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (canStartAnyway) {
                    startGame();
                }
            }
        });
        // A bug report can be made from here too (ReportActivity; Y on a pad).
        pageText = text;
        setContentView(ReportActivity.withButton(this, t, text));
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent e) {
        if (canStartAnyway && (code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_DPAD_CENTER)) {
            startGame();
            return true;
        }
        if (code == KeyEvent.KEYCODE_BUTTON_Y && pageText != null) {
            ReportActivity.open(this, pageText);
            return true;
        }
        return super.onKeyUp(code, e);
    }
}
