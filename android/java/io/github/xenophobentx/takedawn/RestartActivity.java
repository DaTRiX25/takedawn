package io.github.xenophobentx.takedawn;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.widget.TextView;

/**
 * "Restart the game now" (the overlay's driver rows): in a process of its
 * own (android:process=":restart"), so it outlives the game's, which ends
 * itself; a second later it starts the app again, with the driver that was
 * chosen, and goes.
 */
public class RestartActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView t = new TextView(this);
        t.setText("Restarting\u2026");
        t.setTextColor(Color.WHITE);
        t.setBackgroundColor(Color.BLACK);
        t.setGravity(Gravity.CENTER);
        setContentView(t);
        new Handler().postDelayed(new Runnable() {
            @Override public void run() {
                startActivity(new Intent(RestartActivity.this, StartActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            }
        }, 1200);
    }
}
