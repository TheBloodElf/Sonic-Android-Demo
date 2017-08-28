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


import android.text.TextUtils;
import android.util.Log;

import java.io.File;

/**
 * <code>SonicCacheInterceptor</code> provide local data.
 * if a {@link SonicSessionConfig} does not set a sonicCacheInterceptor
 * sonic will use {@link SonicCacheInterceptorDefaultImpl} as default.
 *
 */
public abstract class SonicCacheInterceptor {

    public static final String TAG = SonicConstants.SONIC_SDK_LOG_PREFIX + "SonicCacheInterceptor";

    private final SonicCacheInterceptor nextInterceptor;

    public SonicCacheInterceptor(SonicCacheInterceptor next) {
        nextInterceptor = next;
    }

    public SonicCacheInterceptor next() {
        return nextInterceptor;
    }

    public abstract String getCacheData(SonicSession session);
    //获取该会话缓存的网页数据
    static String getSonicCacheData(SonicSession session) {
        //获取SonicSessionConfig设置的本地缓存数据提供者
        SonicCacheInterceptor interceptor = session.config.cacheInterceptor;
        //如果用户没有配置，就使用默认的SonicCacheInterceptorDefaultImpl提供本地数据缓存
        if (null == interceptor) {
            return SonicCacheInterceptorDefaultImpl.getCacheData(session);
        }
        String htmlString = null;
        while (null != interceptor) {
            htmlString = interceptor.getCacheData(session);
            if (null != htmlString) {
                break;
            }
            interceptor = interceptor.next();
        }
        return htmlString;
    }
    //默认的本地缓存数据提供者
    private static class SonicCacheInterceptorDefaultImpl {
        public static final String TAG = SonicConstants.SONIC_SDK_LOG_PREFIX + "DefaultSonicCacheInterceptor";
        //得到该会话对应的本地缓存数据
        public static String getCacheData(SonicSession session) {
            //判断会话是否有效
            if (session == null) {
                SonicUtils.log(TAG, Log.INFO, "getCache is null");
                return null;
            }
            //SessionData表示每个会话对应的缓存数据
            //获取该会话对应的缓存SessionData
            SonicDataHelper.SessionData sessionData = SonicDataHelper.getSessionData(session.id);
            boolean verifyError;
            String htmlString = "";
            //验证本地数据 如果任何一个为null表示缓存数据不存在
            if (TextUtils.isEmpty(sessionData.etag) || TextUtils.isEmpty(sessionData.templateTag) ||
                    TextUtils.isEmpty(sessionData.htmlSha1)) {
                verifyError = true;
                SonicUtils.log(TAG, Log.INFO, "session(" + session.sId + ") runSonicFlow : session data is empty.");
            } else {//如果本地缓存数据存在
                //获取会话对应的html缓存文件
                File htmlCacheFile = new File(SonicFileUtils.getSonicHtmlPath(session.id));
                //读取文件内容
                htmlString = SonicFileUtils.readFile(htmlCacheFile);
                //看文件内容是否为空
                verifyError = TextUtils.isEmpty(htmlString);
                if (verifyError) {
                    SonicUtils.log(TAG, Log.ERROR, "session(" + session.sId + ") runSonicFlow error:cache data is null.");
                } else {//如果文件内容不为空
                    //是否对文件内容进行sha加密
                    if (SonicEngine.getInstance().getConfig().VERIFY_CACHE_FILE_WITH_SHA1) {
                        //缓存的html和sha不对应 则验证失败
                        if (!SonicFileUtils.verifyData(htmlString, sessionData.htmlSha1)) {
                            verifyError = true;
                            htmlString = "";
                            //通知一下验证html内容失败
                            SonicEngine.getInstance().getRuntime().notifyError(session.sessionClient, session.srcUrl, SonicConstants.ERROR_CODE_DATA_VERIFY_FAIL);
                            SonicUtils.log(TAG, Log.ERROR, "session(" + session.sId + ") runSonicFlow error:verify html cache with sha1 fail.");
                        } else {
                            SonicUtils.log(TAG, Log.INFO, "session(" + session.sId + ") runSonicFlow verify html cache with sha1 success.");
                        }
                    } else {
                        if (sessionData.htmlSize != htmlCacheFile.length()) {
                            verifyError = true;
                            htmlString = "";
                            //通知一下验证html内容失败
                            SonicEngine.getInstance().getRuntime().notifyError(session.sessionClient, session.srcUrl, SonicConstants.ERROR_CODE_DATA_VERIFY_FAIL);
                            SonicUtils.log(TAG, Log.ERROR, "session(" + session.sId + ") runSonicFlow error:verify html cache with size fail.");
                        }
                    }
                }
            }
            //如果本地数据验证错误，就删除本地保存的数据
            if (verifyError) {
                long startTime = System.currentTimeMillis();
                //删除会话对应的缓存
                SonicUtils.removeSessionCache(session.id);
                sessionData.reset();
                SonicUtils.log(TAG, Log.INFO, "session(" + session.sId + ") runSonicFlow:verify error so remove session cache, cost " + +(System.currentTimeMillis() - startTime) + "ms.");
            }
            return htmlString;
        }
    }
}
