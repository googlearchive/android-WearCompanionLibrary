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

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.Node;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * A set of static utility methods used in the library or made available to the client
 * applications.
 */
public class Utils {

    public static final int WIFI_DISABLED = 1;
    public static final int WIFI_NO_CONNECTION = 2;
    public static final int WIFI_CONNECTED = 3;

    private static final boolean DEBUG = false;

    /**
     * Returns the status of wifi connectivity. The possible states are
     * <ul>
     *     <li>WIFI_DISABLED</li>
     *     <li>WIFI_NO_CONNECTION</li>
     *     <li>WIFI_CONNECTED</li>
     * </ul>
     */
    public static int getWifiConnectivityStatus(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled()) {
            return WIFI_DISABLED;
        }
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (!networkInfo.isConnected()) {
            return WIFI_NO_CONNECTION;
        }
        return WIFI_CONNECTED;
    }

    /**
     * Returns one nearby node from {@code nodes}, or {@code null} if there is no nearby node,
     */
    @Nullable
    public static Node filterForNearby(@Nullable Set<Node> nodes) {
        if (nodes != null && !nodes.isEmpty()) {
            for (Node node : nodes) {
                if (node.isNearby()) {
                    return node;
                }
            }
        }
        return null;
    }

    /**
     * Returns {@code true} if and only if the current thread is the UI thread.
     */
    public static boolean isUiThread() {
        return Looper.getMainLooper().equals(Looper.myLooper());
    }

    /**
     * Asserts that the current thread is the UI thread; if not, this method throws an
     * {@link IllegalStateException}.
     */
    public static void assertUiThread() {
        if (!isUiThread()) {
            throw new IllegalStateException("Not a UI thread");
        }
    }

    /**
     * Asserts that the current thread is a worker (i.e. non-UI) thread; if not, this
     * method throws an {@link IllegalStateException}.
     */
    public static void assertNonUiThread() {
        if (isUiThread()) {
            throw new IllegalStateException("Not a non-UI thread");
        }
    }

    /**
     * Returns the {@code object} if it is not {@code null}, or throws a
     * {@link NullPointerException} otherwise.
     *
     * @param object The object to inspect
     * @param name A name for the object to be used in the NPE message
     */
    public static <T> T assertNotNull(T object, String name) {
        if (object == null) {
            throw new NullPointerException(name + " cannot be null");
        }
        return object;
    }

    /**
     * Asserts that the {@code string} is not empty or {@code null}. It throws  an
     * {@link IllegalArgumentException} if it is.
     *
     * @param string The string to inspect
     * @param name A name for the string to be used in the NPE message
     */
    public static void assertNotEmpty(String string, String name) {
        if (TextUtils.isEmpty(string)) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
    }

    /**
     * Builds an {@link com.google.android.gms.wearable.Asset} from a bitmap. The image that we get
     * back from the camera in "data" is a thumbnail size. Typically, your image should not exceed
     * 320x320 and if you want to have zoom and parallax effect in your app, limit the size of your
     * image to 640x400. Resize your image before transferring to your wearable device.
     */
    public static Asset toAsset(Bitmap bitmap) {
        ByteArrayOutputStream byteStream = null;
        try {
            byteStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
            return Asset.createFromBytes(byteStream.toByteArray());
        } finally {
            if (null != byteStream) {
                try {
                    byteStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    /**
     * Determines if the wear device has a built-in speaker or not.
     *
     * <p><b>Important: </b>This method should only be called on a wear device; the return value on
     * a non-wear device can be trusted if and only if the device is running  android version M+.
     */
    public static final boolean wearHasSpeaker(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PackageManager packageManager = context.getPackageManager();
            // The results from AudioManager.getDevices can't be trusted unless the device
            // advertises FEATURE_AUDIO_OUTPUT.
            if (!packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)) {
                return false;
            }
            AudioManager audioManager = (AudioManager) context
                    .getSystemService(Context.AUDIO_SERVICE);
            AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo device : devices) {
                if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    return true;
                }
            }
        }
        return false;

    }

    /**
     * Returns a random UUID
     */
    public static String buildUuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * A simple wrapper around Log.d
     */
    public static void LOGD(String tag, String message) {
        if (DEBUG || Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, "[v" + WearManager.getInstance().getVersion() + "] " + message);
        }
    }
}
