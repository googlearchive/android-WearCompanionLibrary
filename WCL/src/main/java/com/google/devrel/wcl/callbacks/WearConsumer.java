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

package com.google.devrel.wcl.callbacks;

import android.net.Uri;
import android.os.Bundle;

import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.devrel.wcl.WearManager;
import com.google.devrel.wcl.connectivity.WearFileTransfer;
import com.google.devrel.wcl.connectivity.WearHttpHelper;
import com.google.devrel.wcl.filters.NodeSelectionFilter;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * An interface that is used by the library to inform application clients of changes in the state
 * or data. It is recommended that the clients extend the no-op implementation
 * {@link AbstractWearConsumer} of this interface and override only the desired subset of callbacks.
 * Clients register their implementation of this interface by calling
 * {@link WearManager#addWearConsumer(WearConsumer)} and should remember to unregister by
 * calling {@link WearManager#removeWearConsumer(WearConsumer)} to avoid
 * any leaks.
 */
public interface WearConsumer extends WearFileTransfer.OnWearableChannelOutputStreamListener {

    /**
     * Called when the result of
     * {@link WearManager#addCapabilities(String...)} is available.
     *
     * @param statusCode The status code of the result
     */
    void onWearableAddCapabilityResult(int statusCode);

    /**
     * Called when the result of
     * {@link WearManager#removeCapabilities(String...)} is available.
     *
     * @param statusCode The status code of the result
     */
    void onWearableRemoveCapabilityResult(int statusCode);

    /**
     * Called when framework reports a change in the capabilities.
     *
     * @param capability The capability that has changed.
     * @param nodes The new set of nodes for the given capability.
     */
    void onWearableCapabilityChanged(String capability, Set<Node> nodes);

    /**
     * Called when initial capabilities are received after google api connection.
     */
    void onWearableInitialConnectedCapabilitiesReceived();

    /**
     * Called when the result of {@link WearManager#sendMessage(String, String, byte[])}
     * (or other variants of that call) is available.
     *
     * @param statusCode The status code of the result
     */
    void onWearableSendMessageResult(int statusCode);

    /**
     * Called when a message is received.
     */
    void onWearableMessageReceived(MessageEvent messageEvent);

    /**
     * Called when the Google Api Client for Wearable APIs is connected. It is guaranteed that
     * this method is called even if the connectivity is established prior to registration of the
     * listener.
     */
    void onWearableApiConnected();

    /**
     * Called when the connection to the Google Api Client for Wearable APIs is suspended.
     */
    void onWearableApiConnectionSuspended();

    /**
     * Called when the connection to the Google Api Client for Wearable APIs fails.
     */
    void onWearableApiConnectionFailed();

    /**
     * Called when a request for launching the app is received by a node but no activity name is
     * provided. The request is made from another node by calling
     * {@link WearManager#launchAppOnNodes(String, Bundle, boolean, String, NodeSelectionFilter)}
     * and passing {@code null} for the {@code activityName} in this call. Then the node that
     * receives this callback needs to launch the app.
     */
    void onWearableApplicationLaunchRequestReceived(Bundle bundle, boolean relaunchIfRunning);

    /**
     * Called when a request to perform an HTTP call is received by this node. When the request
     * is made and the response is ready, this node should send back the result by calling
     * {@link WearManager#sendHttpResponse(String, int, String, String, ResultCallback)}
     * so that the library can route the response to the correct caller.
     *
     * @param url The url for the request. If the request is of type GET, then url may include query
     * parameters.
     * @param method The HTTP method; only
     * {@link WearHttpHelper#METHOD_GET} and
     * {@link WearHttpHelper#METHOD_POST} are supported.
     * @param query For POST request, the query parameters are included here.
     * @param charset The Charset of the request
     * @param nodeId The nodeId of the node that has sent the request. This is needed when the
     * response is sent back for correct routing.
     * @param requestId A unique id associated with this request; it is required when sending back
     * the response.
     */
    void onWearableHttpRequestReceived(String url, String method, String query, String charset,
            String nodeId, String requestId);

    /**
     * Called when a channel is closed.
     */
    void onWearableChannelClosed(Channel channel, int closeReason, int appSpecificErrorCode);

    /**
     * Called when InputStream associated with the channel is close.
     */
    void onWearableInputClosed(Channel channel, int closeReason, int appSpecificErrorCode);

    /**
     * Called when the output stream associated with the channel is closed.
     */
    void onWearableOutputClosed(Channel channel, int closeReason, int appSpecificErrorCode);

    /**
     * Called when Data layer reports a change in the stored data.
     */
    void onWearableDataChanged(DataEventBuffer dataEvents);

    /**
     * Called when the list of connected nodes changes.
     */
    void onWearableConnectedNodes(List<Node> connectedNodes);

    /**
     * Called when initial list of connected nodes is received - so that you can send messages to it.
     */
    void onWearableInitialConnectedNodesReceived();

    /**
     * Called when a connected peer disconnects.
     */
    void onWearablePeerDisconnected(Node peer);

    /**
     * Called when a node changes its connectivity status to connected.
     */
    void onWearablePeerConnected(Node peer);

    /**
     * Called when the result of
     * {@link WearManager#putDataItem(PutDataRequest, ResultCallback)}
     * is available.
     *
     * @param statusCode The status code of the result
     */
    void onWearableSendDataResult(int statusCode);

    /**
     * Called when the result of
     * {@link WearManager#getDataItems(ResultCallback)}
     * is available.
     *
     * @param statusCode The status code of the result
     * @param dataItemBuffer The data items received
     */
    void onWearableGetDataItems(int statusCode, DataItemBuffer dataItemBuffer);

    /**
     * Called when the result of
     * {@link WearManager#getDataItem(Uri, ResultCallback)}
     * is available.
     *
     * @param statusCode The status code of the result
     * @param dataItemResult The data items received
     */
    void onWearableGetDataItem(int statusCode, DataApi.DataItemResult dataItemResult);

    /**
     * Called when the result of
     * {@link WearManager#deleteDataItems(Uri, ResultCallback)}
     * is available.
     *
     * @param statusCode The status code of the result
     */
    void onWearableDeleteDataItemsResult(int statusCode);

    /**
     * Called when a request for opening an {@link InputStream} is available.
     * When a clients request to open an {@link java.io.OutputStream} by calling
     * {@link WearFileTransfer#requestOutputStream()}, this library will handle opening a channel
     * from the client node to the target node. On the target node, client should register to this
     * callback to be notified when an {@link InputStream} is available to receive the data sent
     * through the channel.
     *
     * @param statusCode The status code corresponding to the attempt to open an
     * {@link InputStream}. Successful operation will be identified by
     * {@link com.google.android.gms.wearable.WearableStatusCodes#SUCCESS}
     * @param requestId The unique id for the request that was made by the sender client. This
     * id is provided here for bookkeeping purposes and being able to correlate different requests
     * from the client to the streams that open on the receiver ends.
     * @param channel The instance of {@link Channel}
     * @param inputStream The {@link InputStream} that is opened if successful
     */
    void onWearableInputStreamForChannelOpened(int statusCode, String requestId, Channel channel,
            InputStream inputStream);

    /**
     * Called with the result corresponding to a request to send a file using
     * {@link WearFileTransfer#startTransfer()}. This is called on the sender node to inform the
     * sender of the success ot failure of the operation.
     *
     * @param statusCode The status code corresponding to the attempt to transfer a file. Successful
     * operation will be identified by
     * {@link com.google.android.gms.wearable.WearableStatusCodes#SUCCESS}
     * @param requestId The unique id for this operation.
     */
    void onWearableSendFileResult(int statusCode, String requestId);

    /**
     * Called when a {@link Channel} is opened.
     */
    void onWearableChannelOpened(Channel channel);

    /**
     * Called when a node has the result of receiving a file transfer. The sender client has
     * used {@link WearFileTransfer#startTransfer()} to send a file and the library handles the
     * logistics of this transfer but on the receiving end, the client can register for this
     * callback to be notified when the transfer is completed, even if unsuccessful.
     *
     * @param statusCode The status code corresponding to the attempt to transfer a file. Successful
     * operation will be identified by
     * {@link com.google.android.gms.wearable.WearableStatusCodes#SUCCESS}
     * @param requestId The unique id for this operation.
     * @param savedFile The {@link File} object pointing to the file that has been transferred.
     * @param originalName The original name of the ile that was sent from the sender node.
     */
    void onWearableFileReceivedResult(int statusCode, String requestId, File savedFile,
            String originalName);

}
