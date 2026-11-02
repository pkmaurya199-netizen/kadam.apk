package app.kadam.walkearn;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int REQ_PERMS = 7;
    private WebView web;
    private boolean tracking = false;

    /** Functions the web app can call as window.KadamNative.* */
    public class Bridge {
        @JavascriptInterface
        public String status() {
            boolean supported = StepService.hasSensor(MainActivity.this);
            boolean granted = StepService.hasPermission(MainActivity.this);
            if (supported && granted) startTracking();
            return "{\"supported\":" + supported
                    + ",\"granted\":" + granted
                    + ",\"steps\":" + StepService.todaySteps(MainActivity.this) + "}";
        }

        @JavascriptInterface
        public void requestPermission() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    askPermissions();
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.addJavascriptInterface(new Bridge(), "KadamNative");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("file".equals(u.getScheme())) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, u));
                return true;
            }
        });
        setContentView(web);
        if (savedInstanceState == null) {
            web.loadUrl("file:///android_asset/index.html");
        } else {
            web.restoreState(savedInstanceState);
        }
    }

    private synchronized void startTracking() {
        if (tracking) return;
        try {
            Intent i = new Intent(this, StepService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(i);
            } else {
                startService(i);
            }
            tracking = true;
        } catch (Exception ignored) {
            // Try again on the next status check.
        }
    }

    private void askPermissions() {
        ArrayList<String> need = new ArrayList<String>();
        if (Build.VERSION.SDK_INT >= 29 && !StepService.hasPermission(this)) {
            need.add(android.Manifest.permission.ACTIVITY_RECOGNITION);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            need.add(android.Manifest.permission.POST_NOTIFICATIONS);
        }
        if (need.isEmpty()) {
            if (StepService.hasSensor(this)) startTracking();
            tellWeb();
            return;
        }
        requestPermissions(need.toArray(new String[0]), REQ_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (StepService.hasPermission(this) && StepService.hasSensor(this)) startTracking();
        tellWeb();
    }

    private void tellWeb() {
        web.post(new Runnable() {
            @Override
            public void run() {
                web.evaluateJavascript("window.kadamSync && window.kadamSync()", null);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        tracking = false;
        tellWeb();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }
}
