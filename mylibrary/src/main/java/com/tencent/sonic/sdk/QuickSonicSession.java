/*
 * Tencent is pleased to support the open source community by making VasSonic available.
 *
 * Copyright (C) 2017 THL A29 Limited, a Tencent company. All rights reserved.
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 *
 *
 */

package com.tencent.sonic.sdk;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * A subclass of SonicSession.
 * QuickSonicSession mainly uses {@link SonicSessionClient#loadDataWithBaseUrlAndHeader(String, String, String, String, String, HashMap)}
 * to load data. Sometime, it will use {@link SonicSessionClient#loadUrl(String, Bundle)} instead. By using
 * {@link SonicSessionClient#loadDataWithBaseUrlAndHeader(String, String, String, String, String, HashMap)}, WebView will
 * quickly load web pages without the network affecting.
 *
 * <p>
 *  ATTENTION:
 *  Standard WebView don't have head information (such as csp) when it calls
 *  {@link SonicSessionClient#loadDataWithBaseUrlAndHeader(String, String, String, String, String, HashMap)} method.
 *  So this session mode may cause a security risk. However, you can put the csp contents into the html to avoid this risk caused by the lack of csp.
 *
 * <p>
 * See also {@link StandardSonicSession}
 */

public class QuickSonicSession extends SonicSession implements Handler.Callback {
    //日志打印TAG
    private static final String TAG = SonicConstants.SONIC_SDK_LOG_PREFIX + "QuickSonicSession";
    //message.what最前的值
    private static final int CLIENT_CORE_MSG_BEGIN = COMMON_MSG_END;
    //本地数据获取并验证完成
    private static final int CLIENT_CORE_MSG_PRE_LOAD = CLIENT_CORE_MSG_BEGIN + 1;
    //客户端第一次请求完成且本地没有缓存数据
    private static final int CLIENT_CORE_MSG_FIRST_LOAD = CLIENT_CORE_MSG_BEGIN + 2;
    //客户端的template和服务器的一样但是动态数据不一样
    private static final int CLIENT_CORE_MSG_DATA_UPDATE = CLIENT_CORE_MSG_BEGIN + 3;
    //客户端的template和服务器的不一样
    private static final int CLIENT_CORE_MSG_TEMPLATE_CHANGE = CLIENT_CORE_MSG_BEGIN + 4;
    //连接失败
    private static final int CLIENT_CORE_MSG_CONNECTION_ERROR = CLIENT_CORE_MSG_BEGIN + 5;
    //当服务器设置了cache-offline为http时,表示sonic服务器不可用
    private static final int CLIENT_CORE_MSG_SERVICE_UNAVAILABLE = CLIENT_CORE_MSG_BEGIN + 6;
    //message.what最后的值
    private static final int CLIENT_CORE_MSG_END = CLIENT_CORE_MSG_SERVICE_UNAVAILABLE + 1;

    //如果pendingClientCoreMessage不为nul，说明用户设置了session创建完成后自动start的配置，
    //pendingClientCoreMessage将会记录最后的一个请求阶段
    private Message pendingClientCoreMessage;
    //本地缓存不存在
    private static final int PRE_LOAD_NO_CACHE = 1;
    //本地缓存存在
    private static final int PRE_LOAD_WITH_CACHE = 2;

    /**
     * First loaded message type : server data has not finished reading when send this message.
     */
    private static final int FIRST_LOAD_NO_CACHE = 1;

    /**
     * First loaded message type : server data has finished reading when send this message.
     */
    private static final int FIRST_LOAD_WITH_CACHE = 2;

    /**
     * Whether refresh page content when template change or not.
     */
    private static final int TEMPLATE_CHANGE_REFRESH = 1;
    //客户端是否执行了loadUrl方法，本地没有缓存
    private final AtomicBoolean wasLoadUrlInvoked = new AtomicBoolean(false);
    //客户端是否在执行请求的时候加载了该url本地的缓存数据（调用了loadDataWithBaseUrlAndHeader方法）
    private final AtomicBoolean wasLoadDataInvoked = new AtomicBoolean(false);

    QuickSonicSession(String id, String url, SonicSessionConfig config) {
        super(id, url, config);
    }
    //处理本类中的sendMessage
    @Override
    public boolean handleMessage(Message msg) {
        super.handleMessage(msg);
        if (CLIENT_CORE_MSG_BEGIN < msg.what && msg.what < CLIENT_CORE_MSG_END && !clientIsReady.get()) {
            pendingClientCoreMessage = Message.obtain(msg);
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") handleMessage: client not ready, core msg = " + msg.what + ".");
            return true;
        }
        switch (msg.what) {
            //如果在预加载阶段，让handleClientCoreMessage_PreLoad处理
            case CLIENT_CORE_MSG_PRE_LOAD:
                handleClientCoreMessage_PreLoad(msg);
                break;
            //第一次请求完成
            case CLIENT_CORE_MSG_FIRST_LOAD:
                handleClientCoreMessage_FirstLoad(msg);
                break;
            case CLIENT_CORE_MSG_CONNECTION_ERROR:
                handleClientCoreMessage_ConnectionError(msg);
                break;
            //客户端不能当问服务器
            case CLIENT_CORE_MSG_SERVICE_UNAVAILABLE:
                handleClientCoreMessage_ServiceUnavailable(msg);
                break;
            //通知webView动态数据变化了
            case CLIENT_CORE_MSG_DATA_UPDATE:
                handleClientCoreMessage_DataUpdate(msg);
                break;
            case CLIENT_CORE_MSG_TEMPLATE_CHANGE:
                handleClientCoreMessage_TemplateChange(msg);
                break;
            case CLIENT_MSG_NOTIFY_RESULT:
                setResult(msg.arg1, msg.arg2, true);
                break;
            //读取变化的部分
            case CLIENT_MSG_ON_WEB_READY: {
                diffDataCallback = (SonicDiffDataCallback) msg.obj;
                setResult(srcResultCode, finalResultCode, true);
                break;
            }
            default: {
                if (SonicUtils.shouldLog(Log.DEBUG)) {
                    SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ") can not  recognize refresh type: " + msg.what);
                }
                return false;
            }

        }
        return true;
    }

    /**
     * Handle the connection error message. Client will invoke loadUrl method if this method is not
     * invoked before, or do nothing.
     *
     * @param msg The message
     */
    private void handleClientCoreMessage_ConnectionError(Message msg) {
        if (wasLoadUrlInvoked.compareAndSet(false, true)) {
            if (SonicUtils.shouldLog(Log.INFO)) {
                SonicUtils.log(TAG, Log.INFO, "handleClientCoreMessage_ConnectionError: load src url.");
            }
            sessionClient.loadUrl(srcUrl, null);
        }
    }
    //如果客户端不能以sonic方式访问服务器
    private void handleClientCoreMessage_ServiceUnavailable(Message msg) {
        if (wasLoadUrlInvoked.compareAndSet(false, true)) {
            if (SonicUtils.shouldLog(Log.INFO)) {
                SonicUtils.log(TAG, Log.INFO, "handleClientCoreMessage_ServiceUnavailable:load src url.");
            }
            //让webView重新加载资源
            sessionClient.loadUrl(srcUrl, null);
        }
    }
    //处理预加载时期的message
    private void handleClientCoreMessage_PreLoad(Message msg) {
        switch (msg.arg1) {
            //如果本地没有缓存
            case PRE_LOAD_NO_CACHE: {
                //如果还没有开始加载，就设置正在加载
                if (wasLoadUrlInvoked.compareAndSet(false, true)) {
                    SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") handleClientCoreMessage_PreLoad:PRE_LOAD_NO_CACHE load url.");
                    //本地没有缓存就让webView加载url
                    sessionClient.loadUrl(srcUrl, null);
                } else {
                    SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleClientCoreMessage_PreLoad:wasLoadUrlInvoked = true.");
                }
            }
            break;
            //如果本地有缓存，直接让webView加载缓存的html文件
            case PRE_LOAD_WITH_CACHE: {
                //如果还没有开始加载，就设置正在加载
                if (wasLoadDataInvoked.compareAndSet(false, true)) {
                    SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") handleClientCoreMessage_PreLoad:PRE_LOAD_WITH_CACHE load data.");
                    String html = (String) msg.obj;
                    //加载本地缓存的html文件
                    sessionClient.loadDataWithBaseUrlAndHeader(currUrl, html, "text/html", "utf-8", currUrl, getHeaders());
                } else {
                    SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleClientCoreMessage_PreLoad:wasLoadDataInvoked = true.");
                }
            }
            break;
        }
    }
    //客户端第一次请求完成
    private void handleClientCoreMessage_FirstLoad(Message msg) {
        switch (msg.arg1) {
            case FIRST_LOAD_NO_CACHE: {
                if (wasInterceptInvoked.get()) {
                    SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") handleClientCoreMessage_FirstLoad:FIRST_LOAD_NO_CACHE.");
                    setResult(SONIC_RESULT_CODE_FIRST_LOAD, SONIC_RESULT_CODE_FIRST_LOAD, true);
                } else
                    SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleClientCoreMessage_FirstLoad:url was not invoked.");
            }
            break;
            case FIRST_LOAD_WITH_CACHE: {
                if (wasLoadUrlInvoked.compareAndSet(false, true)) {
                    SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") handleClientCoreMessage_FirstLoad:oh yeah, first load hit 304.");
                    sessionClient.loadDataWithBaseUrlAndHeader(currUrl, (String) msg.obj, "text/html", "utf-8", currUrl, getHeaders());
                    setResult(SONIC_RESULT_CODE_FIRST_LOAD, SONIC_RESULT_CODE_HIT_CACHE, false);
                } else {
                    SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") FIRST_LOAD_WITH_CACHE load url was invoked.");
                    setResult(SONIC_RESULT_CODE_FIRST_LOAD, SONIC_RESULT_CODE_FIRST_LOAD, true);
                }
            }
            break;
        }
    }
    //通知动态数据变化
    private void handleClientCoreMessage_DataUpdate(Message msg) {
        String htmlString = (String) msg.obj;
        //获取变化的部分
        String diffData = msg.getData().getString(DATA_UPDATE_BUNDLE_PARAMS_DIFF);
        //如果webView已经加载完成了，就通知webView变化的部分
        if (wasLoadDataInvoked.get()) {
            pendingDiffData = diffData;
            if (!TextUtils.isEmpty(diffData)) {
                SonicUtils.log(TAG, Log.INFO, "handleClientCoreMessage_DataUpdate:try to notify web callback.");
                setResult(SONIC_RESULT_CODE_DATA_UPDATE, SONIC_RESULT_CODE_DATA_UPDATE, true);
            } else {
                SonicUtils.log(TAG, Log.INFO, "handleClientCoreMessage_DataUpdate:diffData is null, cache-offline = store , do not refresh.");
                setResult(SONIC_RESULT_CODE_DATA_UPDATE, SONIC_RESULT_CODE_HIT_CACHE, true);
            }
            return;
        } else {
            //如果webView还没有加载完，就加载本次的html字符串
            if (!TextUtils.isEmpty(htmlString)) {
                SonicUtils.log(TAG, Log.INFO, "handleClientCoreMessage_DataUpdate:oh yeah data update hit 304, now clear pending data ->" + (null != pendingDiffData) + ".");
                pendingDiffData = null;
                sessionClient.loadDataWithBaseUrlAndHeader(currUrl, htmlString, "text/html", "utf-8", currUrl, getHeaders());
                setResult(SONIC_RESULT_CODE_DATA_UPDATE, SONIC_RESULT_CODE_HIT_CACHE, false);
                return;
            }
        }
        //如果webView没有加载完，html又没有数据，就让webView重新加载
        SonicUtils.log(TAG, Log.ERROR, "handleClientCoreMessage_DataUpdate error:call load url.");
        sessionClient.loadUrl(srcUrl, null);
        setResult(SONIC_RESULT_CODE_DATA_UPDATE, SONIC_RESULT_CODE_FIRST_LOAD, false);
    }

    /**
     * Handle template change message.If client had loaded local data before and the page need refresh,
     * client will load data or loadUrl according to whether the message has latest data. And the
     * <code>finalResultCode</code> will be set as <code>SONIC_RESULT_CODE_TEMPLATE_CHANGE</code>.
     *
     * If client had not loaded local data before, client will load the latest html content which comes
     * from server and the <code>finalResultCode</code> will be set as <code>SONIC_RESULT_CODE_HIT_CACHE</code>.
     *
     * @param msg The message
     */
    private void handleClientCoreMessage_TemplateChange(Message msg) {
        SonicUtils.log(TAG, Log.INFO, "handleClientCoreMessage_TemplateChange wasLoadDataInvoked = " + wasLoadDataInvoked.get() + ",msg arg1 = " + msg.arg1);

        if (wasLoadDataInvoked.get()) {
            if (TEMPLATE_CHANGE_REFRESH == msg.arg1) {
                String html = (String) msg.obj;
                if (TextUtils.isEmpty(html)) {
                    SonicUtils.log(TAG, Log.INFO, "handleClientCoreMessage_TemplateChange:load url with preload=2, webCallback is null? ->" + (null != diffDataCallback));
                    sessionClient.loadUrl(srcUrl, null);
                } else {
                    SonicUtils.log(TAG, Log.INFO, "handleClientCoreMessage_TemplateChange:load data.");
                    sessionClient.loadDataWithBaseUrlAndHeader(currUrl, html, "text/html", "utf-8", currUrl, getHeaders());
                }
                setResult(SONIC_RESULT_CODE_TEMPLATE_CHANGE, SONIC_RESULT_CODE_TEMPLATE_CHANGE, false);
            } else {
                SonicUtils.log(TAG, Log.INFO, "handleClientCoreMessage_TemplateChange:not refresh.");
                setResult(SONIC_RESULT_CODE_TEMPLATE_CHANGE, SONIC_RESULT_CODE_HIT_CACHE, false);
            }
        } else {
            SonicUtils.log(TAG, Log.INFO, "handleClientCoreMessage_TemplateChange:oh yeah template change hit 304.");
            if (msg.obj instanceof String) {
                String html = (String) msg.obj;
                sessionClient.loadDataWithBaseUrlAndHeader(currUrl, html, "text/html", "utf-8", currUrl, getHeaders());
                setResult(SONIC_RESULT_CODE_TEMPLATE_CHANGE, SONIC_RESULT_CODE_HIT_CACHE, false);
            } else {
                SonicUtils.log(TAG, Log.ERROR, "handleClientCoreMessage_TemplateChange error:call load url.");
                sessionClient.loadUrl(srcUrl, null);
                setResult(SONIC_RESULT_CODE_TEMPLATE_CHANGE, SONIC_RESULT_CODE_FIRST_LOAD, false);
            }
        }
        diffDataCallback = null;
        mainHandler.removeMessages(CLIENT_MSG_ON_WEB_READY);
    }

    @Override
    //发送本地缓存的该会话的html数据
    protected void handleLocalHtml(String localHtml) {
        //给message一个分类
        Message msg = mainHandler.obtainMessage(CLIENT_CORE_MSG_PRE_LOAD);
        //如果有数据，发送数据
        if (!TextUtils.isEmpty(localHtml)) {
            msg.arg1 = PRE_LOAD_WITH_CACHE;
            msg.obj = localHtml;
        } else {
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") runSonicFlow has no cache, do first load flow.");
            msg.arg1 = PRE_LOAD_NO_CACHE;
        }
        //sendMessage后由本类上面的handleMessage处理
        mainHandler.sendMessage(msg);
    }
    //读取变化的部分
    public boolean onWebReady(SonicDiffDataCallback callback) {
        SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") onWebReady: webCallback has set ? ->" + (null != diffDataCallback));
        if (null != diffDataCallback) {
            this.diffDataCallback = null;
            SonicUtils.log(TAG, Log.WARN, "session(" + sId + ") onWebReady: call more than once.");
        }
        Message msg = Message.obtain();
        msg.what = CLIENT_MSG_ON_WEB_READY;
        msg.obj = callback;
        mainHandler.sendMessage(msg);
        return true;
    }
    //webView初始化完成，可以开始接收数据
    public boolean onClientReady() {
        //好像是原子操作的作用
        if (clientIsReady.compareAndSet(false, true)) {
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") onClientReady: have pending client core message ? -> " + (null != pendingClientCoreMessage) + ".");
            //第一次运行都是空的
            if (null != pendingClientCoreMessage) {
                Message message = pendingClientCoreMessage;
                pendingClientCoreMessage = null;
                handleMessage(message);
                //第一次运行sessionState的默认值是STATE_NONE，所以会走start方法
            } else if (STATE_NONE == sessionState.get()) {
                start();
            }
            return true;
        }
        return false;
    }
    //如果已经拦截了
    public Object onClientRequestResource(String url) {
        if (wasInterceptInvoked.get() || !isMatchCurrentUrl(url)) {
            return null;
        }
        //设置已经拦截
        if (!wasInterceptInvoked.compareAndSet(false, true)) {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ")  onClientRequestResource error:Intercept was already invoked, url = " + url);
            return null;
        }
        if (SonicUtils.shouldLog(Log.DEBUG)) {
            SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ")  onClientRequestResource:url = " + url);
        }
        //如果当前请求状态为正在运行
        long startTime = System.currentTimeMillis();
        if (sessionState.get() == STATE_RUNNING) {
            synchronized (sessionState) {
                try {
                    if (sessionState.get() == STATE_RUNNING) {
                        SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") now wait for pendingWebResourceStream!");
                        sessionState.wait(30 * 1000);
                    }
                } catch (Throwable e) {
                    SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") wait for pendingWebResourceStream failed" + e.getMessage());
                }
            }
        } else {
            if (SonicUtils.shouldLog(Log.DEBUG)) {
                SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ") is not in running state: " + sessionState);
            }
        }
        SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") have pending stream? -> " + (pendingWebResourceStream != null) + ", cost " + (System.currentTimeMillis() - startTime) + "ms.");
        //如果pendingWebResourceStream不为nul，也就是数据已经请求了，这时候可以直接把数据给webView
        if (null != pendingWebResourceStream) {
            Object webResourceResponse;
            if (!isDestroyedOrWaitingForDestroy()) {
                String mime = SonicUtils.getMime(currUrl);
                webResourceResponse = SonicEngine.getInstance().getRuntime().createWebResourceResponse(mime, "utf-8", pendingWebResourceStream, getHeaders());
            } else {
                webResourceResponse = null;
                SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") onClientRequestResource error: session is destroyed!");

            }
            pendingWebResourceStream = null;
            return webResourceResponse;
        }
        return null;
    }

    /**
     * Handle 304{@link SonicSession#SONIC_RESULT_CODE_HIT_CACHE}, it just updates the sonic code.
     */
    protected void handleFlow_304(){
        Message msg = mainHandler.obtainMessage(CLIENT_MSG_NOTIFY_RESULT);
        msg.arg1 = SONIC_RESULT_CODE_HIT_CACHE;
        msg.arg2 = SONIC_RESULT_CODE_HIT_CACHE;
        mainHandler.sendMessage(msg);
    }

    protected void handleFlow_HttpError(int responseCode){
        if (config.RELOAD_IN_BAD_NETWORK) {
            mainHandler.removeMessages(CLIENT_CORE_MSG_PRE_LOAD);
            Message msg = mainHandler.obtainMessage(CLIENT_CORE_MSG_CONNECTION_ERROR);
            msg.arg1 = responseCode;
            mainHandler.sendMessage(msg);
        }
    }

    protected void handleFlow_ServiceUnavailable(){
        //删除预加载状态
        mainHandler.removeMessages(CLIENT_CORE_MSG_PRE_LOAD);
        //设置客户端不能访问服务器状态
        Message msg = mainHandler.obtainMessage(CLIENT_CORE_MSG_SERVICE_UNAVAILABLE);
        mainHandler.sendMessage(msg);
    }
    //如果是模板变化，那么将会读取所有的服务器响应数据（直到webView调用onPageFinish为止）
    protected void handleFlow_TemplateChange() {
        try {
            SonicUtils.log(TAG, Log.INFO, "handleFlow_TemplateChange :");
            long startTime = System.currentTimeMillis();
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            SonicSessionConnection.ResponseDataTuple responseDataTuple = sessionConnection.getResponseData(wasOnPageFinishInvoked, output);
            if (responseDataTuple == null) {
                SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_TemplateChange error:responseDataTuple = null!");
                return;
            }
            String cacheOffline = sessionConnection.getResponseHeaderField(SonicSessionConnection.CUSTOM_HEAD_FILED_CACHE_OFFLINE);
            // send CLIENT_CORE_MSG_TEMPLATE_CHANGE message
            mainHandler.removeMessages(CLIENT_CORE_MSG_PRE_LOAD);
            Message msg = mainHandler.obtainMessage(CLIENT_CORE_MSG_TEMPLATE_CHANGE);
            String htmlString = "";
            if (responseDataTuple.isComplete) {
                htmlString = responseDataTuple.outputStream.toString("UTF-8");
                msg.obj = htmlString;
            }
            if (!OFFLINE_MODE_STORE.equals(cacheOffline)) {
                msg.arg1 = TEMPLATE_CHANGE_REFRESH;
            }
            mainHandler.sendMessage(msg);
            if (!responseDataTuple.isComplete) {
                responseDataTuple = sessionConnection.getResponseData(wasInterceptInvoked, output);
                if (responseDataTuple != null) {
                    pendingWebResourceStream = new SonicSessionStream(this, responseDataTuple.outputStream, responseDataTuple.responseStream);
                } else {
                    pendingWebResourceStream = null;
                    SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_TemplateChange error:resourceResponseTuple = null!");
                    return;
                }
            }
            if (SonicUtils.shouldLog(Log.DEBUG)) {
                SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ") read byte stream cost " + (System.currentTimeMillis() - startTime) + " ms, wasInterceptInvoked: " + wasInterceptInvoked.get());
            }
            //save and separate data
            if (SonicUtils.needSaveData(cacheOffline)) {
                switchState(STATE_RUNNING, STATE_READY, true);
                if (!TextUtils.isEmpty(htmlString)) {
                    try {
                        //In order not to seize the cpu resources, affecting the rendering of the kernel，sleep 1.5s here
                        Thread.sleep(1500);
                        startTime = System.currentTimeMillis();
                        separateAndSaveCache(htmlString);
                        SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ") handleFlow_TemplateChange: read complete and finish separate and save cache cost " + (System.currentTimeMillis() - startTime) + " ms.");
                    } catch (Throwable e) {
                        SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_TemplateChange error:" + e.getMessage());
                    }
                }
            } else if (OFFLINE_MODE_FALSE.equals(cacheOffline)) {
                SonicUtils.removeSessionCache(id);
                SonicUtils.log(TAG, Log.INFO, "handleClientCoreMessage_TemplateChange:offline mode is 'false', so clean cache.");
            } else {
                SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_TemplateChange:offline->" + cacheOffline + " , so do not need cache to file.");
            }

        } catch (Throwable e) {
            SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ") handleFlow_TemplateChange error:" + e.getMessage());
        }
    }
    //处理url首次加载
    protected void handleFlow_FirstLoad() {
        //获取服务器的响应数，直到客户端拦截请求
        SonicSessionConnection.ResponseDataTuple responseDataTuple = sessionConnection.getResponseData(wasInterceptInvoked, null);
        if (null == responseDataTuple) {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_FirstLoad error:responseDataTuple is null!");
            return;
        }
        //把已经获取到的数据做成pendingWebResourceStream
        pendingWebResourceStream = new SonicSessionStream(this, responseDataTuple.outputStream, responseDataTuple.responseStream);
        //得到html数据
        String htmlString = null;
        //如果sonic都把这个请求的数据获取完成了，webView还没有拦截此请求（也就是webView还没有初始化完成），那么webView将不会拦截了
        if (responseDataTuple.isComplete) {
            try {
                //获取完整的数据，因为请求没有被webView拦截，数据是完整的
                htmlString = responseDataTuple.outputStream.toString("UTF-8");
            } catch (Throwable e) {
                pendingWebResourceStream = null;
                SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_FirstLoad error:" + e.getMessage() + ".");
            }
        }
        //是否有响应数据
        boolean hasCacheData = !TextUtils.isEmpty(htmlString);
        SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_FirstLoad:hasCacheData=" + hasCacheData + ".");
        //去掉预加载状态，添加第一次请求完成状态
        mainHandler.removeMessages(CLIENT_CORE_MSG_PRE_LOAD);
        Message msg = mainHandler.obtainMessage(CLIENT_CORE_MSG_FIRST_LOAD);
        msg.obj = htmlString;
        //这里就有意思了，如果在webView初始化之前sonic完成了数据请求，也和本地有缓存走一样的逻辑
        msg.arg1 = hasCacheData ? FIRST_LOAD_WITH_CACHE : FIRST_LOAD_NO_CACHE;
        mainHandler.sendMessage(msg);
        //是否需要保存数据到本地
        String cacheOffline = sessionConnection.getResponseHeaderField(SonicSessionConnection.CUSTOM_HEAD_FILED_CACHE_OFFLINE);
        if (SonicUtils.needSaveData(cacheOffline)) {
            try {
                //开始把数据保存到本地
                if (hasCacheData && !wasLoadUrlInvoked.get() && !wasInterceptInvoked.get()) {
                    switchState(STATE_RUNNING, STATE_READY, true);
                    //In order not to seize the cpu resources, affecting the rendering of the kernel，sleep 1.5s here
                    Thread.sleep(1500);
                    separateAndSaveCache(htmlString);
                }
            } catch (Throwable e) {
                SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_FirstLoad error:  " + e.getMessage());
            }
        } else {
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_FirstLoad:offline->" + cacheOffline + " , so do not need cache to file.");
        }
    }
    //处理动态数据变化
    protected void handleFlow_DataUpdate() {
        SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_DataUpdate: start.");
        ByteArrayOutputStream output = sessionConnection.getResponseData();
        if (output != null) {
            try {
                //获取响应头部的各种数据
                final String eTag = sessionConnection.getResponseHeaderField(SonicSessionConnection.CUSTOM_HEAD_FILED_ETAG);
                final String templateTag = sessionConnection.getResponseHeaderField(SonicSessionConnection.CUSTOM_HEAD_FILED_TEMPLATE_TAG);
                String cspContent = sessionConnection.getResponseHeaderField(SonicSessionConnection.HTTP_HEAD_CSP);
                String cspReportOnlyContent = sessionConnection.getResponseHeaderField(SonicSessionConnection.HTTP_HEAD_CSP_REPORT_ONLY);
                String cacheOffline = sessionConnection.getResponseHeaderField(SonicSessionConnection.CUSTOM_HEAD_FILED_CACHE_OFFLINE);
                String serverRsp = output.toString("UTF-8");
                //获取服务器返回的数据
                long startTime = System.currentTimeMillis();
                JSONObject serverRspJson = new JSONObject(serverRsp);
                final JSONObject serverDataJson = serverRspJson.optJSONObject("data");
                //得到变化的部分
                JSONObject diffDataJson = SonicUtils.getDiffData(id, serverDataJson);
                Bundle diffDataBundle = new Bundle();
                if (null != diffDataJson) {
                    diffDataBundle.putString(DATA_UPDATE_BUNDLE_PARAMS_DIFF, diffDataJson.toString());
                } else {
                    SonicUtils.log(TAG, Log.ERROR, "handleFlow_DataUpdate:getDiffData error.");
                    SonicEngine.getInstance().getRuntime().notifyError(sessionClient, srcUrl, SonicConstants.ERROR_CODE_MERGE_DIFF_DATA_FAIL);
                }
                if (SonicUtils.shouldLog(Log.DEBUG)) {
                    SonicUtils.log(TAG, Log.DEBUG, "handleFlow_DataUpdate:getDiffData cost " + (System.currentTimeMillis() - startTime) + " ms.");
                }
                boolean hasSentDataUpdateMessage = false;
                //如果webView已经加载完了数据，就通知数据变化
                if (wasLoadDataInvoked.get()) {
                    if (SonicUtils.shouldLog(Log.INFO)) {
                        SonicUtils.log(TAG, Log.INFO, "handleFlow_DataUpdate:loadData was invoked, quick notify web data update.");
                    }
                    Message msg = mainHandler.obtainMessage(CLIENT_CORE_MSG_DATA_UPDATE);
                    if (!OFFLINE_MODE_STORE.equals(cacheOffline)) {
                        msg.setData(diffDataBundle);
                    }
                    mainHandler.sendMessage(msg);
                    hasSentDataUpdateMessage = true;
                }
                //得到html的sha值
                startTime = System.currentTimeMillis();
                final String htmlSha1 = serverRspJson.optString("html-sha1");
                final String htmlString = SonicUtils.buildHtml(id, serverDataJson, htmlSha1, serverRsp.length());
                if (SonicUtils.shouldLog(Log.DEBUG)) {
                    SonicUtils.log(TAG, Log.DEBUG, "handleFlow_DataUpdate:buildHtml cost " + (System.currentTimeMillis() - startTime) + " ms.");
                }
                //如果html为nul，通知错误
                if (TextUtils.isEmpty(htmlString)) {
                    SonicEngine.getInstance().getRuntime().notifyError(sessionClient, srcUrl, SonicConstants.ERROR_CODE_BUILD_HTML_ERROR);
                }
                //如果没有通知webView动态数据变化
                if (!hasSentDataUpdateMessage) {
                    mainHandler.removeMessages(CLIENT_CORE_MSG_PRE_LOAD);
                    //添加动态数据变化阶段
                    Message msg = mainHandler.obtainMessage(CLIENT_CORE_MSG_DATA_UPDATE);
                    msg.obj = htmlString;
                    mainHandler.sendMessage(msg);
                }

                if (null == diffDataJson || null == htmlString || !SonicUtils.needSaveData(cacheOffline)) {
                    SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_DataUpdate: clean session cache.");
                    SonicUtils.removeSessionCache(id);
                }

                switchState(STATE_RUNNING, STATE_READY, true);

                Thread.yield();

                startTime = System.currentTimeMillis();
                if (SonicUtils.saveSessionFiles(id, htmlString, null, serverDataJson.toString())) {
                    long htmlSize = new File(SonicFileUtils.getSonicHtmlPath(id)).length();
                    SonicUtils.saveSonicData(id, eTag, templateTag, htmlSha1, htmlSize, cspContent, cspReportOnlyContent);
                    SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_DataUpdate: finish save session cache, cost " + (System.currentTimeMillis() - startTime) + " ms.");
                } else {
                    SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_DataUpdate: save session files fail.");
                    SonicEngine.getInstance().getRuntime().notifyError(sessionClient, srcUrl, SonicConstants.ERROR_CODE_WRITE_FILE_FAIL);
                }

            } catch (Throwable e) {
                SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_DataUpdate error:" + e.getMessage());
            } finally {
                try {
                    output.close();
                } catch (Throwable e) {
                    SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_DataUpdate close output stream error:" + e.getMessage());
                }
            }
        }
    }

    @Override
    protected void clearSessionData() {
        if (null != pendingClientCoreMessage) {
            pendingClientCoreMessage = null;
        }
    }
}
