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

import android.os.Bundle;

import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.devrel.wcl.WearManager;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;

/**
 * A no-op implementation of {@link WearConsumer}. Clients can extend this class and override only
 * the subset of callbacks that they are interested in. Note that after extending this class, you
 * need to register your extension by calling
 * {@link WearManager#addWearConsumer(WearConsumer)} and unregister it, when done, by calling
 * {@link WearManager#removeWearConsumer(WearConsumer)}
 */
public abstract class AbstractWearConsumer implements WearConsumer {

    @Override
    public void onWearableInitialConnectedCapabilitiesReceived() {
        //no-op
    }

    @Override
    public void onWearableInitialConnectedNodesReceived() {
        //no-op
    }

    @Override
    public void onWearableAddCapabilityResult(int statusCode) {
        //no-op
    }

    @Override
    public void onWearableRemoveCapabilityResult(int statusCode) {
        //no-op
    }

    @Override
    public void onWearableCapabilityChanged(String capability, Set<Node> nodes) {
        //no-op
    }

    @Override
    public void onWearableSendMessageResult(int statusCode) {
        //no-op
    }

    @Override
    public void onWearableMessageReceived(MessageEvent messageEvent) {
        //no-op
    }

    @Override
    public void onWearableApiConnected() {
        //no-op
    }

    @Override
    public void onWearableApiConnectionSuspended() {
        //no-op
    }

    @Override
    public void onWearableApiConnectionFailed() {
        //no-op
    }

    @Override
    public void onWearableApplicationLaunchRequestReceived(Bundle bundle,
            boolean relaunchIfRunning) {
        //no-op
    }

    @Override
    public void onWearableHttpRequestReceived(String url, String method, String query,
            String charset, String nodeId, String requestId) {
        //no-op
    }

    @Override
    public void onWearableChannelOpened(Channel channel) {
        //no-op
    }

    @Override
    public void onWearableFileReceivedResult(int statusCode, String requestId, File savedFile,
            String originalName) {
        //no-op
    }

    @Override
    public void onWearableChannelClosed(Channel channel, int closeReason,
            int appSpecificErrorCode) {
        //no-op
    }

    @Override
    public void onWearableInputClosed(Channel channel, int closeReason, int appSpecificErrorCode) {
        //no-op
    }

    @Override
    public void onWearableOutputClosed(Channel channel, int closeReason, int appSpecificErrorCode) {
        //no-op
    }

    @Override
    public void onWearableDataChanged(DataEventBuffer dataEvents) {
        //no-op
    }

    @Override
    public void onWearableConnectedNodes(List<Node> connectedNodes) {
        //no-op
    }

    @Override
    public void onWearablePeerDisconnected(Node peer) {
        //no-op
    }

    @Override
    public void onWearablePeerConnected(Node peer) {
        //no-op
    }

    @Override
    public void onWearableSendDataResult(int statusCode) {
        //no-op
    }

    @Override
    public void onWearableGetDataItems(int status, DataItemBuffer dataItemBuffer) {
        //no-op
    }

    @Override
    public void onWearableGetDataItem(int statusCode, DataApi.DataItemResult dataItemResult) {
        //no-op
    }

    @Override
    public void onWearableDeleteDataItemsResult(int statusCode) {
        //no-op
    }

    @Override
    public void onWearableInputStreamForChannelOpened(int statusCode, String requestId,
            Channel channel, InputStream inputStream) {
        //no-op
    }

    @Override
    public void onWearableSendFileResult(int statusCode, String requestId) {
        //no-op
    }

    @Override
    public void onOutputStreamForChannelReady(int statusCode, Channel channel,
            OutputStream outputStream) {
        //no-op
    }
}
