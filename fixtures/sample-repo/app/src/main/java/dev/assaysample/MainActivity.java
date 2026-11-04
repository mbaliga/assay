package dev.assaysample;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

/**
 * Assay's deliberately vulnerable sample target. Everything wrong here is intentional and exists
 * so the audit pipeline has real scanner findings to report. Never copy this code.
 */
public class MainActivity extends Activity {
    // DELIBERATELY VULNERABLE: hardcoded API credential (fake value, not a real service key).
    private static final String API_KEY = "q8Zk2Vn7Xp4Lw9Rt3Ym6Hb1Jc5Df0Sg8Ae";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView view = new WebView(this);
        // DELIBERATELY VULNERABLE: JavaScript enabled plus a native bridge on untrusted content.
        view.getSettings().setJavaScriptEnabled(true);
        view.addJavascriptInterface(new Bridge(), "native");
        view.loadUrl("http://example.invalid/?key=" + API_KEY);
        setContentView(view);
    }

    static final class Bridge {
        @JavascriptInterface
        public String key() {
            return API_KEY;
        }
    }
}
