package jelen.dashplayer;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private static final String PREF_FILE = "player_prefs";
    private static final String PREF_PROFILES = "profiles";
    private static final int SOUND_PROBING_INTERVAL = 5000;
    private static final int SOUND_READ_INTEVAL = 200;
    private static final int SOUND_MEASURMENTS_PER_PROBE = 5;
    private WebView w;
    protected SensorManager sensorManager;
    protected Sensor lightSensor;
    protected SensorEventListener ambientLightListener;
    protected Timer soundTimer;
    protected TimerTask soundRecorder;
    protected IntentFilter batteryIntentFilter;
    protected BroadcastReceiver batteryReceiver;
    protected ConnectivityManager connectivityManager;
    protected IntentFilter networkIntentFilter;
    protected BroadcastReceiver networkReceiver;

    private static class SoundRecorderEventHandler extends Handler {
        private WeakReference<MainActivity> activity;

        private SoundRecorderEventHandler(MainActivity activity) {
            this.activity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(android.os.Message msg) {
            String errorMessage = msg.getData().getString("errorMessage");
            if (null != errorMessage) {
                Toast.makeText(activity.get(), errorMessage, Toast.LENGTH_SHORT).show();
            }
            int soundValue = msg.getData().getInt("value");
            if (soundValue > 0) {
                activity.get().injectCondition("ambientSound", soundValue);
            }
        }
    }

    protected SoundRecorderEventHandler soundRecorderEventHandler;

    private void injectAdaptationProfiles() {
        SharedPreferences p = getSharedPreferences(PREF_FILE, 0);
        if (p.contains(PREF_PROFILES)) {
            String config = p.getString("profiles", "[]");
            String code = "envAdapter.injectAdaptationProfiles("+JSONObject.quote(config)+");";
            w.evaluateJavascript(code, null);
        };
    }

    private void doInjectCondition(String name, String jsValue) {
        String code = "envAdapter.setExternalCondition(\""+name+"\", "+jsValue+");";
        w.evaluateJavascript(code, null);
    }

    protected void injectCondition(String name, String value) {
        if (value == null) {
            value = "null";
        }
        else {
            value = JSONObject.quote(value);
        }
        this.doInjectCondition(name, value);
    }

    protected void injectCondition(String name, int value) {
        try {
            this.doInjectCondition(name, JSONObject.numberToString(value));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    protected void injectCondition(String name, boolean value) {
        this.doInjectCondition(name, value ? "true" : "false");
    }

    private void storeAdaptationProfiles() {
        final ValueCallback<String> configCallback = new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                // this may be called when web application is not initialized, so
                // do sanity checks first
                try {
                    JSONArray parsed = new JSONArray(value);
                } catch (JSONException e) {
                    // invalid array, skip
                    return;
                }
                SharedPreferences p = getSharedPreferences(PREF_FILE, 0);
                SharedPreferences.Editor e = p.edit();
                e.putString(PREF_PROFILES, value);
                e.apply();
            }
        };
        w.evaluateJavascript("envAdapter.provideAdaptationProfilesForPersistence();", configCallback);
    }

    protected void injectBatteryConditions(Intent batteryStatus) {
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        int batteryPct = (int)(level / (float)scale * 100.0);
        boolean discharging = !(status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
        injectCondition("batteryLevel", batteryPct);
        injectCondition("batteryDischarging", discharging);
    }

    protected void injectNetworkConditions() {
        NetworkInfo nInfo = connectivityManager.getActiveNetworkInfo();
        String netType = nInfo != null ? nInfo.getTypeName().toLowerCase() : null;
        injectCondition("networkType", netType);
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

        // prepare objects to inject initial battery and network info
        batteryIntentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        final Intent batteryStatus = MainActivity.this.registerReceiver(null, batteryIntentFilter);

        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        w.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                injectAdaptationProfiles();
                w.evaluateJavascript("envAdapter.setDownloaderProxy(function(url){return DownloaderProxyHandler.encode(url);});", null);
                w.evaluateJavascript("envAdapter.setEnclosedInApplication(true);", null);

                injectBatteryConditions(batteryStatus);
                injectNetworkConditions();
            }
        });

        w.setWebChromeClient(new WebChromeClient());

        String runtimeUrl = getResources().getString(R.string.player_runtime_base_url);
        w.loadUrl(runtimeUrl+"/app/prod/index.html");

        // prepare to light sensor event attaching
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        // init sound recorder error handler
        soundRecorderEventHandler = new SoundRecorderEventHandler(this);

        // init intent filter for connectivity
        networkIntentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
    }

    @Override
    protected void onStop() {
        super.onStop();
        storeAdaptationProfiles();
        sensorManager.unregisterListener(ambientLightListener);
        soundTimer.cancel();
        unregisterReceiver(batteryReceiver);
        unregisterReceiver(networkReceiver);
    }

    @Override
    protected void onStart() {
        super.onStart();

        ambientLightListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                int value = (int)(event.values[0] / event.sensor.getMaximumRange() * 100.0);
                injectCondition("ambientLight", value);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };
        sensorManager.registerListener(ambientLightListener, lightSensor, SensorManager.SENSOR_DELAY_UI);

        soundTimer = new Timer();
        soundRecorder = new TimerTask() {
            @Override
            public void run() {
                MediaRecorder r = new MediaRecorder();
                try {
                    r.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
                    r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                    r.setOutputFile("/dev/null"); // don't need audio at all, only max amplitude
                    r.prepare();
                    r.start();
                    r.getMaxAmplitude(); // init measuring
                    int samplesSum = 0;
                    for (int i=0; i<SOUND_MEASURMENTS_PER_PROBE; i++) {
                        Thread.sleep(SOUND_READ_INTEVAL);
                        int max = r.getMaxAmplitude();
                        samplesSum += max;
                    }
                    r.stop();
                    r.release();
                    int average = samplesSum / SOUND_MEASURMENTS_PER_PROBE;
                    // approximately convert to dB
                    int soundValue = (int)(20.0 * Math.log10((average / 51805.5336) / 0.00002));
                    Bundle bundle = new Bundle();
                    bundle.putInt("value", soundValue);
                    Message msg = new Message();
                    msg.setData(bundle);
                    soundRecorderEventHandler.sendMessage(msg);
                }
                catch (Exception e) {
                    Bundle bundle = new Bundle();
                    bundle.putString("message", e.getClass().getName()+": "+e.getMessage());
                    Message msg = new Message();
                    msg.setData(bundle);
                    soundRecorderEventHandler.sendMessage(msg);
                }
            }
        };

        soundTimer.schedule(soundRecorder, SOUND_PROBING_INTERVAL, SOUND_PROBING_INTERVAL);

        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                injectBatteryConditions(intent);
            }
        };
        registerReceiver(batteryReceiver, batteryIntentFilter);

        networkReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                injectNetworkConditions();
            }
        };
        registerReceiver(networkReceiver, networkIntentFilter);
    }
}
