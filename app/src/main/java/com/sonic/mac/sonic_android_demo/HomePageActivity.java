package com.sonic.mac.sonic_android_demo;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.tencent.sonic.sdk.SonicEngine;

public class HomePageActivity extends AppCompatActivity {
    private final static String locaRequestUrl = "http://192.168.0.103/sonic-php/sample/index.php";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_page);
        //不使用Sonic加载
        findViewById(R.id.button1).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(HomePageActivity.this,OldWebViewActivity.class);
                intent.putExtra("requestUrl",locaRequestUrl);
                startActivity(intent);
            }
        });
        //使用Sonic加载
        findViewById(R.id.button2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(HomePageActivity.this,NewWebViewActivity.class);
                intent.putExtra("requestUrl",locaRequestUrl);
                startActivity(intent);
            }
        });
        //清除Sonic缓存
        findViewById(R.id.button3).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SonicEngine.getInstance().cleanCache();
            }
        });
    }
}
