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
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

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

public class MainActivity extends Activity {
    private static final int REQ_PERMS = 7;
    private static final int REQ_SIGNIN = 9;
    
    // Official Google AdMob Test Rewarded Video Ad Unit ID
    private static final String TEST_REWARDED_AD_ID = "ca-app-pub-3940256099942544/5224354917";
    
    private WebView web;
    private boolean tracking = false;
    private RewardedAd rewardedAd = null;
    private boolean isAdLoading = false;

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

        /** Show AdMob Rewarded Video Ad */
        @JavascriptInterface
        public void showRewardedAd(final String kind) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (rewardedAd != null) {
                        rewardedAd.show(MainActivity.this, new OnUserEarnedRewardListener() {
                            @Override
                            public void onUserEarnedReward(RewardItem rewardItem) {
                                // User earned reward! Notify WebView
                                notifyAdReward(kind);
                            }
                        });
                        rewardedAd = null;
                        loadRewardedAd();
                    } else {
                        Toast.makeText(MainActivity.this, "Ad loading... Opening preview timer", Toast.LENGTH_SHORT).show();
                        loadRewardedAd();
                        notifyAdFallback(kind);
                    }
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize Google Mobile Ads SDK
        try {
            MobileAds.initialize(this, new OnInitializationCompleteListener() {
                @Override
                public void onInitializationComplete(InitializationStatus status) {
                    loadRewardedAd();
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

    private void loadRewardedAd() {
        if (isAdLoading || rewardedAd != null) return;
        isAdLoading = true;
        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(this, TEST_REWARDED_AD_ID, adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(RewardedAd ad) {
                rewardedAd = ad;
                isAdLoading = false;
            }

            @Override
            public void onAdFailedToLoad(LoadAdError error) {
                rewardedAd = null;
                isAdLoading = false;
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
        if (rewardedAd == null) {
            loadRewardedAd();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }
}
