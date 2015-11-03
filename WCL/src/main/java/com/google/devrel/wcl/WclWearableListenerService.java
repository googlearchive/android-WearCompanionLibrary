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

import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;

/**
 * A {@link WearableListenerService} that is started when the client application is in front.
 * WearManager uses this as a means to get a number of wear related callbacks and as
 * such, {@link WearManager} starts this service by calling {@code Context.startService()} which
 * then makes this service a long-lived service. {@link WearManager} is also responsible for
 * stopping this service; clients could call {@link WearManager#stopWearableService()} to do that or
 * the {@link WearManager} will do so when the client application is no longer visible.
 */
public class WclWearableListenerService extends WearableListenerService {

    private static final String TAG = "WearUtilListenerService";
    private WearManager mWearManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Utils.LOGD(TAG, "WearUtilListenerService is being created");
        mWearManager = WearManager.getInstance();
    }

    @Override
    public void onDestroy() {
        Utils.LOGD(TAG, "WearUtilListenerService is being destroyed");
        super.onDestroy();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);
        mWearManager.onDataChanged(dataEvents);
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        mWearManager.onMessageReceived(messageEvent);
    }

    @Override
    public void onPeerConnected(Node peer) {
        super.onPeerConnected(peer);
        mWearManager.onPeerConnected(peer);
    }

    @Override
    public void onPeerDisconnected(Node peer) {
        super.onPeerDisconnected(peer);
        mWearManager.onPeerDisconnected(peer);
    }

    @Override
    public void onConnectedNodes(List<Node> connectedNodes) {
        super.onConnectedNodes(connectedNodes);
        mWearManager.onConnectedNodes(connectedNodes);
    }

    @Override
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        super.onCapabilityChanged(capabilityInfo);
        mWearManager.onCapabilityChanged(capabilityInfo);
    }

    @Override
    public void onChannelOpened(Channel channel) {
        super.onChannelOpened(channel);
        mWearManager.onChannelOpened(channel);
    }

    @Override
    public void onChannelClosed(Channel channel, int closeReason, int appSpecificErrorCode) {
        super.onChannelClosed(channel, closeReason, appSpecificErrorCode);
        mWearManager.onChannelClosed(channel, closeReason, appSpecificErrorCode);
    }

    @Override
    public void onInputClosed(Channel channel, int closeReason, int appSpecificErrorCode) {
        super.onInputClosed(channel, closeReason, appSpecificErrorCode);
        mWearManager.onInputClosed(channel, closeReason, appSpecificErrorCode);
    }

    @Override
    public void onOutputClosed(Channel channel, int closeReason, int appSpecificErrorCode) {
        super.onOutputClosed(channel, closeReason, appSpecificErrorCode);
        mWearManager.onOutputClosed(channel, closeReason, appSpecificErrorCode);
    }
}
