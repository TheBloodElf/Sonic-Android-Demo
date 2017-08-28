package com.sonic.mac.sonic_android_demo;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebResourceResponse;
import android.widget.Toast;

import com.tencent.sonic.sdk.SonicRuntime;
import com.tencent.sonic.sdk.SonicSessionClient;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Created by mac on 2017/8/24.
 */

public class HostSonicRuntime extends SonicRuntime {
    Context currContext = null;
    public HostSonicRuntime(Context context) {
        super(context);
        currContext = context;
    }
    /**
     * 获取用户UA信息
     * @return
     */
    @Override
    public String getUserAgent() {
        return "";
    }
    /**
     * 获取用户ID信息
     * @return
     */
    @Override
    public String getCurrentUserAccount() {
        return "sonic-demo-master";
    }
    @Override
    public String getCookie(String url) {
        CookieManager cookieManager = CookieManager.getInstance();
        return cookieManager.getCookie(url);
    }
    @Override
    public void log(String tag, int level, String message) {
        switch (level) {
            case Log.ERROR:
                Log.e(tag, message);
                break;
            case Log.INFO:
                Log.i(tag, message);
                break;
            default:
                Log.d(tag, message);
        }
    }
    @Override
    public Object createWebResourceResponse(String mimeType, String encoding, InputStream data, Map<String, String> headers) {
        WebResourceResponse resourceResponse =  new WebResourceResponse(mimeType, encoding, data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            resourceResponse.setResponseHeaders(headers);
        }
        return resourceResponse;
    }
    @Override
    public void showToast(CharSequence text, int duration) {
        Toast.makeText(currContext,text,duration).show();
    }
    //收集一些错误信息并显示
    @Override
    public void notifyError(SonicSessionClient client, String url, int errorCode) {
        Toast.makeText(currContext,"errorCode:"+errorCode,Toast.LENGTH_SHORT).show();
    }
    //对哪些url使用sonic加载
    @Override
    public boolean isSonicUrl(String url) {
        if(url.contains("http"))
            return true;
        return false;
    }
    @Override
    public boolean setCookie(String url, List<String> cookies) {
        if (!TextUtils.isEmpty(url) && cookies != null && cookies.size() > 0) {
            CookieManager cookieManager = CookieManager.getInstance();
            for (String cookie : cookies) {
                cookieManager.setCookie(url, cookie);
            }
            return true;
        }
        return false;
    }
    @Override
    public boolean isNetworkValid() {
        return true;
    }
    @Override
    public void postTaskToThread(Runnable task, long delayMillis) {
        Thread thread = new Thread(task, "SonicThread");
        thread.start();
    }
    @Override
    public File getSonicCacheDir() {
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "sonic/";
        File file = new File(path.trim());
        if(!file.exists()){
            file.mkdir();
        }
        return file;
    }
    @Override
    public String getHostDirectAddress(String url) {
        return null;
    }
}
