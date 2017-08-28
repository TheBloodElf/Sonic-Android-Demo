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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In Sonic, <code>SonicSession</code>s are used to manage the entire process,include
 * obtain the latest data from the server, provide local and latest
 * data to kernel, separate html to template and data, build template
 * and data to html and so on. Each url involves one session at a time,
 * that session will be destroyed when the page is destroyed.
 *
 */

public class SonicSession implements SonicSessionStream.Callback, Handler.Callback {

    /**
     * Log filter
     */
    public static final String TAG = SonicConstants.SONIC_SDK_LOG_PREFIX + "SonicSession";

    /**
     * The result keyword to page : the value is <code>srcResultCode</code>
     */
    public static final String WEB_RESPONSE_SRC_CODE = "srcCode";

    /**
     * The result keyword to page : the value is <code>finalResultCode</code>
     */
    public static final String WEB_RESPONSE_CODE = "code";


    /**
     * The all data keyword to page
     */
    public static final String WEB_RESPONSE_DATA = "result";


    public static final String SONIC_URL_PARAM_SESSION_ID = "_sonic_id";


    public static final String DATA_UPDATE_BUNDLE_PARAMS_DIFF = "_diff_data_";


    public static final String WEB_RESPONSE_LOCAL_REFRESH_TIME = "local_refresh_time";

    /**
     * Session state : original.
     * <p>
     * This state means session has not start.
     */
    public static final int STATE_NONE = 0;

    /**
     * Session state : running.
     * <p>
     * This state means session has begun to request data from
     * the server and is processing the data.
     *
     */
    public static final int STATE_RUNNING = 1;

    /**
     * Session state : ready.
     * <p>
     * This state means session data is available when the page
     * initiates a resource interception. In other stats the
     * client(kernel) will wait.
     *
     */
    public static final int STATE_READY = 2;

    /**
     * Session state : destroyed.
     * <p>
     * This state means the session is destroyed.
     */
    public static final int STATE_DESTROY = 3;

    /**
     * The value of "cache-offline" in http(s) response headers.
     * <p>
     * This value means sonic server unavailable, the terminal
     * does not take sonic logic for the next period of time,the
     * value of time is defined in {@link SonicConfig#SONIC_UNAVAILABLE_TIME}
     *
     */
    public static final String OFFLINE_MODE_HTTP = "http";

    /**
     * The value of "cache-offline" in http(s) response headers.
     * <p>
     * This value means sonic will save the latest data, but not refresh
     * page.For example, when sonic mode is data update, sonic will not
     * provide the difference data between local and server to page to refresh
     * the content.
     *
     */
    public static final String OFFLINE_MODE_STORE = "store";

    /**
     * The value of "cache-offline" in http(s) response headers.
     * <p>
     * This value means sonic will save the latest data and refresh page content.
     *
     */
    public static final String OFFLINE_MODE_TRUE = "true";

    /**
     * The value of "cache-offline" in http(s) response headers.
     * <p>
     * This value means sonic will refresh page content but not save date, sonic
     * will remove the local data also.
     *
     */
    public static final String OFFLINE_MODE_FALSE = "false";

    /**
     * Sonic mode : unknown.
     */
    public static final int SONIC_RESULT_CODE_UNKNOWN = -1;

    /**
     * Sonic mode : first load.
     */
    public static final int SONIC_RESULT_CODE_FIRST_LOAD = 1000;

    /**
     * Sonic mode : template change.
     */
    public static final int SONIC_RESULT_CODE_TEMPLATE_CHANGE = 2000;

    /**
     * Sonic mode : data update.
     */
    public static final int SONIC_RESULT_CODE_DATA_UPDATE = 200;

    /**
     * Sonic mode : 304.
     */
    public static final int SONIC_RESULT_CODE_HIT_CACHE = 304;

    /**
     * Sonic original mode.
     * <p>
     *  For example, when local data does not exist, the value is
     *  <code>SONIC_RESULT_CODE_FIRST_LOAD</code>
     *
     */
    protected int srcResultCode = SONIC_RESULT_CODE_UNKNOWN;

    /**
     * Sonic final mode.
     * <p>
     *  For example, when local data does not exist, the <code>srcResultCode</code>
     *  value is <code>SONIC_RESULT_CODE_FIRST_LOAD</code>. If the server data is read
     *  finished, sonic will provide the latest data to kernel when the kernel
     *  initiates a resource interception.This effect is the same as loading local data,
     *  so the sonic mode will be set as <code>SONIC_RESULT_CODE_HIT_CACHE</code>
     *
     */
    protected int finalResultCode = SONIC_RESULT_CODE_UNKNOWN;


    protected static final int COMMON_MSG_BEGIN = 0;

    /**
     * The message to record sonic mode.
     */
    protected static final int CLIENT_MSG_NOTIFY_RESULT = COMMON_MSG_BEGIN + 1;

    /**
     * The message of page ready, its means page want to get the latest session data.
     */
    protected static final int CLIENT_MSG_ON_WEB_READY = COMMON_MSG_BEGIN + 2;

    /**
     * The message of forced to destroy the session.
     */
    protected static final int SESSION_MSG_FORCE_DESTROY = COMMON_MSG_BEGIN + 3;


    protected static final int COMMON_MSG_END = COMMON_MSG_BEGIN + 4;

    /**
     * Session state, include <code>STATE_NONE</code>, <code>STATE_RUNNING</code>,
     * <code>STATE_READY</code> and <code>STATE_DESTROY</code>.
     */
    protected final AtomicInteger sessionState = new AtomicInteger(STATE_NONE);
    //客户端是否拦截了此请求
    protected final AtomicBoolean wasInterceptInvoked = new AtomicBoolean(false);
    //客户端是否已经准备好接收数据
    protected final AtomicBoolean clientIsReady = new AtomicBoolean(false);
    //是否通知了页面动态变化的数据
    private final AtomicBoolean wasNotified = new AtomicBoolean(false);
    //是否等待文件保存到本地，如果为true，那么session不能被销毁
    protected final AtomicBoolean isWaitingForSaveFile = new AtomicBoolean(false);
    //会话等待被销毁
    protected final AtomicBoolean isWaitingForDestroy = new AtomicBoolean(false);
    //session等待数据为true，这个会话将不会被销毁
    protected final AtomicBoolean isWaitingForSessionThread = new AtomicBoolean(false);
    //客户端页面加载完成
    protected final AtomicBoolean wasOnPageFinishInvoked = new AtomicBoolean(false);

    protected final SonicSessionStatistics statistics = new SonicSessionStatistics();

    protected volatile SonicSessionConnection sessionConnection;

    /**
     * The response for client interception.
     */
    protected volatile InputStream pendingWebResourceStream;

    /**
     * The difference data between local and server data.
     */
    protected String pendingDiffData = "";

    /**
     * Log id
     */
    protected static long sNextSessionLogId = new Random().nextInt(263167);

    final public SonicSessionConfig config;

    public final String id;

    /**
     * Whether current session is preload.
     */
    protected boolean isPreload;

    /**
     * The time of current session created.
     */
    public long createdTime;

    public final long sId;

    /**
     * The original url.
     */
    public String srcUrl;

    protected volatile SonicSessionClient sessionClient;

    /**
     * The current url.
     */
    protected String currUrl;

    protected final Handler mainHandler = new Handler(Looper.getMainLooper(), this);

    protected final CopyOnWriteArrayList<WeakReference<Callback>> callbackWeakRefList = new CopyOnWriteArrayList<WeakReference<Callback>>();

    protected SonicDiffDataCallback diffDataCallback;

    /**
     * The interface is used to inform the listeners that the state of the
     * session has changed.
     */
    public interface Callback {

        /**
         * When the session's state changes, this method will be invoked.
         *
         * @param session   Current session.
         * @param oldState  The old state.
         * @param newState  The next state.
         * @param extraData Extra data.
         */
        void onSessionStateChange(SonicSession session, int oldState, int newState, Bundle extraData);
    }

    /**
     * Subclasses must implement this to receive messages.
     */
    @Override
    public boolean handleMessage(Message msg) {

        if (SESSION_MSG_FORCE_DESTROY == msg.what) {
            destroy(true);
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") handleMessage:force destroy.");
            return true;
        }

        if (isDestroyedOrWaitingForDestroy()) {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleMessage error: is destroyed or waiting for destroy.");
            return false;
        }

        if (SonicUtils.shouldLog(Log.DEBUG)) {
            SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ") handleMessage: msg what = " + msg.what + ".");
        }

        return true;
    }
    //创建一个Sonic会话
    SonicSession(String id, String url, SonicSessionConfig config) {
        this.id = id;
        this.config = config;
        this.sId = (sNextSessionLogId++);
        //保存请求地址
        statistics.srcUrl = url.trim();
        //给地址加上_sonic_id参数，值为会话id
        this.srcUrl = SonicUtils.addSonicUrlParam(statistics.srcUrl, SONIC_URL_PARAM_SESSION_ID, String.valueOf(sId));
        this.currUrl = srcUrl;
        this.createdTime = System.currentTimeMillis();
        if (SonicUtils.shouldLog(Log.INFO)) {
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") create:id=" + id + ", url = " + url + ".");
        }
    }
    /**
     * Start the sonic process
     */
    public void start() {
        //如果sessionState的值为STATE_NONE，且设置为STATE_RUNNING成功
        if (!sessionState.compareAndSet(STATE_NONE, STATE_RUNNING)) {
            SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ") start error:sessionState=" + sessionState.get() + ".");
            return;
        }
        SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") now post sonic flow task.");
        //设置sonic开始时间
        statistics.sonicStartTime = System.currentTimeMillis();
        //设置需要等待sessin线程
        isWaitingForSessionThread.set(true);
        //让操作在sessionThread中执行
        SonicEngine.getInstance().getRuntime().postTaskToSessionThread(new Runnable() {
            @Override
            public void run() {
                //开始请求数据
                runSonicFlow();
            }
        });
        //通知状态由STATE_NONE变为STATE_RUNNING，通过SonicSession.addCallback添加
        notifyStateChange(STATE_NONE, STATE_RUNNING, null);
    }
    //开始请求数据
    private void runSonicFlow() {
        //如果sessionState状态不为STATE_RUNNING表示出错
        if (STATE_RUNNING != sessionState.get()) {
            SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ") runSonicFlow error:sessionState=" + sessionState.get() + ".");
            return;
        }
        //设置获取数据的时间
        statistics.sonicFlowStartTime = System.currentTimeMillis();
        //看一下本地是否有缓存的该会话html文件
        String htmlString = SonicCacheInterceptor.getSonicCacheData(this);
        boolean hasHtmlCache = !TextUtils.isEmpty(htmlString);
        //得到本地缓存验证的时间
        statistics.cacheVerifyTime = System.currentTimeMillis();
        SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") runSonicFlow verify cache cost " + (statistics.cacheVerifyTime - statistics.sonicFlowStartTime) + " ms");
        //处理该会话对应的html文件，本地有缓存就直接加载缓存html内容，本地没有缓存就让webView加载url
        handleLocalHtml(htmlString);
        final SonicRuntime runtime = SonicEngine.getInstance().getRuntime();
        //网络是否可用
        if (!runtime.isNetworkValid()) {
            //当网络不可用，且本地有缓存的时候
            if (hasHtmlCache && !TextUtils.isEmpty(config.USE_SONIC_CACHE_IN_BAD_NETWORK_TOAST)) {
                runtime.postTaskToMainThread(new Runnable() {
                    @Override
                    public void run() {
                        //如果客户端已经准备好接收数据，且当前会话没有被销毁，并且没有等待销毁
                        if (clientIsReady.get() && !isDestroyedOrWaitingForDestroy()) {
                            //提示网络不可用
                            runtime.showToast(config.USE_SONIC_CACHE_IN_BAD_NETWORK_TOAST, Toast.LENGTH_LONG);
                        }
                    }
                }, 1500);
            }
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") runSonicFlow error:network is not valid!");
        } else {//如果当前网络可用，处理与服务器的连接
            handleFlow_Connection(htmlString);
            //记录连接完成的时间
            statistics.connectionFlowFinishTime = System.currentTimeMillis();
        }
        // Update session state
        switchState(STATE_RUNNING, STATE_READY, true);

        isWaitingForSessionThread.set(false);

        // Current session can be destroyed if it is waiting for destroy.
        if (postForceDestroyIfNeed()) {
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") runSonicFlow:send force destroy message.");
        }
    }
    //网络可用，处理网络请求
    protected void handleFlow_Connection(String htmlString) {
        //设置连接服务器的时间
        statistics.connectionFlowStartTime = System.currentTimeMillis();
        //得到该会话对应的本地缓存数据
        SonicDataHelper.SessionData sessionData = SonicDataHelper.getSessionData(id);
        //创建一个intent
        Intent intent = new Intent();
        //设置该会话对应的etag、templateTag
        intent.putExtra(SonicSessionConnection.CUSTOM_HEAD_FILED_ETAG, sessionData.etag);
        intent.putExtra(SonicSessionConnection.CUSTOM_HEAD_FILED_TEMPLATE_TAG, sessionData.templateTag);
        //域名转行成ip地址
        String hostDirectAddress = SonicEngine.getInstance().getRuntime().getHostDirectAddress(srcUrl);
        //是否有域名转换成ip地址
        if (!TextUtils.isEmpty(hostDirectAddress)) {
            //设置是一个ip地址
            statistics.isDirectAddress = true;
        }
        //设置域名转换成ip的url地址
        intent.putExtra(SonicSessionConnection.DNS_PREFETCH_ADDRESS, hostDirectAddress);
        //获取数据连接者
        sessionConnection = SonicSessionConnectionInterceptor.getSonicSessionConnection(this, intent);
        //设置开始连接
        long startTime = System.currentTimeMillis();
        //得到请求代码
        int responseCode = sessionConnection.connect();
        //如果请求成功
        if (SonicConstants.ERROR_CODE_SUCCESS == responseCode) {
            //设置会话连接的时间
            statistics.connectionConnectTime = System.currentTimeMillis();
            if (SonicUtils.shouldLog(Log.DEBUG)) {
                SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ") connection connect cost = " + (System.currentTimeMillis() - startTime) + " ms.");
            }
            startTime = System.currentTimeMillis();
            //得到服务器返回的响应code
            responseCode = sessionConnection.getResponseCode();
            //设置得到服务器响应时间
            statistics.connectionRespondTime = System.currentTimeMillis();
            if (SonicUtils.shouldLog(Log.DEBUG)) {
                SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ") connection response cost = " + (System.currentTimeMillis() - startTime) + " ms.");
            }
            // If the page has set cookie, sonic will set the cookie to kernel.
            startTime = System.currentTimeMillis();
            //得到响应头部
            Map<String, List<String>> HeaderFieldsMap = sessionConnection.getResponseHeaderFields();
            if (SonicUtils.shouldLog(Log.DEBUG)) {
                SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ") connection get header fields cost = " + (System.currentTimeMillis() - startTime) + " ms.");
            }
            //如果响应头部不为空
            if (null != HeaderFieldsMap) {
                String keyOfSetCookie = null;
                if (HeaderFieldsMap.containsKey("Set-Cookie")) {
                    keyOfSetCookie = "Set-Cookie";
                } else if (HeaderFieldsMap.containsKey("set-cookie")) {
                    keyOfSetCookie = "set-cookie";
                }
                //如果有设置cookie，我们就设置到CookieManager中去
                if (!TextUtils.isEmpty(keyOfSetCookie)) {
                    List<String> cookieList = HeaderFieldsMap.get(keyOfSetCookie);
                    SonicEngine.getInstance().getRuntime().setCookie(getCurrentUrl(), cookieList);
                }
            }
        }
        SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_Connection: respCode = " + responseCode + ", cost " + (System.currentTimeMillis() - statistics.connectionFlowStartTime) + " ms.");
        if (isDestroyedOrWaitingForDestroy()) {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_Connection: destroy before server response.");
            return;
        }
        //如果服务器返回304，表示完全缓存，客户端和服务器数据一摸一样
        if (HttpURLConnection.HTTP_NOT_MODIFIED == responseCode) {
            handleFlow_304();
            return;
        }
        //如果服务器返回不为200，表示错误
        if (HttpURLConnection.HTTP_OK != responseCode) {
            handleFlow_HttpError(responseCode);
            SonicEngine.getInstance().getRuntime().notifyError(sessionClient, srcUrl, responseCode);
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_Connection: response code not 200, response code = " + responseCode);
            return;
        }
        //获取服务器指派给客户端的cache-office
        String cacheOffline = sessionConnection.getResponseHeaderField(SonicSessionConnection.CUSTOM_HEAD_FILED_CACHE_OFFLINE);
        //如果是http 那么客户端将禁止一段时间，及不能使用sonic方式访问服务器
        if (OFFLINE_MODE_HTTP.equals(cacheOffline)) {
            //删除本地对该会话的缓存
            if (!TextUtils.isEmpty(htmlString)) {
                SonicUtils.removeSessionCache(id);
            }
            long unavailableTime = System.currentTimeMillis() + SonicEngine.getInstance().getConfig().SONIC_UNAVAILABLE_TIME;
            //设置服务器什么时候可以再次访问
            SonicDataHelper.setSonicUnavailableTime(id, unavailableTime);
            handleFlow_ServiceUnavailable();
            return;
        }
        //如果本地没有缓存，则表示该url是首次加载
        if (TextUtils.isEmpty(htmlString)) {
            handleFlow_FirstLoad();
        } else {
            //如果本地有缓存，判断模板是否有变化
            String templateChange = sessionConnection.getResponseHeaderField(SonicSessionConnection.CUSTOM_HEAD_FILED_TEMPLATE_CHANGE);
            if (SonicUtils.shouldLog(Log.INFO)) {
                SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_Connection:templateChange = " + templateChange);
            }
            //如果模版有设置值
            if (!TextUtils.isEmpty(templateChange)) {
                //如果模板没有变化
                if ("false".equals(templateChange) || "0".equals(templateChange)) {
                    handleFlow_DataUpdate(); // data update
                } else {//如果模板有变化
                    handleFlow_TemplateChange(); // template change
                }
            } else {//如果模版没有设置值
                String templateTag = sessionConnection.getResponseHeaderField(SonicSessionConnection.CUSTOM_HEAD_FILED_TEMPLATE_TAG);
                //如果模板有变化
                if (!TextUtils.isEmpty(templateTag) && !templateTag.equals(sessionData.templateTag)) {
                    SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") handleFlow_Connection:no templateChange field but template-tag has changed.");
                    handleFlow_TemplateChange(); //fault tolerance
                } else {//如果模板没有值，并且和本地模板的tag一样，表示服务器返回数据错误，删除该会话的内测缓存和本地缓存
                    SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") handleFlow_Connection:no templateChange field and template-tag is " + templateTag + ".");
                    SonicUtils.removeSessionCache(id);
                    SonicEngine.getInstance().getRuntime().notifyError(sessionClient, srcUrl, SonicConstants.ERROR_CODE_SERVER_DATA_EXCEPTION);
                }
            }
        }
        saveHeaders(sessionConnection);
    }

    protected void handleLocalHtml(String localHtml) {

    }

    protected void handleFlow_304() {

    }

    protected void handleFlow_HttpError(int responseCode) {

    }

    protected void handleFlow_ServiceUnavailable() {

    }

    /**
     * Handle sonic first {@link SonicSession#SONIC_RESULT_CODE_FIRST_LOAD} logic.
     */
    protected void handleFlow_FirstLoad() {

    }

    /**
     * Handle data update {@link SonicSession#SONIC_RESULT_CODE_DATA_UPDATE} logic.
     */
    protected void handleFlow_DataUpdate() {

    }

    /**
     * Handle template update {@link SonicSession#SONIC_RESULT_CODE_TEMPLATE_CHANGE} logic.
     */
    protected void handleFlow_TemplateChange() {

    }
    //设置已经预加载过该url
    void setIsPreload(String url) {
        //设置已经预加载过
        isPreload = true;
        //记录地址
        statistics.srcUrl = url.trim();
        //设置资源url
        this.srcUrl = SonicUtils.addSonicUrlParam(statistics.srcUrl, SONIC_URL_PARAM_SESSION_ID, String.valueOf(sId));
        this.currUrl = srcUrl;
        if (SonicUtils.shouldLog(Log.INFO)) {
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") is preload, new url=" + url + ".");
        }
    }

    public boolean isPreload() {
        return isPreload;
    }

    public SonicSessionStatistics getStatistics() {
        return statistics;
    }

    public boolean addCallback(Callback callback) {
        return callbackWeakRefList.add(new WeakReference<Callback>(callback));
    }

    public boolean removeCallback(Callback callback) {
        return callbackWeakRefList.remove(new WeakReference<Callback>(callback));
    }

    public String getCurrentUrl() {
        return currUrl;
    }

    public int getFinalResultCode() {
        return finalResultCode;
    }

    public int getSrcResultCode() {
        return srcResultCode;
    }

    protected boolean isDestroyedOrWaitingForDestroy() {
        return STATE_DESTROY == sessionState.get() || isWaitingForDestroy.get();
    }

    /**
     * Destroy the session if it is waiting for destroy and it is can be destroyed.
     *
     * @return Return true if the session is waiting for destroy and it is can be destroyed.
     */
    protected boolean postForceDestroyIfNeed() {
        if (isWaitingForDestroy.get() && canDestroy()) {
            mainHandler.sendEmptyMessage(SESSION_MSG_FORCE_DESTROY);
            return true;
        }
        return false;
    }

    protected boolean canDestroy() {
        if (isWaitingForSessionThread.get() || isWaitingForSaveFile.get()) {
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") canDestroy:false, isWaitingForSessionThread=" + isWaitingForDestroy.get() + ", isWaitingForSaveFile=" + isWaitingForSaveFile.get());
            return false;
        }
        return true;
    }

    protected boolean switchState(int fromState, int toState, boolean notify) {
        if (sessionState.compareAndSet(fromState, toState)) {
            if (notify) {
                synchronized (sessionState) {
                    sessionState.notify();
                }
            }
            notifyStateChange(fromState, toState, null);
            return true;
        }
        return false;
    }

    /**
     * If the kernel obtain inputStream from a <code>SonicSessionStream</code>, the inputStream
     * will be closed when the kernel reads the data.This method is invoked when the sonicSessionStream
     * close.
     *
     * <p>
     *  If the html is read complete, sonic will separate the html to template and data, and save these
     *  data.
     *
     * @param readComplete Whether the html is read complete.
     * @param outputStream The html content.
     */
    @Override
    public void onClose(final boolean readComplete, final ByteArrayOutputStream outputStream) {
        // set pendingWebResourceStream to null，or it has a problem when client reload the page.
        if (null != pendingWebResourceStream) {
            pendingWebResourceStream = null;
        }

        isWaitingForSaveFile.set(true);
        long onCloseStartTime = System.currentTimeMillis();

        //Separate and save html.
        if (readComplete && null != outputStream) {
            String cacheOffline = sessionConnection.getResponseHeaderField(SonicSessionConnection.CUSTOM_HEAD_FILED_CACHE_OFFLINE);
            if (SonicUtils.needSaveData(cacheOffline)) {
                SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") onClose:offline->" + cacheOffline + " , post separateAndSaveCache task.");
                SonicEngine.getInstance().getRuntime().postTaskToThread(new Runnable() {
                    @Override
                    public void run() {
                        if (SonicUtils.shouldLog(Log.DEBUG)) {
                            SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ") onClose:cachedStream size:" + outputStream.size());
                        }

                        String htmlString;
                        try {
                            htmlString = outputStream.toString("UTF-8");
                            outputStream.close();
                        } catch (Throwable e) {
                            htmlString = null;
                            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") onClose error:" + e.getMessage());
                        }

                        if (!TextUtils.isEmpty(htmlString)) {
                            long startTime = System.currentTimeMillis();
                            separateAndSaveCache(htmlString);
                            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") onClose:separate And save ache finish, cost " + (System.currentTimeMillis() - startTime) + " ms.");
                        }

                        // Current session can be destroyed if it is waiting for destroy.
                        isWaitingForSaveFile.set(false);
                        if (postForceDestroyIfNeed()) {
                            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") onClose: postForceDestroyIfNeed send destroy message.");
                        }
                    }
                }, 3000);
                return;
            }
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") onClose:offline->" + cacheOffline + " , so do not need cache to file.");
        } else {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") onClose error:readComplete =" + readComplete + ", outputStream is null -> " + (outputStream == null));
        }

        // Current session can be destroyed if it is waiting for destroy.
        isWaitingForSaveFile.set(false);
        if (postForceDestroyIfNeed()) {
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") onClose: postForceDestroyIfNeed send destroy message in chromium_io thread.");
        }

        if (SonicUtils.shouldLog(Log.DEBUG)) {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") onClose cost " + (System.currentTimeMillis() - onCloseStartTime) + " ms.");
        }
    }

    protected void separateAndSaveCache(String htmlString) {
        if (TextUtils.isEmpty(htmlString) || null == sessionConnection) {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") separateAndSaveCache error:htmlString is null or sessionConnection is null.");
            return;
        }

        final String eTag = sessionConnection.getResponseHeaderField(SonicSessionConnection.CUSTOM_HEAD_FILED_ETAG);
        final String templateTag = sessionConnection.getResponseHeaderField(SonicSessionConnection.CUSTOM_HEAD_FILED_TEMPLATE_TAG);
        String cspContent = sessionConnection.getResponseHeaderField(SonicSessionConnection.HTTP_HEAD_CSP);
        String cspReportOnlyContent = sessionConnection.getResponseHeaderField(SonicSessionConnection.HTTP_HEAD_CSP_REPORT_ONLY);

        SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") separateAndSaveCache: start separate, eTag = " + eTag + ", templateTag = " + templateTag);
        long startTime = System.currentTimeMillis();

        StringBuilder templateStringBuilder = new StringBuilder();
        StringBuilder dataStringBuilder = new StringBuilder();
        if (SonicUtils.separateTemplateAndData(id, htmlString, templateStringBuilder, dataStringBuilder)) {
            if (SonicUtils.saveSessionFiles(id, htmlString, templateStringBuilder.toString(), dataStringBuilder.toString())) {
                long htmlSize = new File(SonicFileUtils.getSonicHtmlPath(id)).length();
                SonicUtils.saveSonicData(id, eTag, templateTag, SonicUtils.getSHA1(htmlString), htmlSize, cspContent, cspReportOnlyContent);
            } else {
                SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") separateAndSaveCache: save session files fail.");
                SonicEngine.getInstance().getRuntime().notifyError(sessionClient, srcUrl, SonicConstants.ERROR_CODE_WRITE_FILE_FAIL);
            }
        } else {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") separateAndSaveCache: save separate template and data files fail.");
            SonicEngine.getInstance().getRuntime().notifyError(sessionClient, srcUrl, SonicConstants.ERROR_CODE_SPLIT_HTML_FAIL);
        }
        SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") separateAndSaveCache: finish separate, cost " + (System.currentTimeMillis() - startTime) + "ms.");
    }

    /**
     * When the session state changes, notify the listeners.
     *
     * @param oldState  The old state.
     * @param newState  The nex state.
     * @param extraData The extra data.
     */
    protected void notifyStateChange(int oldState, int newState, Bundle extraData) {
        Callback callback;
        for (WeakReference<Callback> callbackWeakRef : callbackWeakRefList) {
            callback = callbackWeakRef.get();
            if (null != callback)
                callback.onSessionStateChange(this, oldState, newState, extraData);
        }
    }
    //客户端请求完成走该方法
    protected void setResult(int srcCode, int finalCode, boolean notify) {
        SonicUtils.log(TAG, Log.INFO, "session(" + sId + ")  setResult: srcCode=" + srcCode + ", finalCode=" + finalCode + ".");
        statistics.originalMode = srcResultCode = srcCode;
        statistics.finalMode = finalResultCode = finalCode;
        if (!notify) return;
        //是否已经通知过了
        if (wasNotified.get()) {
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ")  setResult: notify error -> already has notified!");
        }
        //如果diffDataCallback为空，返回
        if (null == diffDataCallback) {
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ")  setResult: notify fail as webCallback is not set, please wait!");
            return;
        }
        //如果结果状态未知，返回
        if (this.finalResultCode == SONIC_RESULT_CODE_UNKNOWN) {
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ")  setResult: notify fail finalResultCode is not set, please wait!");
            return;
        }
        //如果wasNotified为fals，设置wasNotified为true
        wasNotified.compareAndSet(false, true);
        JSONObject json = new JSONObject();
        try {
            //如果结果状态为200，表示有动态数据更新
            if (finalResultCode == SONIC_RESULT_CODE_DATA_UPDATE) {
                JSONObject pendingObject = new JSONObject(pendingDiffData);
                long timeDelta = System.currentTimeMillis() - pendingObject.optLong("local_refresh_time", 0);
                //时间超过了30，不进行结果的处理
                if (timeDelta > 30 * 1000) {
                    SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") setResult: notify fail as receive js call too late, " + (timeDelta / 1000.0) + " s.");
                    pendingDiffData = "";
                    return;
                } else {
                    if (SonicUtils.shouldLog(Log.DEBUG)) {
                        SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ") setResult: notify receive js call in time: " + (timeDelta / 1000.0) + " s.");
                    }
                }
                if (timeDelta > 0) json.put("local_refresh_time", timeDelta);
                pendingObject.remove(WEB_RESPONSE_LOCAL_REFRESH_TIME);
                json.put(WEB_RESPONSE_DATA, pendingObject.toString());
            }
            json.put(WEB_RESPONSE_CODE, finalResultCode);
            json.put(WEB_RESPONSE_SRC_CODE, srcResultCode);
        } catch (Throwable e) {
            e.printStackTrace();
            SonicUtils.log(TAG, Log.ERROR, "session(" + sId + ") setResult: notify error -> " + e.getMessage());
        }
        if (SonicUtils.shouldLog(Log.DEBUG)) {
            String logStr = json.toString();
            if (logStr.length() > 512) {
                logStr = logStr.substring(0, 512);
            }
            SonicUtils.log(TAG, Log.DEBUG, "session(" + sId + ") setResult: notify now call jsCallback, jsonStr = " + logStr);
        }
        pendingDiffData = null;
        //通知callBack有动态数据变化
        diffDataCallback.callback(json.toString());
    }
    //给会话绑定会话客户端
    public boolean bindClient(SonicSessionClient client) {
        if (null == this.sessionClient) {
            this.sessionClient = client;
            //绑定会话
            client.bindSession(this);
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") bind client.");
            return true;
        }
        return false;
    }
    /**
     * Client informs sonic that it is ready.
     * Client ready means it's webview has been initialized, can start load url or load data.
     *
     * @return True if it is set for the first time
     */
    protected boolean onClientReady() {
        return false;
    }

    /**
     * When the webview initiates a resource interception, the client invokes the method to retrieve the data
     *
     * @param url The url of this session
     * @return Return the data to kernel
     */
    protected Object onClientRequestResource(String url) {
        return null;
    }

    /**
     * Client will call this method to obtain the update data when the page shows the content.
     *
     * @param diffDataCallback  Sonic provides the latest data to the page through this callback
     * @return The result
     */
    protected boolean onWebReady(SonicDiffDataCallback diffDataCallback) {
        return false;
    }

    protected boolean onClientPageFinished(String url) {
        if (isMatchCurrentUrl(url)) {
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") onClientPageFinished:url=" + url + ".");
            wasOnPageFinishInvoked.set(true);
            return true;
        }
        return false;
    }

    /**
     * Whether the incoming url matches the current url,it will
     * ignore url parameters
     *
     * @param url The incoming url.
     * @return Whether the incoming url matches the current url.
     */
    public boolean isMatchCurrentUrl(String url) {
        try {
            Uri currentUri = Uri.parse(currUrl);
            Uri uri = Uri.parse(url);

            String currentPath = (currentUri.getHost() + currentUri.getPath());
            String pendingPath = uri.getHost() + uri.getPath();

            if (currentUri.getHost().equalsIgnoreCase(uri.getHost())) {
                if (!currentPath.endsWith("/")) currentPath = currentPath + "/";
                if (!pendingPath.endsWith("/")) pendingPath = pendingPath + "/";
                return currentPath.equalsIgnoreCase(pendingPath);
            }
        } catch (Throwable e) {
            SonicUtils.log(TAG, Log.ERROR, "isMatchCurrentUrl error:" + e.getMessage());
        }
        return false;
    }

    /**
     * Get header info with the original url of current session.
     *
     * @return The header info.
     */
    protected HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<String, String>();
        String cspContent = SonicDataHelper.getCSPContent(id);
        String cspReportOnlyContent = SonicDataHelper.getCSPReportOnlyContent(id);
        if (SonicUtils.shouldLog(Log.INFO)) {
            SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") cspContent = " + cspContent + ", cspReportOnlyContent = " + cspReportOnlyContent + ".");
        }

        headers.put(SonicSessionConnection.HTTP_HEAD_CSP, cspContent);
        headers.put(SonicSessionConnection.HTTP_HEAD_CSP_REPORT_ONLY, cspReportOnlyContent);

        //Get header info from the headersProvider.
        SonicHeadersProvider headersProvider = SonicEngine.getInstance().getSonicHeadersProvider();
        if (headersProvider != null) {
            Map<String, String> headerData = headersProvider.getHeaders(srcUrl);
            if (headerData != null && headerData.size() > 0) {
                for (Map.Entry<String, String> entry : headerData.entrySet()) {
                    headers.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return headers;
    }
    //保存本地连接的请求头部
    protected void saveHeaders(SonicSessionConnection sessionConnection) {
        if (sessionConnection != null) {
            Map<String, List<String>> headerFieldsMap = sessionConnection.getResponseHeaderFields();
            SonicHeadersProvider headersProvider = SonicEngine.getInstance().getSonicHeadersProvider();
            if (headersProvider != null) {
                headersProvider.saveHeaders(srcUrl, headerFieldsMap);
            }
        }
    }
    public SonicSessionClient getSessionClient() {
        return sessionClient;
    }
    public void destroy() {
        destroy(false);
    }
    protected void destroy(boolean force) {
        int curState = sessionState.get();
        if (STATE_DESTROY != curState) {

            if (null != sessionClient) {
                sessionClient = null;
            }

            if (null != pendingWebResourceStream) {
                pendingWebResourceStream = null;
            }

            if (null != pendingDiffData) {
                pendingDiffData = null;
            }

            clearSessionData();

            if (force || canDestroy()) {
                if (null != sessionConnection && !force) {
                    sessionConnection.disconnect();
                    sessionConnection = null;
                }

                sessionState.set(STATE_DESTROY);
                synchronized (sessionState) {
                    sessionState.notify();
                }
                notifyStateChange(curState, STATE_DESTROY, null);

                mainHandler.removeMessages(SESSION_MSG_FORCE_DESTROY);

                callbackWeakRefList.clear();

                isWaitingForDestroy.set(false);

                SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") final destroy, force=" + force + ".");
                return;
            }

            if (isWaitingForDestroy.compareAndSet(false, true)) {
                mainHandler.sendEmptyMessageDelayed(SESSION_MSG_FORCE_DESTROY, 6000);
                SonicUtils.log(TAG, Log.INFO, "session(" + sId + ") waiting for destroy, current state =" + curState + ".");
            }
        }
    }

    protected void clearSessionData() {

    }
}
