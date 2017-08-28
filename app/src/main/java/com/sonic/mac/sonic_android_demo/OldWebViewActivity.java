package com.sonic.mac.sonic_android_demo;

import android.graphics.Bitmap;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Date;
import java.util.Locale;

public class OldWebViewActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_old_web_view);
        final long openTime = new Date().getTime();
        //获取地址
        String requestUrl = getIntent().getStringExtra("requestUrl");
        //加载地址
        WebView webView = (WebView) findViewById(R.id.webView);
        webView.setWebViewClient(new WebViewClient(){
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                long currTime = new Date().getTime() - openTime;
                setTitle(String.format(Locale.CANADA,"%.2f秒",currTime/1000.f));
            }
        });
        webView.loadUrl(requestUrl);
    }
}
