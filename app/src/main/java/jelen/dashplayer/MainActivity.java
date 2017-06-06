package jelen.dashplayer;

import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private float currentLightVal;
    private static final String PREF_FILE = "player_prefs";
    private Random r;
    private WebView w;

    private void restoreConfig() {
        SharedPreferences p = getSharedPreferences(PREF_FILE, 0);
        if (p.contains("config")) {
            String config = p.getString("config", "");
            StringBuilder code = new StringBuilder();
            code.append("window.manager.setConfig(");
            code.append(config);
            code.append(");");
            w.evaluateJavascript(code.toString(), null);
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        w = (WebView) findViewById(R.id.playerView);
        w.getSettings().setJavaScriptEnabled(true);
        w.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                restoreConfig();
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Toast.makeText(MainActivity.this, "Oh no! " + description, Toast.LENGTH_SHORT).show();
            }
        });
        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                System.out.println(consoleMessage.message() + ", " + consoleMessage.sourceId() + ", " + consoleMessage.lineNumber());
                return true;
            }
        });
        w.loadUrl("file:///android_asset/player_www/index.html");

        r = new Random();
        SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor s = sm.getDefaultSensor(Sensor.TYPE_LIGHT);
        sm.registerListener(new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                currentLightVal = event.values[0];
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        }, s, Sensor.REPORTING_MODE_ON_CHANGE);

        final ValueCallback<String> configCallback = new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                SharedPreferences p = getSharedPreferences(PREF_FILE, 0);
                SharedPreferences.Editor e = p.edit();
                e.putString("config", value);
                e.commit();
            }
        };
        Button storeConfig = (Button) findViewById(R.id.storeConfig);
        storeConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                w.evaluateJavascript("window.manager.getConfig();", configCallback);
            }
        });

        final Button restoreConfigB = (Button) findViewById(R.id.restoreConfig);
        restoreConfigB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restoreConfig();
            }
        });

        Button passState = (Button) findViewById(R.id.passState);
        passState.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                JSONObject o = new JSONObject();
                try {
                    o.put("state1", currentLightVal);
                    o.put("state2", r.nextInt());
                } catch (JSONException e) {
                    e.printStackTrace();
                    return;
                }
                StringBuilder code = new StringBuilder();
                code.append("window.manager.setCurrentState(");
                code.append(o.toString());
                code.append(");");
                w.evaluateJavascript(code.toString(), null);
            }
        });
    }
}
