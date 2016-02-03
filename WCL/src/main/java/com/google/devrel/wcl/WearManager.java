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

package com.google.devrel.wcl;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.ChannelApi;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.devrel.wcl.callbacks.AbstractWearConsumer;
import com.google.devrel.wcl.callbacks.WearConsumer;
import com.google.devrel.wcl.connectivity.WearFileTransfer;
import com.google.devrel.wcl.connectivity.WearHttpHelper;
import com.google.devrel.wcl.filters.NearbyFilter;
import com.google.devrel.wcl.filters.NodeSelectionFilter;
import com.google.devrel.wcl.widgets.list.WclWearableListViewActivity;
import com.google.devrel.wcl.widgets.list.WearableListConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

/**
 * A singleton to manage the wear interaction between a device and its connected devices.
 * This class maintains certain states of the system and hides direct interaction with the
 * {@link GoogleApiClient}. Clients should initialize this singleton as early as possible (e.g. in
 * the onCreate() of the Application instance). Then, accessing this singleton instance is possible
 * throughout the client application by calling {@link #getInstance()}.
 *
 * <p>In most cases, components within the client application need to have access to certain events
 * and receive messages, data items, etc. Each component can register an implementation of the
 * {@link WearConsumer} interface with the {@link WearManager}. Then, the relevant callbacks
 * within that interface will be called when an event is received or a life-cycle change
 * happens. To make this easier, {@link AbstractWearConsumer}
 * provides a no-op implementation of the {@link WearConsumer} which clients can extend
 * and only override the callbacks that they are interested in.
 */
public class WearManager {

    private static final String TAG = "WearManager";
    private static final String KEY_START_ACTIVITY_NAME
            = "com.google.devrel.wcl:KEY_START_ACTIVITY_NAME";
    private static final String KEY_START_ACTIVITY_BUNDLE
            = "com.google.devrel.wcl.KEY_START_ACTIVITY_BUNDLE";
    private static final String KEY_START_ACTIVITY_RELAUNCH
            = "com.google.devrel.wcl.KEY_START_ACTIVITY_RELAUNCH";
    private static WearManager sInstance;
    private final Context mContext;
    private final String[] mCapabilitiesToBeAdded;
    private GoogleApiClient mGoogleApiClient;
    private final Set<WearConsumer> mWearConsumers = new CopyOnWriteArraySet<>();
    private final Set<String> mWatchedCapabilities = new CopyOnWriteArraySet<>();
    private final Set<Node> mConnectedNodes = new CopyOnWriteArraySet<>();
    private final Map<String, Set<Node>> mCapabilityToNodesMapping = Collections
            .synchronizedMap(new HashMap<String, Set<Node>>());
    private final String mWclVersion;
    private boolean mAppForeground;

    /**
     * The private constructor which is called internally by the
     * {@link #initialize(Context, String...)} method.
     */
    private WearManager(Context context, String... capabilitiesToBeAdded) {
        mContext = context;
        mCapabilitiesToBeAdded = capabilitiesToBeAdded != null ? Arrays.copyOf(
                capabilitiesToBeAdded, capabilitiesToBeAdded.length) : null;
        mWclVersion = context.getString(R.string.wcl_version);
        Log.i(TAG, "******** Wear Companion Library version " + mWclVersion + " ********");
    }

    /**
     * A static method to get a hold of this singleton after it is initialized. If a client calls
     * this method prior to the initialization of this singleton, an {@link IllegalStateException}
     * exception will be thrown.
     */
    public static WearManager getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException(
                    "No instance of WearManager was found, did you forget to call initialize()?");
        }
        return sInstance;
    }

    private void initialize() {
        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                .addApi(Wearable.API)
                .addConnectionCallbacks(new MyConnectionCallbacksListener())
                .addOnConnectionFailedListener(new MyConnectionFailedListener())
                .build();
        mGoogleApiClient.connect();
        ApplicationVisibilityDetector visibilityDetector = ApplicationVisibilityDetector
                .forApp((Application) mContext);
        visibilityDetector.addListener(new MyApplicationVisibilityDetectorListener());
    }

    /**
     * Initializes this singleton. Clients should call this prior to calling any other public
     * method on this singleton. It is required to call this method from within the
     * {@link Application#onCreate()} method of the Application instance of the client application.
     *
     * <p>A {@link Context} is required but this singleton only holds a reference to the
     * (Application) context. Clients can also decide to declare zero or more capabilities to be
     * declared at runtime. Note that at any later time, clients can add or remove any capabilities
     * that are declared at runtime. Here, we set up the Google Api Client
     * instance and call the connect on it.
     *
     * @param context A context. An application context will be extracted from this to avoid having
     * a reference to any other type of context.
     * @param capabilities (optional) zero or more capabilities, to be declared dynamically by the
     * caller client.
     */
    public static synchronized WearManager initialize(Context context, String... capabilities) {
        if (sInstance == null) {
            sInstance = new WearManager(context.getApplicationContext(), capabilities);
            sInstance.initialize();
        }
        return sInstance;
    }

    /**
     * Sends an asynchronous message to the node with the given {@code nodeId}. If the
     * {@code callback} is null, then a default callback will be used that provides a feedback to
     * the caller using the {@link WearConsumer#onWearableSendMessageResult}, in which case, the
     * status of the result will be made available. Callers may decide to provide their own
     * {@code callback} to be used instead. This variant receives the message in a {@link DataMap}.
     */
    public void sendMessage(String nodeId, String path, @Nullable DataMap dataMap,
            @Nullable ResultCallback<? super MessageApi.SendMessageResult> callback) {
        sendMessage(nodeId, path, dataMap != null ? dataMap.toByteArray() : null, callback);
    }

    /**
     * Sends an asynchronous message to the node with the given {@code nodeId}. If the
     * {@code callback} is null, then a default callback will be used that provides a feedback to
     * the caller using the {@link WearConsumer#onWearableSendMessageResult}, in which case, the
     * status of the result will be made available. Callers may decide to provide their own
     * {@code callback} to be used instead. This variant receives the message in an array of bytes.
     */
    public void sendMessage(String nodeId, String path, @Nullable byte[] bytes,
            @Nullable final ResultCallback<? super MessageApi.SendMessageResult> callback) {
        assertApiConnectivity();
        Wearable.MessageApi.sendMessage(mGoogleApiClient, nodeId, path, bytes).setResultCallback(
                new ResultCallback<MessageApi.SendMessageResult>() {
                    @Override
                    public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                        if (!sendMessageResult.getStatus().isSuccess()) {
                            Log.e(TAG, "Failed to send message, statusCode: " + sendMessageResult
                                    .getStatus().getStatusCode());
                        }
                        if (callback == null) {
                            for (WearConsumer consumer : mWearConsumers) {
                                consumer.onWearableSendMessageResult(sendMessageResult.getStatus()
                                        .getStatusCode());
                            }
                        } else {
                            callback.onResult(sendMessageResult);
                        }
                    }
                });
    }

    /**
     * Sends an asynchronous message to the node with the given {@code nodeId}.
     * A default callback will be used that provides a feedback to the caller using the
     * {@link WearConsumer#onWearableSendMessageResult} where the status of the result will be
     * made available. To provide your own callback, use
     * {@link #sendMessage(String, String, byte[], ResultCallback)}.
     *
     * @see WearManager#sendMessage(String, String, byte[], ResultCallback)
     */
    public void sendMessage(String nodeId, String path, @Nullable byte[] bytes) {
        sendMessage(nodeId, path, bytes, null);
    }

    /**
     * Sends an HTTP response to a node inside an asynchronous custom message. When a node makes an
     * HTTP Request using the {@link WearHttpHelper} class, a node capable of fulfilling that request
     * will be notified if it is registered to
     * {@link WearConsumer#onWearableHttpRequestReceived(String, String, String, String, String, String)}
     * After processing the request, that node can send the results back to the originating node by
     * calling this method and providing the result.
     *
     * @param response The response to be sent back to the originator of the request; it can be
     * {@code null}
     * @param status The status of the response
     * @param nodeId The nodeId of the originator; this can be obtained from the callback that
     * made the http request
     * @param requestId A unique identifier that was received from the original callback that made
     * the http request
     * @param callback A callback to receive the result. If {@code null}, a default callback will be
     * used, see {@link #sendMessage(String, String, byte[], ResultCallback)}
     */
    public void sendHttpResponse(@Nullable String response, int status, String nodeId,
            String requestId, ResultCallback<? super MessageApi.SendMessageResult> callback) {
        Utils.assertNotEmpty(nodeId, "nodeId");
        Utils.assertNotEmpty(requestId, "requestId");
        final DataMap dataMap = new DataMap();
        dataMap.putString(WearHttpHelper.KEY_REQUEST_ID, requestId);
        dataMap.putString(WearHttpHelper.KEY_RESPONSE_DATA, response);
        dataMap.putInt(WearHttpHelper.KEY_STATUS_CODE, status);
        sendMessage(nodeId, Constants.PATH_HTTP_RESPONSE, dataMap, callback);
    }

    /**
     * Adds a data item asynchronously. Caller can specify a {@link ResultCallback} or pass a
     * {@code null}; if {@code null} is passed, a default {@link ResultCallback} will be used which
     * will call {@link WearConsumer#onWearableSendDataResult(int)} and passes the status code of
     * the result.
     *
     * @see #putDataItem(PutDataRequest)
     */
    public void putDataItem(PutDataRequest request,
            @Nullable final ResultCallback<? super DataApi.DataItemResult> callback) {
        assertApiConnectivity();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request).setResultCallback(
                new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        if (!dataItemResult.getStatus().isSuccess()) {
                            Log.e(TAG, "Failed to send data, status code = " + dataItemResult
                                    .getStatus()
                                    .getStatusCode());
                        }
                        if (callback == null) {
                            for (WearConsumer consumer : mWearConsumers) {
                                consumer.onWearableSendDataResult(dataItemResult.getStatus()
                                        .getStatusCode());
                            }
                        } else {
                            callback.onResult(dataItemResult);
                        }
                    }
                });
    }

    /**
     * Adds a data item asynchronously. A default {@link ResultCallback} will be used to capture the
     * result of this call.
     *
     * @see #putDataItem(PutDataRequest, ResultCallback)
     */
    public void putDataItem(PutDataRequest request) {
        putDataItem(request, null);
    }


    /**
     * Adds a data item  <b>synchronously</b>. This should be called only on non-UI threads.
     * A {@code timeoutInMillis} is required to specify the
     * maximum length of time, in milliseconds, that the thread should be blocked.
     */
    public int putDataItemSynchronous(PutDataRequest request, long timeoutInMillis) {
        assertApiConnectivity();
        Utils.assertNonUiThread();
        DataApi.DataItemResult result = Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .await(timeoutInMillis, TimeUnit.MILLISECONDS);
        return result.getStatus().getStatusCode();
    }

    /**
     * Adds a {@code bitmap} image to a data item asynchronously. Caller can
     * specify a {@link ResultCallback} or pass a {@code null}; if a {@code null} is passed, a
     * default {@link ResultCallback} will be used (see
     * {@link #putDataItem(PutDataRequest, ResultCallback)} for details).
     *
     * @param bitmap The bitmap to be added.
     * @param path The path for the data item.
     * @param key The key to be used for this item in the data map.
     * @param isUrgent If {@code true}, request will be set as urgent.
     * @param addTimestamp If {@code true}, adds a timestamp to the data map to always create a new
     * data item even if an identical data item with the same bitmap has already been added
     * @param callback The callback to be notified of the result (can be {@code null}).
     */
    public void putImageData(Bitmap bitmap, String path, String key, boolean isUrgent,
            boolean addTimestamp,
            @Nullable ResultCallback<? super DataApi.DataItemResult> callback) {
        Utils.assertNotNull(bitmap, "bitmap");
        Utils.assertNotEmpty(path, "path");
        Utils.assertNotEmpty(key, "key");
        Asset imageAsset = Utils.toAsset(bitmap);
        PutDataMapRequest dataMap = PutDataMapRequest.create(path);
        dataMap.getDataMap().putAsset(key, imageAsset);
        if (addTimestamp) {
            dataMap.getDataMap().putLong(Constants.KEY_TIMESTAMP, new Date().getTime());
        }
        PutDataRequest request = dataMap.asPutDataRequest();
        if (isUrgent) {
            request.setUrgent();
        }
        putDataItem(request, callback);
    }

    /**
     * Retrieves data items asynchronously. Caller can specify a {@link ResultCallback} or pass a
     * {@code null}; if a {@code null} is passed, a default {@link ResultCallback} will be used that
     * calls {@link WearConsumer#onWearableGetDataItems(int, DataItemBuffer)}.
     *
     * @see WearConsumer#onWearableGetDataItems(int, DataItemBuffer)
     */
    public void getDataItems(@Nullable final ResultCallback<? super DataItemBuffer> callback) {
        assertApiConnectivity();
        Wearable.DataApi.getDataItems(mGoogleApiClient).setResultCallback(
                new ResultCallback<DataItemBuffer>() {
                    @Override
                    public void onResult(DataItemBuffer dataItems) {
                        try {
                            int statusCode = dataItems.getStatus().getStatusCode();
                            if (!dataItems.getStatus().isSuccess()) {
                                Log.e(TAG, "Failed to get items, status code: " + statusCode);
                            }
                            if (callback == null) {
                                for (WearConsumer consumer : mWearConsumers) {
                                    consumer.onWearableGetDataItems(statusCode, dataItems);
                                }
                            } else {
                                callback.onResult(dataItems);
                            }
                        } finally {
                            dataItems.release();
                        }
                    }
                });
    }

    /**
     * Retrieves data items asynchronously from the Android Wear network, matching the provided URI
     * and filter type. Caller can specify a {@link ResultCallback} or pass a
     * {@code null}; if a {@code null} is passed, a default {@link ResultCallback} will be used that
     * calls {@link WearConsumer#onWearableGetDataItems(int, DataItemBuffer)}.
     *
     * @see DataApi#getDataItems(GoogleApiClient, Uri, int)
     */
    public void getDataItems(Uri uri, int filterType,
            @Nullable final ResultCallback<? super DataItemBuffer> callback) {
        assertApiConnectivity();
        Wearable.DataApi.getDataItems(mGoogleApiClient, uri, filterType).setResultCallback(
                new ResultCallback<DataItemBuffer>() {
                    @Override
                    public void onResult(DataItemBuffer dataItems) {
                        int statusCode = dataItems.getStatus().getStatusCode();
                        if (!dataItems.getStatus().isSuccess()) {
                            Log.e(TAG, "Failed to get items, status code: " + statusCode);
                        }
                        if (callback == null) {
                            for (WearConsumer consumer : mWearConsumers) {
                                consumer.onWearableGetDataItems(statusCode, dataItems);
                            }
                        } else {
                            callback.onResult(dataItems);
                        }
                        dataItems.release();
                    }
                });
    }

    /**
     * Retrieves data items asynchronously from the Android Wear network. A {@code timeoutInMillis}
     * is required to specify the maximum length of time, in milliseconds, that the thread should be
     * blocked. Caller needs to call {@code release()} on the returned {@link DataItemBuffer} when
     * done.
     */
    public DataItemBuffer getDataItemsSynchronous(long timeoutInMillis) {
        assertApiConnectivity();
        Utils.assertNonUiThread();
        return Wearable.DataApi.getDataItems(mGoogleApiClient).await(
                timeoutInMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Retrieves data items <b>synchronously</b> from the Android Wear network, matching the
     * provided URI and filter type. This should only be called on a non-UI thread.
     * A {@code timeoutInMillis} is required to specify the
     * maximum length of time, in milliseconds, that the thread should be blocked. Note that caller
     * needs to call {@code release()} on the returned {@link DataItemBuffer} when done.
     */
    public DataItemBuffer getDataItemsSynchronous(Uri uri, int filterType, long timeoutInMillis) {
        assertApiConnectivity();
        Utils.assertNonUiThread();
        return Wearable.DataApi.getDataItems(mGoogleApiClient, uri, filterType).await(
                timeoutInMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Retrieves a single data item with the given {@code dataItemUri} asynchronously. Caller can
     * specify a {@link ResultCallback} or pass a {@code null}; if a {@code null} is passed, a
     * default {@link ResultCallback} will be used that will call
     * {@link WearConsumer#onWearableGetDataItem(int, DataApi.DataItemResult)}.
     *
     * @see WearConsumer#onWearableGetDataItem(int, DataApi.DataItemResult)
     */
    public void getDataItem(Uri dataItemUri,
            @Nullable final ResultCallback<? super DataApi.DataItemResult> callback) {
        assertApiConnectivity();
        Wearable.DataApi.getDataItem(mGoogleApiClient, dataItemUri).setResultCallback(
                new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        int statusCode = dataItemResult.getStatus().getStatusCode();
                        if (!dataItemResult.getStatus().isSuccess()) {
                            Log.e(TAG, "Failed to get the data item, status code: " + statusCode);
                        }
                        if (callback == null) {
                            for (WearConsumer consumer : mWearConsumers) {
                                consumer.onWearableGetDataItem(statusCode, dataItemResult);
                            }
                        } else {
                            callback.onResult(dataItemResult);
                        }
                    }
                });
    }

    /**
     * Retrieves data items with the given {@code dataItemUri} <b>synchronously</b>.
     * This should be called on non-UI threads. A {@code timeoutInMillis} is required to specify the
     * maximum length of time, in milliseconds, that the thread should be blocked.
     */
    public DataApi.DataItemResult getDataItemSynchronous(Uri dataItemUri, long timeoutInMillis) {
        assertApiConnectivity();
        Utils.assertNonUiThread();
        return Wearable.DataApi.getDataItem(mGoogleApiClient, dataItemUri).await(
                timeoutInMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Deletes a data items asynchronously. Caller can specify a {@link ResultCallback} or
     * pass a {@code null}; if a {@code null} is passed, a default {@link ResultCallback} will be
     * used that would call {@link WearConsumer#onWearableDeleteDataItemsResult(int)}.
     */
    public void deleteDataItems(final Uri dataItemUri,
            @Nullable final ResultCallback<? super DataApi.DeleteDataItemsResult> callback) {
        assertApiConnectivity();
        Wearable.DataApi.deleteDataItems(mGoogleApiClient, dataItemUri).setResultCallback(
                new ResultCallback<DataApi.DeleteDataItemsResult>() {
                    @Override
                    public void onResult(DataApi.DeleteDataItemsResult deleteDataItemsResult) {
                        int statusCode = deleteDataItemsResult.getStatus().getStatusCode();
                        if (!deleteDataItemsResult.getStatus().isSuccess()) {
                            Log.e(TAG, String.format(
                                    "Failed to delete data items (status code=%d): %s",
                                    statusCode, dataItemUri));
                        }
                        if (callback == null) {
                            for (WearConsumer consumer : mWearConsumers) {
                                consumer.onWearableDeleteDataItemsResult(statusCode);
                            }
                        } else {
                            callback.onResult(deleteDataItemsResult);
                        }
                    }
                });
    }

    /**
     * Deletes a data items <b>synchronously</b>. This should
     * only be called on a non-UI thread. A {@code timeoutInMillis} is required to specify the
     * maximum length of time, in milliseconds, that the thread should be blocked.
     */
    public DataApi.DeleteDataItemsResult deleteDataItemsSynchronous(final Uri dataItemUri,
            long timeoutInMillis) {
        assertApiConnectivity();
        Utils.assertNonUiThread();
        return Wearable.DataApi.deleteDataItems(mGoogleApiClient, dataItemUri)
                .await(timeoutInMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Adds one or more capabilities to the client at runtime. Make sure you balance this with a
     * similar call to {@link #removeCapabilities(String...)}
     *
     * @see #removeCapabilities(String...)
     */
    public void addCapabilities(String... capabilities) {
        if (capabilities == null || capabilities.length == 0) {
            return;
        }
        assertApiConnectivity();
        for (final String capability : capabilities) {
            Wearable.CapabilityApi.addLocalCapability(mGoogleApiClient, capability)
                    .setResultCallback(
                            new ResultCallback<CapabilityApi.AddLocalCapabilityResult>() {
                                @Override
                                public void onResult(
                                        CapabilityApi.AddLocalCapabilityResult
                                                addLocalCapabilityResult) {
                                    if (!addLocalCapabilityResult.getStatus().isSuccess()) {
                                        Log.e(TAG, "Failed to add the capability " + capability);
                                    } else {
                                        mWatchedCapabilities.add(capability);
                                    }
                                    for (WearConsumer consumer : mWearConsumers) {
                                        consumer.onWearableAddCapabilityResult(
                                                addLocalCapabilityResult.getStatus()
                                                        .getStatusCode());
                                    }
                                }
                            });
        }
    }
    /**
     * Removes one or more capabilities from the client at runtime.
     *
     * @see #addCapabilities(String...)
     */
    public void removeCapabilities(String... capabilities) {
        if (capabilities == null || capabilities.length == 0) {
            return;
        }
        assertApiConnectivity();
        for (final String capability : capabilities) {
            Wearable.CapabilityApi.removeLocalCapability(mGoogleApiClient, capability)
                    .setResultCallback(
                            new ResultCallback<CapabilityApi.RemoveLocalCapabilityResult>() {
                                @Override
                                public void onResult(
                                        CapabilityApi.RemoveLocalCapabilityResult
                                                removeLocalCapabilityResult) {
                                    if (!removeLocalCapabilityResult.getStatus().isSuccess()) {
                                        Log.e(TAG, "Failed to remove the capability " + capability);
                                    } else {
                                        mWatchedCapabilities.remove(capability);
                                    }
                                    for (WearConsumer consumer : mWearConsumers) {
                                        consumer.onWearableRemoveCapabilityResult(
                                                removeLocalCapabilityResult.getStatus()
                                                        .getStatusCode());
                                    }
                                }
                            });
        }
    }

    /**
     * This method is used to assert that we are connected to the Google Api Client for Wearable
     * APIs. If not, it throws an {@link IllegalStateException}.
     */
    public void assertApiConnectivity() {
        if (!isConnected()) {
            throw new IllegalStateException("Google API Client is not connected");
        }
    }

    /**
     * Adds the {@link WearConsumer} to be managed by this singleton to receive changes in
     * lifecycle or other important callbacks for various event. Calls to this method should be
     * balanced by the calls to {@link #removeWearConsumer(WearConsumer)} to avoid leaks. Clients
     * should consider building a {@link WearConsumer} by extending
     * {@link AbstractWearConsumer} instead of implementing {@link WearConsumer} directly.
     *
     * @see AbstractWearConsumer
     */
    public void addWearConsumer(WearConsumer consumer) {
        mWearConsumers.add(Utils.assertNotNull(consumer, "consumer"));
        // if we were connected to the Google Api Client earlier, let's call the
        // onWearableApiConnected() on new consumer manually since it won't be called again
        if (isConnected()) {
            consumer.onWearableApiConnected();
        }
    }

    /**
     * Removes the {@link WearConsumer} from this singleton. This should be when there is no need
     * for the registered {@link WearConsumer} to avoid leaks.
     *
     * @see #addWearConsumer(WearConsumer)
     */
    public void removeWearConsumer(WearConsumer consumer) {
        mWearConsumers.remove(Utils.assertNotNull(consumer, "consumer"));
    }

    /**
     * Clients can register to {@link WearConsumer#onWearableApiConnected()}.
     */
    private void onConnected(Bundle bundle) {
        Utils.LOGD(TAG, "Google Api Connected");
        for (WearConsumer consumer : mWearConsumers) {
            consumer.onWearableApiConnected();
        }
        addCapabilities(mCapabilitiesToBeAdded);
        Wearable.CapabilityApi.getAllCapabilities(mGoogleApiClient,
                CapabilityApi.FILTER_REACHABLE).setResultCallback(

                new ResultCallback<CapabilityApi.GetAllCapabilitiesResult>() {
                    @Override
                    public void onResult(
                            CapabilityApi.GetAllCapabilitiesResult getAllCapabilitiesResult) {
                        if (getAllCapabilitiesResult.getStatus().isSuccess()) {
                            Map<String, CapabilityInfo> capabilities = getAllCapabilitiesResult
                                    .getAllCapabilities();
                            if (capabilities != null) {
                                for (String capability : capabilities.keySet()) {
                                    CapabilityInfo info = capabilities.get(capability);
                                    mCapabilityToNodesMapping.put(capability, info.getNodes());
                                }
                            }
                            onConnectedInitialCapabilitiesReceived();
                        } else {
                            Log.e(TAG, "getAllCapabilities(): Failed to get all the capabilities");
                        }
                    }
                });
        Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).setResultCallback(
                new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                    @Override
                    public void onResult(NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                        if (getConnectedNodesResult.getStatus().isSuccess()) {
                            mConnectedNodes.clear();
                            mConnectedNodes.addAll(getConnectedNodesResult.getNodes());
                            onConnectedInitialNodesReceived();
                        }
                    }
                });
    }

    /**
     * Returns a view of the set of currently connected nodes. The returned set will change as nodes
     * connect and disconnect from the Wear network. The set is safe to access from multiple
     * threads. Note that this may contain cloud, as well as nodes that are not directly connected
     * to this node.
     */
    public Set<Node> getConnectedNodes() {
        return Collections.unmodifiableSet(mConnectedNodes);
    }

    /**
     * Returns {@code true} if and only of the Google Api Client for Wearable APIs is connected.
     */
    public boolean isConnected() {
        return mGoogleApiClient.isConnected();
    }

    /**
     * Returns the current set of connected nodes that provide the given {@code capability}.
     *
     * @see #getNodesForCapability(String, NodeSelectionFilter)
     */
    public Set<Node> getNodesForCapability(String capability) {
        if (TextUtils.isEmpty(capability)) {
            throw new IllegalArgumentException(
                    "getNodesForCapability(): Capability cannot be null or empty");
        }
        return mCapabilityToNodesMapping.get(capability);
    }

    /**
     * Returns the current set of connected nodes that provide the given {@code capability} and
     * further narrowed down by the provided {@code filter}. If no such node exists, it returns an
     * empty set. Note that {@code filter} cannot be {@code null}.
     *
     * @see #getNodesForCapability(String)
     */
    public Set<Node> getNodesForCapability(String capability, NodeSelectionFilter filter) {
        if (TextUtils.isEmpty(capability)) {
            throw new IllegalArgumentException(
                    "getNodesForCapability(): Capability cannot be null or empty");
        }
        if (filter == null) {
            throw new IllegalArgumentException("getNodesForCapability(): filter cannot be null");
        }
        Set<Node> nodes = mCapabilityToNodesMapping.get(capability);
        if (nodes == null) {
            return Collections.emptySet();
        }
        return filter.filterNodes(nodes);
    }

    /**
     * Returns the connected node with the given {@code nodeId}, if there is one, or {@code null}
     * otherwise.
     */
    @Nullable
    public final Node getNodeById(String nodeId) {
        nodeId = Utils.assertNotNull(nodeId, "nodeId");
        for (Node node : mConnectedNodes) {
            if (nodeId.equals(node.getId())) {
                return node;
            }
        }
        return null;
    }

    /**
     * Launches an activity on the nodes identified by the given capability and filter.
     *
     * @param activityName The name of activity to be launched. If this parameter is {@code null},
     * then {@link WearConsumer#onWearableApplicationLaunchRequestReceived(Bundle, boolean)} will be
     * called and client applications need to register this callback to be notified and will be
     * responsible for launching the app.
     * @param bundle A {@link Bundle} that contains additional information to be used in the launch
     * process. If {@code activityName} is not {@code null}, then this will be passed to the intent
     * that launches the app (set by {@code Intent.setExtras(bundle)}). If, on the other hand,
     * {@code activityName} is {@code null}, then this bundle will be passed to the callback
     * {@link WearConsumer#onWearableApplicationLaunchRequestReceived(Bundle, boolean)}
     * @param relaunchIfRunning If {@code true} and {@code activityName != null}, then the activity
     * will be relaunched even if the application is currently running. If {@code false} and if
     * {@code activityName != null}, then the activity will be launched only if the client
     * application is not in the foreground. If {@code activityName == null}, this will be passed to
     * {@link WearConsumer#onWearableApplicationLaunchRequestReceived(Bundle, boolean)}
     * @param capability The capability that is used to find the target nodes
     * @param filter If not {@code null}, it is will be applied to the list of nodes that provide
     * the given capability. If {@code null}, the default {@link NearbyFilter} will be used.
     * @return {@code true} if and only of the combination of {@code capability} and {@code filter}
     * results in a non-empty set of qualified node(s).
     *
     * @see #launchAppOnNode(String, Bundle, boolean, Node)
     */
    public boolean launchAppOnNodes(@Nullable final String activityName, @Nullable Bundle bundle,
            boolean relaunchIfRunning, final String capability,
            @Nullable NodeSelectionFilter filter) {
        Set<Node> nodes = getNodesForCapability(capability);
        if (nodes != null && !nodes.isEmpty()) {
            if (filter == null) {
                filter = new NearbyFilter();
            }
            Set<Node> filteredNodes = filter.filterNodes(nodes);
            if (filteredNodes == null) {
                Log.w(TAG, "No node was found to match the filter " + filter.describe());
                return false;
            }
            for (Node targetNode : filteredNodes) {
                launchAppOnNode(activityName, bundle, relaunchIfRunning, targetNode);
            }
            return true;
        }

        return false;
    }

    /**
     * Launches an activity on the given {@code node}.
     *
     * @param activityName The name of activity to be launched. If this parameter is {@code null},
     * then {@link WearConsumer#onWearableApplicationLaunchRequestReceived(Bundle, boolean)} will be
     * called and client applications need to register this callback to be notified and will be
     * responsible for launching the app.
     * @param bundle A {@link Bundle} that contains additional information to be used in the launch
     * process. If {@code activityName} is not {@code null}, then this will be passed to the intent
     * that launches the app (set by {@code Intent.setExtras(bundle)}). If, on the other hand,
     * {@code activityName} is {@code null}, then this bundle will be passed to the callback
     * {@link WearConsumer#onWearableApplicationLaunchRequestReceived(Bundle, boolean)}
     * @param relaunchIfRunning If {@code true} and {@code activityName != null}, then the activity
     * will be relaunched even if the application is currently running. If {@code false} and if
     * {@code activityName != null}, then the activity will be launched only if the client
     * application is not in the foreground. If {@code activityName == null}, this will be passed to
     * {@link WearConsumer#onWearableApplicationLaunchRequestReceived(Bundle, boolean)}
     *
     * @see #launchAppOnNodes(String, Bundle, boolean, String, NodeSelectionFilter)
     */
    public void launchAppOnNode(@Nullable final String activityName, @Nullable Bundle bundle,
            boolean relaunchIfRunning, Node node) {
        DataMap dataMap = new DataMap();
        dataMap.putBoolean(KEY_START_ACTIVITY_RELAUNCH, relaunchIfRunning);
        if (bundle != null) {
            dataMap.putDataMap(KEY_START_ACTIVITY_BUNDLE, DataMap.fromBundle(bundle));
        }
        if (!TextUtils.isEmpty(activityName)) {
            dataMap.putString(KEY_START_ACTIVITY_NAME, activityName);
        }
        sendMessage(node.getId(), Constants.PATH_LAUNCH_APP, dataMap, null);
    }

    public void sendFile(final String requestId, Channel channel, Uri file, long startOffset,
            long length, ResultCallback<Status> callback) {

        channel.addListener(mGoogleApiClient, new MyChannelListener());
        PendingResult<Status> result
                = channel.sendFile(mGoogleApiClient, file, startOffset, length);
        if (callback == null) {
            callback = new ResultCallback<Status>() {
                @Override
                public void onResult(Status status) {
                    for (WearConsumer consumer : mWearConsumers) {
                        consumer.onWearableSendFileResult(status.getStatusCode(), requestId);
                    }
                }
            };
        }

        result.setResultCallback(callback);
    }

    /**
     * Initiates opening a channel to a nearby node. When done, it will call the {@code listener}
     * and passes the status code of the request and the channel that was opened. Note that if the
     * request was not successful, the channel passed to the listener will be {@code null}. <br/>
     * <strong>Note:</strong> It is the responsibility of the caller to close the channel and the
     * stream when it is done.
     *
     * @param node The node to which a channel should be opened. Note that {@code node.isNearby()}
     * should return {@code true} otherwise this method throws an {@link IllegalArgumentException}
     * exception.
     * @param path The path used for opening a channel
     * @param listener The listener that is called when this request is completed.
     */
    public void openChannel(Node node, String path,
            final WearFileTransfer.OnChannelReadyListener listener) {
        if (node.isNearby()) {
            Wearable.ChannelApi.openChannel(
                    mGoogleApiClient, node.getId(), path).setResultCallback(
                    new ResultCallback<ChannelApi.OpenChannelResult>() {
                        @Override
                        public void onResult(ChannelApi.OpenChannelResult openChannelResult) {
                            int statusCode = openChannelResult.getStatus().getStatusCode();
                            Channel channel = null;
                            if (openChannelResult.getStatus().isSuccess()) {
                                channel = openChannelResult.getChannel();
                            } else {
                                Log.e(TAG, "openChannel(): Failed to get channel, status code: "
                                        + statusCode);
                            }
                            listener.onChannelReady(statusCode, channel);
                        }
                    });
        } else {
            throw new IllegalArgumentException(
                    "openChannel(): Node should be nearby, you have: " + node);
        }
    }

    /**
     * Opens an {@link OutputStream} to a nearby node. To do this, this method first makes an
     * attempt to open a channel to the target node using the {@code path} that is provided. If
     * successful, then it opens an {@link OutputStream} using that channel. Finally, it calls the
     * {@code listener} when the {@link OutputStream} is available. On the target node, clients
     * should register a
     * {@link WearConsumer#onWearableInputStreamForChannelOpened(int, String, Channel, InputStream)}
     * to be notified of the availability of an {@link InputStream} to handle the incoming bytes.
     *
     * <p>Caller should register a
     * {@link WearFileTransfer.OnWearableChannelOutputStreamListener}
     * listener to be notified of the status of the request and to obtain a reference to the
     * {@link OutputStream} that is opened upon successful execution.
     *
     * @param node The node to open a channel for data transfer. Note that this node should be
     * nearby otherwise this method will return immediately without performing any additional tasks.
     * @param path The path that will be used to open a channel for transfer.
     * @param listener The listener that will be notified of the status of this request. Upon a
     * successful execution, this listener will receive a pointer to the {@link OutputStream} that
     * was opened.
     */
    public void getOutputStreamViaChannel(Node node, String path,
            final WearFileTransfer.OnWearableChannelOutputStreamListener listener) {
        if (!node.isNearby()) {
            throw new IllegalArgumentException(
                    "getOutputStreamViaChannel(): Node should be nearby, you have: " + node);
        }
        Wearable.ChannelApi.openChannel(
                mGoogleApiClient, node.getId(), path).setResultCallback(
                new ResultCallback<ChannelApi.OpenChannelResult>() {
                    @Override
                    public void onResult(ChannelApi.OpenChannelResult openChannelResult) {
                        if (openChannelResult.getStatus().isSuccess()) {
                            final Channel channel = openChannelResult.getChannel();
                            channel.addListener(mGoogleApiClient, new MyChannelListener());
                            channel.getOutputStream(mGoogleApiClient).setResultCallback(

                                    new ResultCallback<Channel.GetOutputStreamResult>() {
                                        @Override
                                        public void onResult(
                                                Channel.GetOutputStreamResult
                                                        getOutputStreamResult) {
                                            if (getOutputStreamResult.getStatus().isSuccess()) {
                                                OutputStream outputStream
                                                        = getOutputStreamResult.getOutputStream();
                                                listener.onOutputStreamForChannelReady(
                                                        getOutputStreamResult.getStatus()
                                                                .getStatusCode(), channel,
                                                        outputStream);
                                            } else {
                                                closeChannel(channel);
                                                listener.onOutputStreamForChannelReady(
                                                        getOutputStreamResult.getStatus()
                                                                .getStatusCode(), null, null);
                                            }
                                        }
                                    });
                        } else {
                            listener.onOutputStreamForChannelReady(
                                    openChannelResult.getStatus().getStatusCode(), null, null);
                        }
                    }
                });
    }

    /**
     * Closes the {@code channel} if it is not {@code null}.
     */
    public void closeChannel(Channel channel) {
        if (channel != null) {
            channel.close(mGoogleApiClient);
        }
    }

    /**
     * Extracts {@link android.graphics.Bitmap} data from an
     * {@link com.google.android.gms.wearable.Asset}, in a blocking way, hence should not be called
     * on the UI thread. This may return {@code null}.
     */
    public Bitmap loadBitmapFromAssetSynchronous(Asset asset) {
        assertApiConnectivity();
        Utils.assertNonUiThread();
        if (asset == null) {
            throw new IllegalArgumentException("Asset must be non-null");
        }

        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                mGoogleApiClient, asset).await().getInputStream();

        if (assetInputStream == null) {
            Log.w(TAG, "Requested an unknown Asset.");
            return null;
        }
        return BitmapFactory.decodeStream(assetInputStream);
    }

    /**
     * Handles messages with the following paths and routes them to special callbacks:
     * <ul>
     *     <li>{@link Constants#PATH_HTTP_REQUEST}</li>
     *     <li>{@link Constants#PATH_LAUNCH_APP}</li>
     * </ul>
     *
     * @return Returns {@code true} if and only if this method handles the message completely and
     * should not be bubbled up.
     */
    private boolean handleSpecialMessages(MessageEvent messageEvent) {
        boolean handled = false;
        String path = messageEvent.getPath();
        if (path.equals(Constants.PATH_HTTP_REQUEST)) {
            // Wear app has sent an http request, so let's handle that
            handleHttpMessageEvent(messageEvent);
            handled = true;
        } else if (path.equals(Constants.PATH_LAUNCH_APP)) {
            // Wear app has sent a request to launch an app, so let's handle this too
            handleLaunchMessageEvent(messageEvent);
            handled = true;
        }
        return handled;
    }

    /**
     * Handles the special message to launch an activity.
     */
    private void handleLaunchMessageEvent(MessageEvent messageEvent) {
        DataMap dataMap = DataMap.fromByteArray(messageEvent.getData());
        boolean relaunchIfRunning = dataMap.getBoolean(KEY_START_ACTIVITY_RELAUNCH, false);
        DataMap bundleData = dataMap.getDataMap(KEY_START_ACTIVITY_BUNDLE);
        String activityName = dataMap.getString(KEY_START_ACTIVITY_NAME);
        Bundle bundle = null;
        if (bundleData != null) {
            bundle = bundleData.toBundle();
        }
        if (activityName == null) {
            for (WearConsumer consumer : mWearConsumers) {
                consumer.onWearableApplicationLaunchRequestReceived(bundle, relaunchIfRunning);
            }
        } else {
            try {
                if (!TextUtils.isEmpty(activityName)) {
                    Class<?> targetActivity = Class.forName(activityName);
                    Intent intent = new Intent(mContext, targetActivity);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (bundle != null) {
                        intent.putExtras(bundle);
                    }
                    if (!mAppForeground || relaunchIfRunning) {
                        mContext.startActivity(intent);
                    }
                } else {
                    Log.e(TAG, "Activity Name cannot be empty");
                }

            } catch (ClassNotFoundException e) {
                Log.e(TAG, "Failed to find the activity class to launch", e);
            }
        }
    }

    /**
     * Handles the special message when the response to an http request is received.
     */
    private void handleHttpMessageEvent(MessageEvent messageEvent) {
        String nodeId = messageEvent.getSourceNodeId();
        DataMap dataMap = DataMap.fromByteArray(messageEvent.getData());
        String requestId = dataMap.get(WearHttpHelper.KEY_REQUEST_ID);
        String url = dataMap.get(WearHttpHelper.KEY_URL);
        String method = dataMap.get(WearHttpHelper.KEY_METHOD_TYPE);
        String charset = dataMap.get(WearHttpHelper.KEY_CHARSET);
        if (TextUtils.isEmpty(method)) {
            method = WearHttpHelper.METHOD_GET;
        }
        String query = dataMap.get(WearHttpHelper.KEY_QUERY_PARAMS);
        for (WearConsumer consumer : mWearConsumers) {
            consumer.onWearableHttpRequestReceived(url, method, query, charset, nodeId,
                    requestId);
        }
    }

    /**
     * Clients can register to {@link WearConsumer#onWearableApiConnectionSuspended()}.
     */
    private void onConnectionSuspended(int i) {
        for(WearConsumer consumer : mWearConsumers) {
            consumer.onWearableApiConnectionSuspended();
        }
    }

    /**
     * Clients can register to {@link WearConsumer#onWearableApiConnectionFailed()}.
     */
    private void onConnectionFailed(ConnectionResult connectionResult) {
        for(WearConsumer consumer : mWearConsumers) {
            consumer.onWearableApiConnectionFailed();
        }
    }

    /**
     * Clients can register to {@link WearConsumer#onWearableMessageReceived(MessageEvent)}.
     */
    void onMessageReceived(MessageEvent messageEvent) {
        Utils.LOGD(TAG, "Received a message with path: " + messageEvent.getPath());
        if (!handleSpecialMessages(messageEvent)) {
            for (WearConsumer consumer : mWearConsumers) {
                consumer.onWearableMessageReceived(messageEvent);
            }
        }
    }

    /**
     * Clients can register to {@link WearConsumer#onWearablePeerConnected(Node)}.
     */
    void onPeerConnected(Node peer) {
        Utils.LOGD(TAG, "onPeerConnected: " + peer);
        for (WearConsumer consumer : mWearConsumers) {
            consumer.onWearablePeerConnected(peer);
        }
    }

    /**
     * Clients can register to {@link WearConsumer#onWearablePeerDisconnected(Node)}.
     */
    void onPeerDisconnected(Node peer) {
        Utils.LOGD(TAG, "onPeerDisconnected: " + peer);
        for (WearConsumer consumer : mWearConsumers) {
            consumer.onWearablePeerDisconnected(peer);
        }
    }

    /**
     * Clients can register to {@link WearConsumer#onWearableConnectedNodes(List)}.
     */
    void onConnectedNodes(List<Node> connectedNodes) {
        Utils.LOGD(TAG, "onConnectedNodes: " + connectedNodes);
        mConnectedNodes.clear();
        mConnectedNodes.addAll(connectedNodes);
        for (WearConsumer consumer : mWearConsumers) {
            consumer.onWearableConnectedNodes(connectedNodes);
        }
    }

    /**
     * Clients can register to {@link WearConsumer#onWearableInitialConnectedNodesReceived()}.
     */
    void onConnectedInitialNodesReceived() {
        Utils.LOGD(TAG, "onConnectedInitialNodesReceived");
        for (WearConsumer consumer : mWearConsumers) {
            consumer.onWearableInitialConnectedNodesReceived();
        }
    }

    /**
     * Clients can register to {@link WearConsumer#onWearableCapabilityChanged(String, Set)}.
     */
    void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        String capability = capabilityInfo.getName();
        Set<Node> nodes = capabilityInfo.getNodes();
        mCapabilityToNodesMapping.put(capability, nodes);
        for (WearConsumer consumer : mWearConsumers) {
            consumer.onWearableCapabilityChanged(capability, nodes);
        }
    }

    /**
     * Clients can register to {@link WearConsumer#onWearableInitialConnectedCapabilitiesReceived().
     */
    void onConnectedInitialCapabilitiesReceived() {
        for (WearConsumer consumer : mWearConsumers) {
            consumer.onWearableInitialConnectedCapabilitiesReceived();
        }
    }

    /**
     * Clients can register to
     * {@link WearConsumer#onWearableInputStreamForChannelOpened(int, String, Channel, InputStream)}.
     */
    void onChannelOpened(final Channel channel) {
        String path = channel.getPath();
        Utils.LOGD(TAG, "onChannelOpened(): Path =" + path);
        if (path.startsWith(Constants.PATH_FILE_TRANSFER_TYPE_FILE)) {
            // we are receiving a file sent by WearFileTransfer
            final Map<String, String> paramsMap = getFileTransferParams(path);
            final String name = paramsMap.get(WearFileTransfer.PARAM_NAME);
            final String requestId = paramsMap.get(WearFileTransfer.PARAM_REQUEST_ID);
            final long size = Long.valueOf(paramsMap.get(WearFileTransfer.PARAM_SIZE));
            try {
                final File outFile = prepareFile(name);
                if (outFile == null || !outFile.exists()) {
                    Log.e(TAG, "Failed to create the file: " + name);
                    return;
                }
                channel.receiveFile(mGoogleApiClient, Uri.fromFile(outFile), false)
                        .setResultCallback(
                                new ResultCallback<Status>() {
                                    @Override
                                    public void onResult(Status status) {
                                        int statusCode = status.getStatusCode();
                                        if (!status.isSuccess()) {
                                            Log.e(TAG, "receiveFile(): Failed to receive file with "
                                                    + "status code = " + statusCode
                                                    + ", and status: " + status.getStatus());
                                        } else if (size != outFile.length()) {
                                            Log.e(TAG, "receiveFile(): Size of the transferred "
                                                    + "file doesn't match the original size");
                                        }
                                        for (WearConsumer consumer : mWearConsumers) {
                                            consumer.onWearableFileReceivedResult(statusCode,
                                                    requestId, outFile, name);
                                        }
                                    }
                                });
            } catch (IOException e) {
                Log.e(TAG, "Failed to create the file: " + name, e);
            }

        } else if (path.startsWith(Constants.PATH_FILE_TRANSFER_TYPE_STREAM)) {
            // we are receiving data by low level InputStream, sent by WearFileTransfer
            final Map<String, String> paramsMap = getStreamTransferParams(path);
            final String requestId = paramsMap.get(WearFileTransfer.PARAM_REQUEST_ID);
            channel.getInputStream(mGoogleApiClient).setResultCallback(
                    new ResultCallback<Channel.GetInputStreamResult>() {
                        @Override
                        public void onResult(Channel.GetInputStreamResult getInputStreamResult) {
                            int statusCode = getInputStreamResult.getStatus().getStatusCode();
                            if (!getInputStreamResult.getStatus().isSuccess()) {
                                Log.e(TAG, "Failed to open InputStream from channel, status code: "
                                        + statusCode);
                            }
                            for (WearConsumer consumer : mWearConsumers) {
                                consumer.onWearableInputStreamForChannelOpened(statusCode,
                                        requestId, channel, getInputStreamResult.getInputStream());
                            }
                        }
                    });
        } else {
            for (WearConsumer consumer : mWearConsumers) {
                consumer.onWearableChannelOpened(channel);
            }
        }
    }

    private Map<String, String> getFileTransferParams(String path) {
        Map<String, String> result = new HashMap<>();
        if (path.startsWith(Constants.PATH_FILE_TRANSFER_TYPE_FILE)) {
            String[] pieces = path.replace(Constants.PATH_FILE_TRANSFER_TYPE_FILE, "").split("\\/");
            try {
                result.put(WearFileTransfer.PARAM_NAME, URLDecoder.decode(pieces[0], "utf-8"));
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "Failed to decode name", e);
            }
            result.put(WearFileTransfer.PARAM_SIZE, pieces[1]);
            result.put(WearFileTransfer.PARAM_REQUEST_ID, pieces[2]);
        } else {
            throw new IllegalArgumentException(
                    "Path doesn't start with " + Constants.PATH_FILE_TRANSFER_TYPE_FILE);
        }

        return result;
    }

    private Map<String, String> getStreamTransferParams(String path) {
        Map<String, String> result = new HashMap<>();
        if (path.startsWith(Constants.PATH_FILE_TRANSFER_TYPE_STREAM)) {
            String[] pieces = path.replace(Constants.PATH_FILE_TRANSFER_TYPE_FILE, "").split("\\/");
            result.put(WearFileTransfer.PARAM_REQUEST_ID, pieces[0]);
        } else {
            throw new IllegalArgumentException(
                    "Path doesn't start with " + Constants.PATH_FILE_TRANSFER_TYPE_STREAM);
        }

        return result;
    }

    private File prepareFile(String name) throws IOException {
        File file = new File(mContext.getFilesDir(), name);
        if (!file.exists()) {
            if (!file.createNewFile()) {
                return null;
            }
        }

        return file;
    }

    /**
     * Clients can register to {@link WearConsumer#onWearableChannelClosed(Channel, int, int)}.
     */
    void onChannelClosed(Channel channel, int closeReason,
            int appSpecificErrorCode) {
        for (WearConsumer consumer : mWearConsumers) {
            consumer.onWearableChannelClosed(channel, closeReason, appSpecificErrorCode);
        }
    }

    /**
     * Clients can register to {@link WearConsumer#onWearableInputClosed(Channel, int, int)}.
     */
    void onInputClosed(Channel channel, int closeReason, int appSpecificErrorCode) {
        for (WearConsumer consumer : mWearConsumers) {
            consumer.onWearableInputClosed(channel, closeReason, appSpecificErrorCode);
        }
    }

    /**
     * Clients can register to {@link WearConsumer#onWearableOutputClosed(Channel, int, int)}.
     */
    void onOutputClosed(Channel channel, int closeReason, int appSpecificErrorCode) {
        for (WearConsumer consumer : mWearConsumers) {
            consumer.onWearableOutputClosed(channel, closeReason, appSpecificErrorCode);
        }
    }

    /**
     * Clients can register to {@link WearConsumer#onWearableDataChanged(DataEventBuffer)}.
     */
    void onDataChanged(DataEventBuffer dataEvents) {
        for (WearConsumer consumer : mWearConsumers) {
            consumer.onWearableDataChanged(dataEvents);
        }
    }

    /**
     * A method to clean up the artifacts of the WearManager. This should be called when we are
     * certain we would not need the WearManager any more.
     */
    public void cleanUp() {
        Utils.LOGD(TAG, "cleanUp() ...");
        mContext.stopService(new Intent(mContext, WclWearableListenerService.class));
        if (!mWatchedCapabilities.isEmpty()) {
            String[] capabilities = mWatchedCapabilities
                    .toArray(new String[mWatchedCapabilities.size()]);
            removeCapabilities(capabilities);
        }
        mWearConsumers.clear();
    }

    /**
     * Stops the {@link WclWearableListenerService}.
     */
    public void stopWearableService() {
        Utils.LOGD(TAG, "stopWearableService()");
        mContext.stopService(new Intent(mContext, WclWearableListenerService.class));
    }

    /**
     * Start an internal activity that presents a
     * {@link android.support.wearable.view.WearableListView}. A {@link WearableListConfig} is
     * used to pass the configuration parameters for the list. When a selection is made, the
     * activity will be closed and users can capture the result by implementing
     * {@link Activity#onActivityResult(int, int, Intent)}.
     *
     * @see WearableListConfig
     */
    public void showWearableList(Activity activity, WearableListConfig config) {
        Intent intent = new Intent(activity, WclWearableListViewActivity.class);
        intent.putExtra(Constants.KEY_LIST_CONFIG, config.toBundle());
        activity.startActivityForResult(intent, config.getRequestCode());
    }

    /**
     * Returns the version of this library
     */
    public String getVersion() {
        return mWclVersion;
    }
    /**
     * When the client application becomes visible, we start an instance of
     * {@link com.google.android.gms.wearable.WearableListenerService} that is provided by this
     * library. Even if the service has already started, here we call this service in a way that
     * it would stay active until it is explicitly stopped. In order to avoid having a long-running
     * background service, we kill that service when the client application is no longer visible.
     */
    private void onAppEnterForeground() {
        mAppForeground = true;
        mContext.startService(new Intent(mContext, WclWearableListenerService.class));
    }

    /**
     * Called when application goes to background. We do the needed clean up here.
     */
    private void onAppEnterBackground() {
        mAppForeground = false;
        stopWearableService();
    }

    private final class MyConnectionCallbacksListener
            implements GoogleApiClient.ConnectionCallbacks {

        @Override
        public void onConnected(Bundle bundle) {
            WearManager.this.onConnected(bundle);
        }

        @Override
        public void onConnectionSuspended(int i) {
            WearManager.this.onConnectionSuspended(i);
        }
    }

    private final class MyConnectionFailedListener
            implements GoogleApiClient.OnConnectionFailedListener {

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            WearManager.this.onConnectionFailed(connectionResult);
        }
    }

    private final class MyApplicationVisibilityDetectorListener
            implements ApplicationVisibilityDetector.Listener {

        @Override
        public void onAppEnterForeground() {
            WearManager.this.onAppEnterForeground();
        }

        @Override
        public void onAppEnterBackground() {
            WearManager.this.onAppEnterBackground();
        }
    }

    private  class MyChannelListener implements ChannelApi.ChannelListener {
        @Override
        public void onChannelOpened(Channel channel) {
        }

        @Override
        public void onChannelClosed(Channel channel,
                int closedReason, int appSpecificErrorCode) {
            Utils.LOGD(TAG, "Channel Closed");
        }

        @Override
        public void onInputClosed(Channel channel,
                int closedReason, int appSpecificErrorCode) {
        }

        @Override
        public void onOutputClosed(Channel channel,
                int closedReason, int appSpecificErrorCode) {
            Utils.LOGD(TAG, "onOutputClosed(): Output closed so closing channel...");
            closeChannel(channel);
        }
    }
}
