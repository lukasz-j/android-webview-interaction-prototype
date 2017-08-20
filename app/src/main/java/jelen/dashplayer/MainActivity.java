package jelen.dashplayer;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private float currentLightVal;
    private static final String PREF_FILE = "player_prefs";
    private static final String PREF_PROFILES = "profiles";
    private WebView w;

    private void injectAdaptationProfiles() {
        SharedPreferences p = getSharedPreferences(PREF_FILE, 0);
        if (p.contains(PREF_PROFILES)) {
            String config = p.getString("profiles", "[]");
            String code = "envAdapter.injectAdaptationProfiles(\""+config+"\");";
            w.evaluateJavascript(code, null);
        };
    }

    private void storeAdaptationProfiles() {
        final ValueCallback<String> configCallback = new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                SharedPreferences p = getSharedPreferences(PREF_FILE, 0);
                SharedPreferences.Editor e = p.edit();
                e.putString(PREF_PROFILES, value);
                e.apply();
            }
        };
        w.evaluateJavascript("envAdapter.provideAdaptationProfilesForPersistence();", configCallback);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WebView.setWebContentsDebuggingEnabled(true);

        w = (WebView) findViewById(R.id.playerView);
        w.getSettings().setJavaScriptEnabled(true);

        w.addJavascriptInterface(new CorsProxyHandler(this), "DownloaderProxyHandler");

        w.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
//                injectAdaptationProfiles();
                w.evaluateJavascript("envAdapter.setDownloaderProxy(function(url){return DownloaderProxy.encode(url);})", null);
            }
        });

        w.setWebChromeClient(new WebChromeClient());

        String runtimeUrl = getResources().getString(R.string.player_runtime_base_url);
        w.loadUrl(runtimeUrl+"/app/prod/index.html");

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

        Button passState = (Button) findViewById(R.id.passState);
        passState.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                JSONObject o = new JSONObject();
                try {
                    o.put("state1", currentLightVal);
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

    @Override
    protected void onStop() {
        super.onStop();
        storeAdaptationProfiles();
    }
}
