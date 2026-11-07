package com.taskflow.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import java.io.ByteArrayInputStream;
import java.util.Collections;

public final class MainActivity extends Activity {
    private static final String SERVER_KEY = "server";
    private static final String WEB_STATE_KEY = "web-state";

    private SharedPreferences preferences;
    private ServerAddress server;
    private FrameLayout content;
    private View progress;
    private View errorPanel;
    private WebView webView;
    private String lastPage;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.activity_main);
        fitSystemBars(findViewById(R.id.root));

        content = findViewById(R.id.content);
        progress = findViewById(R.id.loading);
        errorPanel = findViewById(R.id.error_panel);
        preferences = getSharedPreferences("taskflow", MODE_PRIVATE);
        server = readServerAddress();

        findViewById(R.id.change_server).setOnClickListener(view -> editServer());
        findViewById(R.id.retry).setOnClickListener(view -> openTaskFlow(null));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::navigateBack);
        }

        Bundle webState = savedState != null && server.toString().equals(savedState.getString(SERVER_KEY))
                ? savedState.getBundle(WEB_STATE_KEY)
                : null;
        openTaskFlow(webState);
    }

    private void fitSystemBars(View root) {
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                Insets safeArea = windowInsets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom);
                return WindowInsets.CONSUMED;
            });
            WindowInsetsController bars = getWindow().getInsetsController();
            if (bars != null) {
                bars.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        }
    }

    private ServerAddress readServerAddress() {
        String defaultAddress = getString(R.string.default_server);
        try {
            return ServerAddress.parse(preferences.getString(SERVER_KEY, defaultAddress));
        } catch (IllegalArgumentException invalidPreference) {
            return ServerAddress.parse(defaultAddress);
        }
    }

    private void editServer() {
        View form = getLayoutInflater().inflate(R.layout.dialog_server, null);
        EditText addressInput = form.findViewById(R.id.address_input);
        addressInput.setText(server.toString());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.server_address)
                .setView(form)
                .setPositiveButton(R.string.connect, null)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.reset_address, null)
                .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                ServerAddress newServer;
                try {
                    newServer = ServerAddress.parse(addressInput.getText().toString());
                } catch (IllegalArgumentException invalidAddress) {
                    addressInput.setError(getString(R.string.server_address_invalid));
                    addressInput.requestFocus();
                    return;
                }

                if (!newServer.toString().equals(server.toString())) {
                    lastPage = null;
                }
                server = newServer;
                preferences.edit().putString(SERVER_KEY, newServer.toString()).apply();
                dialog.dismiss();
                openTaskFlow(null);
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view ->
                    addressInput.setText(R.string.default_server));
        });
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.show();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void openTaskFlow(Bundle webState) {
        disposeWebView();
        try {
            webView = new WebView(this);
        } catch (RuntimeException unavailableWebView) {
            showError(getString(R.string.webview_unavailable));
            return;
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " TaskFlowAndroid/1.0");
        webView.setBackgroundColor(getColor(R.color.ivory));

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, false);
        webView.setWebViewClient(new TaskFlowClient(server));
        content.addView(webView, 0, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        showLoading();
        if (webState == null || webView.restoreState(webState) == null) {
            webView.loadUrl(lastPage != null ? lastPage : server + "/");
        }
    }

    private void showLoading() {
        errorPanel.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        webView.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        if (webView != null) {
            webView.setVisibility(View.INVISIBLE);
        }
        progress.setVisibility(View.GONE);
        TextView errorMessage = findViewById(R.id.error_message);
        TextView errorAddress = findViewById(R.id.error_address);
        errorMessage.setText(message);
        errorAddress.setText(server.toString());
        errorPanel.setVisibility(View.VISIBLE);
    }

    private void navigateBack() {
        if (webView != null && errorPanel.getVisibility() != View.VISIBLE && webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        navigateBack();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
            CookieManager.getInstance().flush();
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (webView != null && errorPanel.getVisibility() != View.VISIBLE) {
            Bundle webState = new Bundle();
            webView.saveState(webState);
            state.putString(SERVER_KEY, server.toString());
            state.putBundle(WEB_STATE_KEY, webState);
        }
    }

    @Override
    protected void onDestroy() {
        disposeWebView();
        super.onDestroy();
    }

    private void disposeWebView() {
        if (webView != null) {
            WebView previousView = webView;
            webView = null;
            previousView.stopLoading();
            content.removeView(previousView);
            previousView.destroy();
        }
    }

    private final class TaskFlowClient extends WebViewClient {
        private final ServerAddress address;

        TaskFlowClient(ServerAddress address) {
            this.address = address;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            boolean blocked = !address.contains(request.getUrl().toString());
            if (blocked && request.isForMainFrame() && view == webView) {
                Toast.makeText(MainActivity.this, R.string.outside_taskflow, Toast.LENGTH_SHORT).show();
            }
            return blocked;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (address.contains(request.getUrl().toString())) {
                return null;
            }
            return new WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden",
                    Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (view != webView) {
                return;
            }
            if (!address.contains(url)) {
                view.stopLoading();
                showError(getString(R.string.outside_taskflow));
                return;
            }
            showLoading();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (view == webView && errorPanel.getVisibility() != View.VISIBLE && address.contains(url)) {
                lastPage = url;
                progress.setVisibility(View.GONE);
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (view == webView && request.isForMainFrame()) {
                int message = error.getErrorCode() == ERROR_TIMEOUT
                        ? R.string.connection_timeout : R.string.connection_error;
                showError(getString(message));
            }
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse error) {
            int status = error.getStatusCode();
            if (view == webView && request.isForMainFrame() && status >= 500) {
                showError(getString(R.string.server_error, status));
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
            if (view == webView) {
                showError(getString(R.string.certificate_error));
            }
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            if (view == webView) {
                disposeWebView();
                showError(getString(R.string.connection_error));
            }
            return true;
        }
    }
}
