package com.sonic.mac.sonic_android_demo;

import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.github.lzyzsd.jsbridge.BridgeHandler;
import com.github.lzyzsd.jsbridge.BridgeUtil;
import com.github.lzyzsd.jsbridge.BridgeWebView;
import com.github.lzyzsd.jsbridge.BridgeWebViewClient;
import com.github.lzyzsd.jsbridge.CallBackFunction;
import com.tencent.sonic.sdk.SonicConfig;
import com.tencent.sonic.sdk.SonicDiffDataCallback;
import com.tencent.sonic.sdk.SonicEngine;
import com.tencent.sonic.sdk.SonicSession;
import com.tencent.sonic.sdk.SonicSessionConfig;

import java.util.Date;
import java.util.Locale;

public class NewWebViewActivity extends AppCompatActivity {
    //记录页面打开的时间
    public static long openTime;
    private SonicSession sonicSession;//当前SonicSession
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_web_view);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        openTime = new Date().getTime();
        //获取请求的url地址
        String requestUrl = getIntent().getStringExtra("requestUrl");
        //配置SonicSessionConfig
        SonicSessionConfig.Builder sessionConfigBuilder = new SonicSessionConfig.Builder();
        //创建SonicSession
        sonicSession = SonicEngine.getInstance().createSession(requestUrl, sessionConfigBuilder.build());
        SonicSessionClientImpl sonicSessionClient = new SonicSessionClientImpl();
        //给会话绑定客户端
        sonicSession.bindClient(sonicSessionClient);
        //加载地址
        final BridgeWebView webView = (BridgeWebView) findViewById(R.id.webView);
        webView.setWebViewClient(new MyBridgeWebViewClient(webView));
        //webView已经初始化完毕，可以开始请求数据
        if (sonicSessionClient != null) {
            sonicSessionClient.bindWebView(webView);
            sonicSessionClient.clientReady();
        } else //不使用sonic，使用webView默认方式
            webView.loadUrl(requestUrl);
        sonicSessionClient.getDiffData(new SonicDiffDataCallback() {
            @Override
            public void callback(String resultData) {
                webView.callHandler("getDiffDataCallback", resultData, new CallBackFunction() {
                    @Override
                    public void onCallBack(String data) {}
                });
            }
        });
    }
    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
    @Override
    protected void onDestroy() {
        if (null != sonicSession) {
            sonicSession.destroy();
            sonicSession = null;
        }
        super.onDestroy();
    }
    public class MyBridgeWebViewClient extends BridgeWebViewClient {
        public MyBridgeWebViewClient(BridgeWebView webView) {
            super(webView);
        }
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            long currTime = new Date().getTime() - openTime;
            setTitle(String.format(Locale.CANADA,"%.2f秒",currTime/1000.f));
            if (sonicSession != null) {
                sonicSession.getSessionClient().pageFinish(url);
            }
        }
        @TargetApi(21)
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return shouldInterceptRequest(view, request.getUrl().toString());
        }
        //是否拦截请求
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            if (sonicSession != null) {
                return (WebResourceResponse) sonicSession.getSessionClient().requestResource(url);
            }
            return null;
        }
    }
}
