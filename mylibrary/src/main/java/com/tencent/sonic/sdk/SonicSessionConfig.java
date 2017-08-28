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

import android.os.Build;

//SonicSession配置
public class SonicSessionConfig {
    //链接超时时间 默认5s
    int CONNECT_TIMEOUT_MILLIS = 5 * 1000;
    //读取数据超时 默认15s
    int READ_TIMEOUT_MILLIS = 15 * 1000;
    //读取buf数据的大小 默认10k
    int READ_BUF_SIZE = 1024 * 10;
    //预加载会话保留时间 默认3分钟
    long PRELOAD_SESSION_EXPIRED_TIME = 3 * 60 * 1000;
    //是否开启dif，如果为true则服务器只会返回和客户端动态数据变化的部分
    boolean ACCEPT_DIFF_DATA = true;
    //本地缓存是否关联账号，如果关联账号，那么每个账号都有独立的缓存文件
    boolean IS_ACCOUNT_RELATED = true;
    //在网路不好的时候是否读取数据
    boolean RELOAD_IN_BAD_NETWORK = false;
    //创建一个会话的时候自动开始请求数据
    boolean AUTO_START_WHEN_CREATE = true;
    //网路不好时的提示语言
    String USE_SONIC_CACHE_IN_BAD_NETWORK_TOAST = "当前网路不可用，请检查网络";
    //SonicSession会话模式 有SESSION_MODE_QUICK和SESSION_MODE_DEFAULT两种，SESSION_MODE_QUICK逻辑更复杂速度更快
    int sessionMode = SonicConstants.SESSION_MODE_QUICK;
    //本地缓存数据提供者
    SonicCacheInterceptor cacheInterceptor = null;
    //网络连接提供者
    SonicSessionConnectionInterceptor connectionInterceptor = null;

    @Override
    public boolean equals(Object other) {
        return other instanceof SonicSessionConfig && sessionMode == ((SonicSessionConfig) other).sessionMode;
    }
    private SonicSessionConfig() {
    }
    public static class Builder {
        private final SonicSessionConfig target;
        public Builder() {
            target = new SonicSessionConfig();
        }
        public Builder setConnectTimeoutMillis(int connectTimeoutMillis) {
            target.CONNECT_TIMEOUT_MILLIS = connectTimeoutMillis;
            return this;
        }
        public Builder setReadTimeoutMillis(int readTimeoutMillis) {
            target.READ_TIMEOUT_MILLIS = readTimeoutMillis;
            return this;
        }
        public Builder setReadBufferSize(int readBufferSize) {
            target.READ_BUF_SIZE = readBufferSize;
            return this;
        }
        public Builder setPreloadSessionExpiredTimeMillis(long preloadSessionExpiredTimeMillis) {
            target.PRELOAD_SESSION_EXPIRED_TIME = preloadSessionExpiredTimeMillis;
            return this;
        }
        public Builder setAcceptDiff(boolean enable) {
            target.ACCEPT_DIFF_DATA = enable;
            return this;
        }
        public Builder setIsAccountRelated(boolean value) {
            target.IS_ACCOUNT_RELATED = value;
            return this;
        }
        public Builder setReloadInBadNetwork(boolean reloadInBadNetwork) {
            target.RELOAD_IN_BAD_NETWORK = reloadInBadNetwork;
            return this;
        }
        public Builder setAutoStartWhenCreate(boolean autoStartWhenCreate) {
            target.AUTO_START_WHEN_CREATE = autoStartWhenCreate;
            return this;
        }
        public Builder setUseSonicCacheInBadNetworkToastMessage(String toastMessage) {
            target.USE_SONIC_CACHE_IN_BAD_NETWORK_TOAST = toastMessage;
            return this;
        }
        public Builder setSessionMode(int sessionMode) {
            target.sessionMode = sessionMode;
            return this;
        }
        public Builder setCacheInterceptor(SonicCacheInterceptor interceptor) {
            target.cacheInterceptor = interceptor;
            return this;
        }
        public Builder setConnectionIntercepter(SonicSessionConnectionInterceptor intercepter) {
            target.connectionInterceptor = intercepter;
            return this;
        }
        public SonicSessionConfig build() {
            return target;
        }
    }
}
