package com.sonic.mac.sonic_android_demo;

import android.app.Application;

import com.tencent.sonic.sdk.SonicConfig;
import com.tencent.sonic.sdk.SonicEngine;

/**
 * Created by mac on 2017/8/25.
 */

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        //是否还没有初始化Sonic环境
        if (!SonicEngine.isGetInstanceAllowed())
            //初始化Sonic环境
            SonicEngine.createInstance(new HostSonicRuntime(this), new SonicConfig.Builder().
                    setMaxPreloadSessionCount(3).setUnavailableTime(3 * 60 * 60 * 1000).
                    setCacheVerifyWithSha1(true).build());
    }
}
