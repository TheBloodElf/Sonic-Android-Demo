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

//Sonic全局配置
public class SonicConfig {
    //用户可以预加载的会话数量，默认是5个
    int MAX_PRELOAD_SESSION_COUNT = 5;
    //服务器容灾，让部分客户端无法访问的时间，默认6小时
    long SONIC_UNAVAILABLE_TIME = 6 * 60 * 60 * 1000;
    //是否启动文件sha
    boolean VERIFY_CACHE_FILE_WITH_SHA1 = true;

    private SonicConfig() {}

    //SoncConfig初始化工具
    public static class Builder {
        private final SonicConfig target;
        public Builder() {
            target = new SonicConfig();
        }
        public Builder setMaxPreloadSessionCount(int maxPreloadSessionCount) {
            target.MAX_PRELOAD_SESSION_COUNT = maxPreloadSessionCount;
            return this;
        }
        public Builder setUnavailableTime(long unavailableTime) {
            target.SONIC_UNAVAILABLE_TIME = unavailableTime;
            return this;
        }
        public Builder setCacheVerifyWithSha1(boolean enable) {
            target.VERIFY_CACHE_FILE_WITH_SHA1 = enable;
            return this;
        }
        public SonicConfig build() {
            return target;
        }
    }
}
