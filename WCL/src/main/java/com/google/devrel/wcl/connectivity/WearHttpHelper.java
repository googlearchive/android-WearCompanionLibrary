/*
 * Copyright (C) 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * imitations under the License.
 */

package com.google.devrel.wcl.connectivity;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.StringDef;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.devrel.wcl.Constants;
import com.google.devrel.wcl.Utils;
import com.google.devrel.wcl.WearManager;
import com.google.devrel.wcl.callbacks.AbstractWearConsumer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

/**
 * A utility class to enable a wear application making HTTP requests over the network via the
 * paired phone (if one is connected) or directly (if wifi is available). When using a connected
 * paired device, this utility encapsulates the http request and sends it to the paired device where
 * it then makes the actual network call. The result will then be sent back to the wearable device
 * and this class captures the response.
 * <p/>
 * A typical usage is shown below:
 * <pre>
 * new WearHttpHelper.Builder(url, context)
 *    .setHttpMethod(HttpUtils.METHOD_GET) // optional, GET is default
 *    .setTargetNodeId(nodeId)
 *    .setHttpResponseListener(myResponseListener)
 *    .setTimeout(10000) // default is 15000 ms = 15 seconds
 *    .build()
 *    .makeHttpRequest();
 * </pre>
 * Callers can register a {@link WearHttpHelper.OnHttpResponseListener} listener to be
 * notified of the response and the corresponding status code. It is assumed that the response
 * is of the type {@code String}. Both GET and POST are supported and clients can set a timeout
 * for the call. For GET requests, query parameters should be included in the url but for POST
 * requests, they should be provided separately. Setters in this class can be chained.
 */
public class WearHttpHelper {

    private static final String TAG = "HttpUtils";
    public static final String KEY_REQUEST_ID = "wear-utils:http-request-id";
    public static final String KEY_STATUS_CODE = "wear-utils:http-status-code";
    public static final String KEY_URL = "wear-utils:http-url";
    public static final String KEY_RESPONSE_DATA = "wear-utils:http-response-data";
    public static final String KEY_CHARSET = "wear-utils:charset";
    public static final String KEY_METHOD_TYPE = "wear-utils:method-type";
    public static final String KEY_QUERY_PARAMS = "wear-utils:query-params";
    private static final long TIMEOUT_MS = 15000L; //default timeout (15 seconds)
    public static final String METHOD_GET = "GET";
    public static final String METHOD_POST = "POST";
    private static final String DEFAULT_CHARSET = "UTF-8";
    private final Handler mHandler;
    private boolean mIsCalled;
    private FutureTask<Void> mFuture;
    private ExecutorService mExecutorService;

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({METHOD_GET, METHOD_POST})
    public @interface HttpMethods {}

    /** Additional Error codes **/
    public static final int ERROR_REQUEST_FAILED = -1;
    public static final int ERROR_TIMEOUT = -2;

    private final String mUrl;
    private final String mRequestId;
    private final Context mContext;
    private final WearManager mWearManager;
    private final AbstractWearConsumer mWearConsumer;
    private final String mHttpMethod;
    private OnHttpResponseListener mListener;
    private final String mNodeId;
    private final long mTimeout;
    private final String mQueryParams;
    private final String mCharset;
    private Timer mTimer;
    private TimerTask mTimerTask;

    /**
     * A Builder class to help with building a {@link WearHttpHelper}. The usage pattern is like the
     * following:
     * <pre>
     * new WearHttpHelper.Builder(url, context)
     *    .setHttpMethod(HttpUtils.METHOD_GET) // optional, GET is default
     *    .setTargetNodeId(nodeId)
     *    .setHttpResponseListener(myResponseListener)
     *    .setTimeout(10000) // default is 15000 ms = 15 seconds
     *    .build()
     *    .makeHttpRequest();
     * </pre>
     */
    public static final class Builder {

        private String mUrl;
        private Context mContext;
        private String mHttpMethod = METHOD_GET;
        private OnHttpResponseListener mListener;
        private String mNodeId;
        private long mTimeout = TIMEOUT_MS;
        private String mQueryParams;
        private String mCharset = DEFAULT_CHARSET;

        /**
         * The Builder for the {@link WearHttpHelper}. Use this class to construct an instance of
         * {@link WearHttpHelper}.
         *
         * @param url The url for the target call.
         * @param context The {@link Context} which will be used to obtain the status of WiFi
         * connectivity.
         */
        public Builder(String url, Context context) {
            mUrl = url;
            mContext = context;
        }

        public WearHttpHelper build() {
            return new WearHttpHelper(this);
        }

        /**
         * Sets the Http Method of the request; valid values are {@link WearHttpHelper#METHOD_GET}
         * or {@link WearHttpHelper#METHOD_POST}. Default is {@link WearHttpHelper#METHOD_GET}
         */
        public Builder setHttpMethod(@HttpMethods String method) {
            mHttpMethod = method;
            return this;
        }

        /**
         * Registers a {@link WearHttpHelper.OnHttpResponseListener} listener to
         * be notified when the http response is ready.
         */
        public Builder setHttpResponseListener(OnHttpResponseListener listener) {
            mListener = listener;
            return this;
        }

        /**
         * Sets the Charset for the request
         */
        public Builder setCharset(String charset) {
            mCharset = charset;
            return this;
        }

        /**
         * Sets what target node the http request should be sent to for processing.
         */
        public Builder setTargetNodeId(String processingNode) {
            mNodeId = processingNode;
            return this;
        }

        /**
         * Sets the timeout, in milliseconds. If a response to a request is not returned from the
         * handset within this period, the listener that is registered to receive the response will
         * be notified and will be unregistered. If caller does not set the timeout value, the
         * default value of {@link WearHttpHelper#TIMEOUT_MS} milliseconds will be applied.
         *
         * @param timeout in milliseconds
         */
        public Builder setTimeout(long timeout) {
            mTimeout = timeout;
            return this;
        }

        /**
         * Sets the query parameters for a POST request. The format should be
         * {@code param1=value1&param2=value2&..} and all <code>value1, value2, ...</code> should
         * be URLEncoded by the caller. For GET requests, this cannot be used; you should provide
         * the query parameter as part of the URL.
         */
        public Builder setQueryParams(String params) {
            mQueryParams = params;
            return this;
        }
    }

    /**
     * The private constructor that is used in the {@link Builder}
     */
    private WearHttpHelper(Builder builder) {
        mUrl = builder.mUrl;
        mContext = builder.mContext.getApplicationContext();
        mTimeout = builder.mTimeout;
        mNodeId = builder.mNodeId;
        mHttpMethod = builder.mHttpMethod;
        mListener = builder.mListener;
        mCharset = builder.mCharset;
        mQueryParams = builder.mQueryParams;
        mHandler = new Handler(Looper.getMainLooper());
        mRequestId = new Date().getTime() + "-" + new Random().nextLong();
        mWearManager = WearManager.getInstance();
        mWearConsumer = new AbstractWearConsumer() {
            @Override
            public void onWearableMessageReceived(MessageEvent messageEvent) {
                WearHttpHelper.this.onMessageReceived(messageEvent);
            }
        };
        mWearManager.addWearConsumer(mWearConsumer);
    }

    private void makeDirectHttpRequest(String url, String method, String query) throws IOException {
        Utils.LOGD(TAG, "Making the call using makeDirectHttpRequest()");
        HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();
        urlConnection.setRequestProperty("Accept-Charset", mCharset);
        if (METHOD_POST.equals(method)) {
            urlConnection.setDoOutput(true);
            urlConnection.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded;charset=" + mCharset);
            if (!TextUtils.isEmpty(query)) {
                OutputStream output = urlConnection.getOutputStream();
                output.write(query.getBytes(mCharset));
            }
        }
        BufferedReader in = new BufferedReader(
                new InputStreamReader(urlConnection.getInputStream()));
        String inputLine;
        final StringBuilder sb = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            sb.append(inputLine);
        }
        in.close();
        final int statusCode = urlConnection.getResponseCode();
        if (null != mListener) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        mListener.onHttpResponseReceived(mRequestId, statusCode, sb.toString());
                    } catch (Exception e) {
                        Log.e(TAG, "onHttpResponseReceived(): Encountered an exception on the "
                                + "client side", e);
                    }
                }
            });
        }
    }

    /**
     * Sends a message to the target node to perform an HTTP request. Result will be returned via
     * the {@link WearHttpHelper.OnHttpResponseListener} listener, if one is registered. Note that
     * you can call this method only once on a {@link WearHttpHelper} instance.
     */
    public void makeHttpRequest() {
        validateArguments();
        if (mIsCalled) {
            // we don't want to call this multiple times
            throw new IllegalStateException(
                    "Calling this method multiple times on the same instance is not permitted");
        }
        mIsCalled = true;
        if (Utils.getWifiConnectivityStatus(mContext) == Utils.WIFI_CONNECTED) {
            mFuture = new FutureTask<>(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    try {
                        makeDirectHttpRequest(mUrl, mHttpMethod, mQueryParams);
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to make the network call", e);
                    }
                    return null;
                }
            });
            mExecutorService = Executors.newSingleThreadExecutor();
            mExecutorService.execute(mFuture);
            return;
        }
        Utils.LOGD(TAG, "Making the call using the paired device");
        mWearManager.assertApiConnectivity();
        if (TextUtils.isEmpty(mNodeId)) {
            throw new IllegalArgumentException("No target node is specified");
        }

        DataMap dataMap = new DataMap();
        dataMap.putString(KEY_URL, mUrl);
        dataMap.putString(KEY_REQUEST_ID, mRequestId);
        dataMap.putString(KEY_METHOD_TYPE, mHttpMethod);
        dataMap.putString(KEY_CHARSET, mCharset);
        if (METHOD_POST.equals(mHttpMethod) && !TextUtils.isEmpty(mQueryParams)) {
            dataMap.putString(KEY_QUERY_PARAMS, mQueryParams);
        }

        // if we don't receive a response within the timeout, we remove the listener
        mTimerTask = new TimerTask() {
            @Override
            public void run() {
                if (mListener != null) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                mListener.onHttpResponseReceived(mRequestId, ERROR_TIMEOUT, null);
                                mListener = null;
                            } catch (Exception e) {
                                Log.e(TAG, "onHttpResponseReceived(): Encountered an exception on "
                                        + "the client side", e);
                            }
                        }
                    });
                }
                removeListener();
            }
        };
        mTimer = new Timer();
        mTimer.schedule(mTimerTask, mTimeout);

        mWearManager.sendMessage(mNodeId, Constants.PATH_HTTP_REQUEST, dataMap,
                new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                        if (!sendMessageResult.getStatus().isSuccess()) {
                            Utils.LOGD(TAG,
                                    "Failed to send message, statusCode: " + sendMessageResult
                                            .getStatus().getStatusCode());
                            if (null != mListener) {
                                try {
                                    mListener.onHttpResponseReceived(mRequestId,
                                            ERROR_REQUEST_FAILED,
                                            null);
                                    cleanUp();
                                } catch (Exception e) {
                                    //ignore
                                }
                            }
                        }
                    }
                });

    }

    /**
     * A simple validator.
     */
    private void validateArguments() {
        if (!METHOD_GET.equals(mHttpMethod) && !METHOD_POST.equals(mHttpMethod)) {
            throw new IllegalArgumentException("Only POST or GET methods are supported");
        }

        if (METHOD_GET.equals(mHttpMethod) && !TextUtils.isEmpty(mQueryParams)) {
            throw new IllegalArgumentException(
                    "Query parameters for a GET request should be included in the URL");
        }
    }

    private void cleanUp() {
        cancelTimer();
        removeListener();
    }

    /**
     * Aborts the call. This method will cleanUp the resources and removes the listener, if one was
     * registered,
     */
    public void abort() {
        cleanUp();
        if (mFuture != null) {
            mFuture.cancel(true);
            mFuture = null;
        }

        if (mExecutorService != null) {
            mExecutorService.shutdownNow();
            mExecutorService = null;
        }
        mListener = null;
    }

    private void cancelTimer() {
        if (mTimerTask != null) {
            mTimerTask.cancel();
            mTimerTask = null;
        }
        if (null != mTimer) {
            mTimer.cancel();
            mTimer = null;
        }
    }

    private void removeListener() {
        mWearManager.removeWearConsumer(mWearConsumer);
    }

    /**
     * The interface for receiving the result of http request. Clients that are interested to
     * receive the response to the HTTP request can implement this interface and register
     * themselves.
     *
     * @see Builder#setHttpResponseListener(WearHttpHelper.OnHttpResponseListener)
     */
    public interface OnHttpResponseListener {

        /**
         * Called, on the main thread, when the response to the HTTP request is available.
         *
         * @param requestId A unique id that was created and associated with this request
         * @param status The status code of the response. Positive numbers reflect the
         * HTTP status and negative values are custom errors, defined in the {@link WearHttpHelper}
         * class.
         * @param response The text-based response of the request. If there is an error, this can be
         * <code>null</code>
         */
        void onHttpResponseReceived(String requestId, int status, String response);
    }

    public String getRequestId() {
        return mRequestId;
    }

    public void onMessageReceived(MessageEvent messageEvent) {
        if (null == messageEvent || null == messageEvent.getData() || !mIsCalled) {
            return;
        }
        String nodeId = messageEvent.getSourceNodeId();
        final byte[] data = messageEvent.getData();
        final DataMap dataMap = DataMap.fromByteArray(data);
        final String requestId = dataMap.get(KEY_REQUEST_ID);
        if (Constants.PATH_HTTP_RESPONSE.equals(messageEvent.getPath()) &&
                mNodeId.equals(nodeId) &&
                mRequestId.equals(requestId)) {
            // we have a message back for the same call
            final int statusCode = dataMap.getInt(KEY_STATUS_CODE);
            final String response = dataMap.getString(KEY_RESPONSE_DATA);
            if (null != mListener) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            mListener.onHttpResponseReceived(mRequestId, statusCode, response);
                        } catch (Exception e) {
                            Log.e(TAG, "onHttpResponseReceived(): Encountered an exception on the "
                                            + "client side", e);
                        }
                    }
                });
            }
            cleanUp();
        }
    }

}
