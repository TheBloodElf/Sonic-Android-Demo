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

import java.net.HttpURLConnection;


//该类提供会话的一些数据
public class SonicSessionStatistics {
    //原地址
    public String srcUrl;

    /**
     * Sonic final mode{@link SonicSession#finalResultCode}
     */
    public int finalMode;

    /**
     * Sonic original mode{@link SonicSession#srcResultCode}
     */
    public int originalMode;
    //Sonic开始时间
    public long sonicStartTime;

    /**
     * Sonic flow start{@link SonicSession#runSonicFlow()} time
     */
    public long sonicFlowStartTime;

    /**
     * The time that sonic begin verify local data
     */
    public long cacheVerifyTime;

    /**
     * The time sonic initiate the http(s) request
     */
    public long connectionFlowStartTime;

    /**
     * The http(s) connect{@link HttpURLConnection#connect()} response time
     */
    public long connectionConnectTime;

    /**
     * The http(s) getResponseCode{@link HttpURLConnection#getResponseCode()} response time
     */
    public long connectionRespondTime;

    /**
     * Sonic flow end time
     */
    public long connectionFlowFinishTime;

    /**
     * Is IP direct
     */
    public boolean isDirectAddress;
}
