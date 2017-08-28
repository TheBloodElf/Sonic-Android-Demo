package com.sonic.mac.sonic_android_demo;

import android.os.Bundle;
import android.webkit.WebView;

import com.github.lzyzsd.jsbridge.BridgeWebView;
import com.tencent.sonic.sdk.SonicSessionClient;

import java.util.HashMap;

/**
 * Created by mac on 2017/8/24.
 */

public class SonicSessionClientImpl extends SonicSessionClient {
    private BridgeWebView webView;
    public void bindWebView(BridgeWebView webView) {
        this.webView = webView;
    }
    @Override
    public void loadUrl(String url, Bundle extraData) {
        webView.loadUrl(url);
    }
    @Override
    public void loadDataWithBaseUrl(String baseUrl, String data, String mimeType, String encoding, String historyUrl) {
        webView.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl);
    }
    @Override
    public void loadDataWithBaseUrlAndHeader(String baseUrl, String data, String mimeType, String encoding, String historyUrl, HashMap<String, String> headers) {
        loadDataWithBaseUrl(baseUrl, data, mimeType, encoding, historyUrl);
    }
    public void destroy() {
        if (null != webView) {
            webView.destroy();
            webView = null;
        }
    }
}
