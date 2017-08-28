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
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * Interacts with the overall SonicSessions running in the system.
 * Instances of this class can be used to query or fetch the information, such as SonicSession SonicRuntime.
 */
public class SonicEngine {

    /**
     * Log filter
     */
    private final static String TAG = SonicConstants.SONIC_SDK_LOG_PREFIX + "SonicEngine";

    /**
     * SonicRuntime
     */
    private final SonicRuntime runtime;

    /**
     * Global config
     */
    private final SonicConfig config;

    /**
     * Single instance
     */
    private static SonicEngine sInstance;
    //预加载的会话记录
    private final ConcurrentHashMap<String, SonicSession> preloadSessionPool = new ConcurrentHashMap<String, SonicSession>(5);
    //当前正在运行的会话记录
    private final ConcurrentHashMap<String, SonicSession> runningSessionHashMap = new ConcurrentHashMap<String, SonicSession>(5);

    private SonicEngine(SonicRuntime runtime, SonicConfig config) {
        this.runtime = runtime;
        this.config = config;
    }

    /**
     * Returns a SonicEngine instance
     * <p>
     * Make sure {@link #createInstance(SonicRuntime, SonicConfig)} has been called.
     *
     * @return SonicEngine instance
     * @throws IllegalStateException if {@link #createInstance(SonicRuntime, SonicConfig)} hasn't been called
     */
    public static synchronized SonicEngine getInstance() {
        if (null == sInstance) {
            throw new IllegalStateException("SonicEngine::createInstance() needs to be called before SonicEngine::getInstance()");
        }
        return sInstance;
    }

    //是否已经创建过单例
    public static synchronized boolean isGetInstanceAllowed() {
        return null != sInstance;
    }
    //创建SonicEngine单例对象
    public static synchronized SonicEngine createInstance(@NonNull SonicRuntime runtime, @NonNull SonicConfig config) {
        if (null == sInstance)
            sInstance = new SonicEngine(runtime, config);
        return sInstance;
    }
    /**
     * @return SonicRuntime object
     */
    public SonicRuntime getRuntime() {
        return runtime;
    }
    /**
     * @return SonicConfig object
     */
    public SonicConfig getConfig() {
        return config;
    }
    /**
     * Create session ID
     *
     * @param url    session url
     * @param isAccountRelated
     *   Session Id will contain {@link com.tencent.sonic.sdk.SonicRuntime#getCurrentUserAccount()}  if {@code isAccountRelated } is true.
     * @return String Object of session ID
     */
    public static String makeSessionId(String url, boolean isAccountRelated) {
        return getInstance().getRuntime().makeSessionId(url, isAccountRelated);
    }

    /**
     * This method will preCreate sonic session .
     * And maps the specified session id to the specified value in this table {@link #preloadSessionPool} if there is no same sonic session.
     * At the same time, if the number of {@link #preloadSessionPool} exceeds {@link SonicConfig#MAX_PRELOAD_SESSION_COUNT},
     * preCreateSession will return false and not create any sonic session.
     *
     * <p><b>Note: this method is intended for preload scene.</b></p>
     * @param url           url for preCreate sonic session
     * @param sessionConfig SonicSession config
     * @return
     *  If this method preCreate sonic session and associated with {@code sessionId} in this table {@link #preloadSessionPool} successfully,
     *  it will return true,
     *  <code>false</code> otherwise.
     */
    public synchronized boolean preCreateSession(@NonNull String url, @NonNull SonicSessionConfig sessionConfig) {
        String sessionId = makeSessionId(url, sessionConfig.IS_ACCOUNT_RELATED);
        if (!TextUtils.isEmpty(sessionId)) {
            SonicSession sonicSession = lookupSession(sessionConfig, sessionId, false);
            if (null != sonicSession) {
                runtime.log(TAG, Log.ERROR, "preCreateSession：sessionId(" + sessionId + ") is already in preload pool.");
                return false;
            }
            if (preloadSessionPool.size() < config.MAX_PRELOAD_SESSION_COUNT) {
                if (isSessionAvailable(sessionId) && runtime.isNetworkValid()) {
                    sonicSession = internalCreateSession(sessionId, url, sessionConfig);
                    if (null != sonicSession) {
                        preloadSessionPool.put(sessionId, sonicSession);
                        return true;
                    }
                }
            } else {
                runtime.log(TAG, Log.ERROR, "create id(" + sessionId + ") fail for preload size is bigger than " + config.MAX_PRELOAD_SESSION_COUNT + ".");
            }
        }
        return false;
    }

    //用请求地址和SonicSessionConfig创建会话
    public synchronized SonicSession createSession(@NonNull String url, @NonNull SonicSessionConfig sessionConfig) {
        //创建会话id 这里和iOS的处理还不一样，iOS直接就是把url进行md5，而Android还取了一些参数
        String sessionId = makeSessionId(url, sessionConfig.IS_ACCOUNT_RELATED);
        //这里为false表示该url不需要用sonic处理，HostSonicRuntime中isSonicUrl(Uri uri)返回false
        if (!TextUtils.isEmpty(sessionId)) {
            //预加载会话列表中是否有该会话，同时去掉预加载列表中的该会话
            SonicSession sonicSession = lookupSession(sessionConfig, sessionId, true);
            if (null != sonicSession) {
                //如果有，就设置已经加载了该url
                sonicSession.setIsPreload(url);
            } else if (isSessionAvailable(sessionId)) { // 缓存中未存，就创建
                sonicSession = internalCreateSession(sessionId, url, sessionConfig);
            }
            return sonicSession;
        }
        return null;
    }


    //用sessionId从预加载会话记录中查找会话 pick表示是否从预加载会话中去除该会话
    private SonicSession lookupSession(SonicSessionConfig config, String sessionId, boolean pick) {
        //判断会话是否为空 配置是否为空
        if (!TextUtils.isEmpty(sessionId) && config != null) {
            //从预加载的会话记录中取出该url的会话
            SonicSession sonicSession = preloadSessionPool.get(sessionId);
            if (sonicSession != null) {
                //判断session缓存是否过期,以及sessionConfig是否发生变化
                if (!config.equals(sonicSession.config) ||
                        sonicSession.config.PRELOAD_SESSION_EXPIRED_TIME > 0 && System.currentTimeMillis()
                                - sonicSession.createdTime > sonicSession.config.PRELOAD_SESSION_EXPIRED_TIME) {
                    if (runtime.shouldLog(Log.ERROR))
                        runtime.log(TAG, Log.ERROR, "lookupSession error:sessionId(" + sessionId + ") is expired.");
                    //如果过期、sessionConfig变化了，从预加载中删除
                    preloadSessionPool.remove(sessionId);
                    //销毁该会话
                    sonicSession.destroy();
                    return null;
                }
                //如果没有过期，但是pick为true，从预加载会话中删除，还是返回预加载中找到的sonicSession;
                if (pick)
                    preloadSessionPool.remove(sessionId);
            }
            return sonicSession;
        }
        return null;
    }
    //创建一个会话
    private SonicSession internalCreateSession(String sessionId, String url, SonicSessionConfig sessionConfig) {
        //如果正在运行的会话中不包含该会话
        if (!runningSessionHashMap.containsKey(sessionId)) {
            SonicSession sonicSession;
            //判断会话模式，创建各会话模式对应会话
            if (sessionConfig.sessionMode == SonicConstants.SESSION_MODE_QUICK)
                sonicSession = new QuickSonicSession(sessionId, url, sessionConfig);
            else
                sonicSession = new StandardSonicSession(sessionId, url, sessionConfig);
            //给会话添加回调监听会话状态，在本类下面有定义，主要是操作runningSessionHashMap
            sonicSession.addCallback(sessionCallback);
            //如果配置了自动开始会话
            if (sessionConfig.AUTO_START_WHEN_CREATE) {
                sonicSession.start();
            }
            return sonicSession;
        }
        if (runtime.shouldLog(Log.ERROR)) {
            runtime.log(TAG, Log.ERROR, "internalCreateSession error:sessionId(" + sessionId + ") is running now.");
        }
        return null;
    }

    //如果服务器设置了容灾，客户端可能不能访问
    private boolean isSessionAvailable(String sessionId) {
        long unavailableTime = SonicDataHelper.getLastSonicUnavailableTime(sessionId);
        if (System.currentTimeMillis() > unavailableTime) {
            return true;
        }
        if (runtime.shouldLog(Log.ERROR)) {
            runtime.log(TAG, Log.ERROR, "sessionId(" + sessionId + ") is unavailable and unavailable time until " + unavailableTime + ".");
        }
        return false;
    }
    
    /**
     *
     * @return Return SonicHeadersProvider Object.
     */
    public synchronized SonicHeadersProvider getSonicHeadersProvider() {
        return getInstance().getRuntime().getSonicHeadersProvider();
    }

    /**
     * Removes all of the cache from {@link #preloadSessionPool} and deletes file caches from SDCard.
     *
     * @return
     *      Returns {@code false} if {@link #runningSessionHashMap} is not empty.
     *      Returns {@code true} if all of the local file cache has been deleted, <code>false</code> otherwise
     */
    public synchronized boolean cleanCache() {
        if (!preloadSessionPool.isEmpty()) {
            runtime.log(TAG, Log.INFO, "cleanCache: remove all preload sessions, size=" + preloadSessionPool.size() + ".");
            Collection<SonicSession> sonicSessions = preloadSessionPool.values();
            for (SonicSession session : sonicSessions) {
                session.destroy();
            }
            preloadSessionPool.clear();
        }

        if (!runningSessionHashMap.isEmpty()) {
            runtime.log(TAG, Log.ERROR, "cleanCache fail, running session map's size is " + runningSessionHashMap.size() + ".");
            return false;
        }

        runtime.log(TAG, Log.INFO, "cleanCache: remove all sessions cache.");

        return SonicUtils.removeAllSessionCache();
    }

    /**
     * Removes the sessionId and its corresponding SonicSession from {@link #preloadSessionPool}.
     *
     * @param sessionId A unique session id
     * @return Return {@code true} If there is no specified sessionId in {@link #runningSessionHashMap}, <code>false</code> otherwise.
     */
    public synchronized boolean removeSessionCache(@NonNull String sessionId) {
        SonicSession sonicSession = preloadSessionPool.get(sessionId);
        if (null != sonicSession) {
            sonicSession.destroy();
            preloadSessionPool.remove(sessionId);
            runtime.log(TAG, Log.INFO, "sessionId(" + sessionId + ") removeSessionCache: remove preload session.");
        }

        if (!runningSessionHashMap.containsKey(sessionId)) {
            runtime.log(TAG, Log.INFO, "sessionId(" + sessionId + ") removeSessionCache success.");
            SonicUtils.removeSessionCache(sessionId);
            return true;
        }
        runtime.log(TAG, Log.ERROR, "sessionId(" + sessionId + ") removeSessionCache fail: session is running.");
        return false;
    }
    //监听会话的状态，运行中就添加到runningSessionHashMap，销毁就从runningSessionHashMap中移除
    private final SonicSession.Callback sessionCallback = new SonicSession.Callback() {
        @Override
        public void onSessionStateChange(SonicSession session, int oldState, int newState, Bundle extraData) {
            SonicUtils.log(TAG, Log.DEBUG, "onSessionStateChange:session(" + session.sId + ") from state " + oldState + " -> " + newState);
            switch (newState) {
                case SonicSession.STATE_RUNNING:
                    runningSessionHashMap.put(session.id, session);
                    break;
                case SonicSession.STATE_DESTROY:
                    runningSessionHashMap.remove(session.id);
                    break;
            }
        }
    };

}
