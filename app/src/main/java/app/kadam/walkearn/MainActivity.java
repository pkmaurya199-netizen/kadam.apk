package app.kadam.walkearn;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_PERMS = 7;
    private static final int REQ_SIGNIN = 9;
    
    // Official Google AdMob Production Rewarded Video Ad Unit ID
    private static final String REAL_REWARDED_AD_ID = "ca-app-pub-1680367915210629/8430598203";
    
    // Official Google AdMob Production Banner Ad Unit ID
    private static final String BANNER_AD_ID = "ca-app-pub-1680367915210629/6050016879";
    
    private WebView web;
    private android.speech.tts.TextToSpeech tts;
    private AdView bannerAdView = null;
    private boolean tracking = false;
    private RewardedInterstitialAd rewardedInterstitialAd = null;
    private boolean isAdLoading = false;

    /** Functions the web app can call as window.KadamNative.* or window.Android.* */
    public class Bridge {
        @JavascriptInterface
        public void speak(final String text, final String lang) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (tts != null && text != null && !text.isEmpty()) {
                        if ("hi".equalsIgnoreCase(lang) || "hi-IN".equalsIgnoreCase(lang)) {
                            tts.setLanguage(new java.util.Locale("hi", "IN"));
                        } else {
                            tts.setLanguage(java.util.Locale.US);
                        }
                        tts.setSpeechRate(0.85f);
                        tts.setPitch(1.15f);
                        tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "tts_" + System.currentTimeMillis());
                    }
                }
            });
        }

        @JavascriptInterface
        public void stopSpeaking() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (tts != null) {
                        tts.stop();
                    }
                }
            });
        }
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
        public String authStatus() {
            return userJson(null);
        }

        @JavascriptInterface
        public void signIn() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    startGoogleSignIn();
                }
            });
        }

        @JavascriptInterface
        public void signOut() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        FirebaseAuth.getInstance().signOut();
                        googleClient().signOut();
                    } catch (Exception ignored) {
                    }
                }
            });
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

        /** Show AdMob Rewarded Interstitial Video Ad */
        @JavascriptInterface
        public void showRewardedAd(final String kind) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (rewardedInterstitialAd != null) {
                        rewardedInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                rewardedInterstitialAd = null;
                                loadRewardedInterstitialAd();
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(AdError adError) {
                                rewardedInterstitialAd = null;
                                loadRewardedInterstitialAd();
                                notifyAdFailed(kind, adError.getMessage());
                            }
                        });

                        rewardedInterstitialAd.show(MainActivity.this, new OnUserEarnedRewardListener() {
                            @Override
                            public void onUserEarnedReward(RewardItem rewardItem) {
                                // User watched the ad completely!
                                notifyAdReward(kind);
                            }
                        });
                        rewardedInterstitialAd = null;
                        loadRewardedInterstitialAd();
                    } else {
                        Toast.makeText(MainActivity.this, "Loading Rewarded Video Ad...", Toast.LENGTH_SHORT).show();
                        loadRewardedInterstitialAndShow(kind);
                    }
                }
            });
        }

        @JavascriptInterface
        public void showRewardedAd() {
            showRewardedAd("reward");
        }

        @JavascriptInterface
        public void setDailyChallengeDone(final boolean done) {
            try {
                android.content.SharedPreferences prefs = MainActivity.this.getSharedPreferences(StepService.PREFS, Context.MODE_PRIVATE);
                if (done) {
                    String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
                    prefs.edit().putString("challenge_completed_day", today).apply();
                } else {
                    prefs.edit().remove("challenge_completed_day").apply();
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tts = new android.speech.tts.TextToSpeech(this, new android.speech.tts.TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == android.speech.tts.TextToSpeech.SUCCESS && tts != null) {
                    tts.setLanguage(new java.util.Locale("hi", "IN"));
                }
            }
        });
        
        // Initialize Google Mobile Ads SDK with test device configuration
        try {
            List<String> testDeviceIds = Arrays.asList(
                "ab42b275-d52c-42c2-896b-c7ef52bc44ea",
                "AB42B275-D52C-42C2-896B-C7EF52BC44EA",
                "ab42b275d52c42c2896bc7ef52bc44ea",
                "AB42B275D52C42C2896BC7EF52BC44EA",
                AdRequest.DEVICE_ID_EMULATOR
            );
            RequestConfiguration configuration = new RequestConfiguration.Builder()
                    .setTestDeviceIds(testDeviceIds)
                    .build();
            MobileAds.setRequestConfiguration(configuration);

            MobileAds.initialize(this, new OnInitializationCompleteListener() {
                @Override
                public void onInitializationComplete(InitializationStatus status) {
                    loadRewardedInterstitialAd();
                }
            });
        } catch (Exception ignored) {
        }

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.addJavascriptInterface(new Bridge(), "KadamNative");
        web.addJavascriptInterface(new Bridge(), "Android");
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
        // Auto-request step tracking permissions on launch
        askPermissions();

        // Schedule daily challenge push reminders (6:00 PM & 8:45 PM)
        try {
            DailyReminderReceiver.scheduleAlarms(this);
        } catch (Exception ignored) {
        }
    }

    private void loadRewardedInterstitialAd() {
        if (isAdLoading || rewardedInterstitialAd != null) return;
        isAdLoading = true;
        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedInterstitialAd.load(this, REAL_REWARDED_AD_ID, adRequest, new RewardedInterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(RewardedInterstitialAd ad) {
                rewardedInterstitialAd = ad;
                isAdLoading = false;
            }

            @Override
            public void onAdFailedToLoad(LoadAdError error) {
                rewardedInterstitialAd = null;
                isAdLoading = false;
            }
        });
    }

    private void loadRewardedInterstitialAndShow(final String kind) {
        if (isAdLoading) {
            // Already loading, wait briefly then check
            web.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (rewardedInterstitialAd != null) {
                        rewardedInterstitialAd.show(MainActivity.this, new OnUserEarnedRewardListener() {
                            @Override
                            public void onUserEarnedReward(RewardItem rewardItem) {
                                notifyAdReward(kind);
                            }
                        });
                        rewardedInterstitialAd = null;
                        loadRewardedInterstitialAd();
                    } else {
                        notifyAdFallback(kind);
                    }
                }
            }, 2500);
            return;
        }

        isAdLoading = true;
        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedInterstitialAd.load(this, REAL_REWARDED_AD_ID, adRequest, new RewardedInterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(final RewardedInterstitialAd ad) {
                rewardedInterstitialAd = ad;
                isAdLoading = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (rewardedInterstitialAd != null) {
                            rewardedInterstitialAd.show(MainActivity.this, new OnUserEarnedRewardListener() {
                                @Override
                                public void onUserEarnedReward(RewardItem rewardItem) {
                                    notifyAdReward(kind);
                                }
                            });
                            rewardedInterstitialAd = null;
                            loadRewardedInterstitialAd();
                        }
                    }
                });
            }

            @Override
            public void onAdFailedToLoad(final LoadAdError error) {
                rewardedInterstitialAd = null;
                isAdLoading = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "AdMob: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        notifyAdFailed(kind, error.getMessage());
                    }
                });
            }
        });
    }

    private void notifyAdReward(final String kind) {
        web.post(new Runnable() {
            @Override
            public void run() {
                web.evaluateJavascript("window.kadamOnAdRewarded && window.kadamOnAdRewarded('" + kind + "');", null);
            }
        });
    }

    private void notifyAdFailed(final String kind, final String error) {
        web.post(new Runnable() {
            @Override
            public void run() {
                String safe = error == null ? "" : error.replace("'", "\\'");
                web.evaluateJavascript("window.kadamOnAdFailed && window.kadamOnAdFailed('" + kind + "','" + safe + "');", null);
            }
        });
    }

    private void notifyAdFallback(final String kind) {
        web.post(new Runnable() {
            @Override
            public void run() {
                web.evaluateJavascript("window.kadamOnAdFallback && window.kadamOnAdFallback('" + kind + "');", null);
            }
        });
    }

    private GoogleSignInClient googleClient() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        return GoogleSignIn.getClient(this, gso);
    }

    private void startGoogleSignIn() {
        try {
            startActivityForResult(googleClient().getSignInIntent(), REQ_SIGNIN);
        } catch (Exception e) {
            tellAuth(userJson("Could not start Google sign-in"));
        }
    }

    /** Current signed-in user (or an error) as a JSON object for the web app. */
    private String userJson(String error) {
        JSONObject o = new JSONObject();
        try {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            if (u != null && error == null) {
                o.put("signedIn", true);
                o.put("uid", u.getUid());
                o.put("name", u.getDisplayName() == null ? "" : u.getDisplayName());
                o.put("email", u.getEmail() == null ? "" : u.getEmail());
            } else {
                o.put("signedIn", false);
                if (error != null) o.put("error", error);
            }
        } catch (Exception ignored) {
        }
        return o.toString();
    }

    private void tellAuth(final String json) {
        web.post(new Runnable() {
            @Override
            public void run() {
                web.evaluateJavascript("window.kadamAuth && window.kadamAuth(" + json + ")", null);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SIGNIN) return;
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount acct = task.getResult(ApiException.class);
            AuthCredential cred = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
            FirebaseAuth.getInstance().signInWithCredential(cred)
                    .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                        @Override
                        public void onComplete(Task<AuthResult> t) {
                            if (t.isSuccessful()) {
                                tellAuth(userJson(null));
                            } else {
                                tellAuth(userJson("Sign-in failed. Please try again."));
                            }
                        }
                    });
        } catch (ApiException e) {
            tellAuth(userJson(e.getStatusCode() == 12501 ? "Sign-in cancelled" : "Google sign-in error " + e.getStatusCode()));
        } catch (Exception e) {
            tellAuth(userJson("Google sign-in error"));
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
            try {
                DailyReminderReceiver.scheduleAlarms(this);
            } catch (Exception ignored) {
            }
            tellWeb();
            return;
        }
        requestPermissions(need.toArray(new String[0]), REQ_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (StepService.hasPermission(this) && StepService.hasSensor(this)) {
            startTracking();
        }
        try {
            DailyReminderReceiver.scheduleAlarms(this);
        } catch (Exception ignored) {
        }
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

    /** Phone back button: let the web app close a screen first, exit only from Home. */
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(function(){return window.kadamBack?window.kadamBack():false})()", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (!"true".equals(value)) {
                    MainActivity.super.onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bannerAdView != null) {
            bannerAdView.resume();
        }
        tracking = false;
        if (StepService.hasPermission(this) && StepService.hasSensor(this)) {
            startTracking();
        } else {
            askPermissions();
        }
        tellWeb();
        if (rewardedInterstitialAd == null) {
            loadRewardedInterstitialAd();
        }
    }

    @Override
    protected void onPause() {
        if (bannerAdView != null) {
            bannerAdView.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (bannerAdView != null) {
            bannerAdView.destroy();
        }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }
}
