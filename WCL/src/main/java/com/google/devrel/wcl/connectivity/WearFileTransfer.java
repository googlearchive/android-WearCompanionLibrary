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

import android.net.Uri;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableStatusCodes;
import com.google.devrel.wcl.Constants;
import com.google.devrel.wcl.Utils;
import com.google.devrel.wcl.WearManager;
import com.google.devrel.wcl.callbacks.WearConsumer;

import java.io.File;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.UUID;

/**
 * A helper class to facilitate the transfer of a file or bytes across the wearable network. For
 * a pure file transfer, clients should use a pattern similar to the following:<p/>
 * On the sender side:
 * <pre>
 * WearFileTransfer fileTransfer = new WearFileTransfer.Builder(targetNode)
 *     .setTargetName(targetFileName)
 *     .setFile(fileToBeSent))
 *     .setOnFileTransferResultListener(myResultListener)
 *     .build();
 * fileTransfer.transfer();
 * </pre>
 * On the receiver end, the library handles the rest and saves the file in the private data storage
 * for the app, under the "targetFileName". If the receiver node wants to be notified that a file
 * has been received and saved successfully, they can register a WearConsumer listener and override
 * {@link WearConsumer#onWearableFileReceivedResult(int, String, File, String)}
 * which includes the status, the unique requestId for the transfer, the File object pointing to the
 * saved file and the original name of the file on the sender side:
 * <pre>
 * mWearConsumer = new AbstractWearConsumer() {
 *
 *     public void onWearableFileReceivedResult(int statusCode, String requestId,
 *             File savedFile, String originalName) {
 *         Log.d(TAG, String.format(
 *             "File Received: status=%d, requestId=%s, savedLocation=%s, originalName=%s",
 *             statusCode, requestId, savedFile.getAbsolutePath(), originalName));
 *     }
 * }
 * </pre>
 * It is also possible to use low-level apis based on
 * {@link com.google.android.gms.wearable.ChannelApi} to open a channel and create an
 * {@link OutputStream} on the sender side and and {@link java.io.InputStream} on the other side to
 * transfer data. This can be done by following a pattern similar to this:
 *
 * On the sender side:
 * <pre>
 * WearFileTransfer fileTransfer = new WearFileTransfer.Builder(targetNode)
 *        .setOnChannelOutputStreamListener(listener)
 *        .build();
 * fileTransfer.requestOutputStream();
 * </pre>
 * The {@link WearConsumer#onOutputStreamForChannelReady(int, Channel, OutputStream)} listener will
 * receive a reference to the status for this request, a channel and an
 * {@link OutputStream} to write to. On the receiver side, the library can inform the client that a
 * channel has been opened and a callback will provide a reference to the channel and the
 * {@link java.io.InputStream} that the client can use to receive the data:
 * <pre>
 * mWearConsumer = new AbstractWearConsumer() {
 *
 *         public void onWearableInputStreamForChannelOpened(int statusCode, String requestId,
 *             Channel channel, InputStream inputStream) {
 *                 //get the data from the inputStream ..
 *         }
 * }
 * </pre>
 */
public class WearFileTransfer {

    private static final String TAG = "WearFileTransfer";
    public static final String PARAM_NAME = "name";
    public static final String PARAM_SIZE = "size";
    public static final String PARAM_REQUEST_ID = "request-id";
    private static final String PATH_SEPARATOR = "/";
    private final File mFile;
    private final String mTargetName;
    private final Node mNode;
    private final String mRequestId;
    private final OnFileTransferRequestListener mFileTransferResultListener;
    private final OnWearableChannelOutputStreamListener mOnChannelOutputStreamListener;

    private WearFileTransfer(Builder builder) {
        mFile = builder.mFile;
        mTargetName = builder.mTargetName;
        mNode = builder.mNode;
        mRequestId = builder.mRequestId;
        mFileTransferResultListener = builder.mFileTransferResultListener;
        mOnChannelOutputStreamListener = builder.mOnChannelOutputStreamListener;
    }

    /** Builder for {@link WearFileTransfer}. */
    public static final class Builder {
        private File mFile;
        private String mTargetName;
        private Node mNode;
        private String mRequestId;
        private OnFileTransferRequestListener mFileTransferResultListener;
        private OnWearableChannelOutputStreamListener mOnChannelOutputStreamListener;

        /**
         * A Builder class to help with the construction of a {@link WearFileTransfer} object.
         *
         * @param targetNode The {@link Node} to send the file or bytes to.
         */
        public Builder(Node targetNode) {
            mNode = Utils.assertNotNull(targetNode, "targetNode");
        }

        /**
         * Sets the original file that should be transferred.
         */
        public Builder setFile(File file) {
            mFile = Utils.assertNotNull(file, "file");
            if (!file.exists()) {
                throw new IllegalArgumentException(
                        "The file to be transferred doesn't exist: " + file.getAbsolutePath());
            }
            return this;
        }

        /**
         * Sets the name of the transferred file at the destination. This is optional and if not
         * set, the name of the original file will be used.
         */
        public Builder setTargetName(@Nullable String targetName) {
            mTargetName = targetName;
            return this;
        }

        /**
         * Sets the listener {@link OnWearableChannelOutputStreamListener} that should
         * be notified when an output stream is available. This is used when we are using
         * low-level apis to transfer data.
         */
        public Builder setOnChannelOutputStreamListener(
                OnWearableChannelOutputStreamListener onWearableChannelOutputStreamListener) {
            mOnChannelOutputStreamListener = Utils
                    .assertNotNull(onWearableChannelOutputStreamListener,
                            "onWearableChannelOutputStreamListener");
            return this;
        }

        /**
         * Sets the (optional) requestId. This will be used to correlate the request and its
         * corresponding results and responses. If not provided, a unique requestId will be created
         * automatically.
         */
        public Builder setRequestId(@Nullable String requestId) {
            mRequestId = requestId;
            return this;
        }

        /**
         * Sets an optional {@link OnFileTransferRequestListener} listener that will be notified
         * of the status of {@link #startTransfer()}.
         */
        public Builder setOnFileTransferResultListener(
                OnFileTransferRequestListener nFileTransferRequestListener) {
            mFileTransferResultListener = Utils
                    .assertNotNull(nFileTransferRequestListener, "nFileTransferRequestListener");
            return this;
        }

        /**
         * Builds the {@link WearFileTransfer} object.
         */
        public WearFileTransfer build() {
            if (TextUtils.isEmpty(mRequestId)) {
                mRequestId = UUID.randomUUID().toString();
            }
            if (TextUtils.isEmpty(mTargetName) && mFile != null) {
                mTargetName = mFile.getName();
            }
            return new WearFileTransfer(this);
        }

    }

    /**
     * Initiates the transfer of a file to the target node. Using this method, a file can be
     * transferred without any additional work on the receiver end although the receiver can
     * register a {@link WearConsumer#onWearableFileReceivedResult(int, String, File, String)}
     * to be notified when the transfer is completed or when it fails.
     * Using {@link WearConsumer#onWearableSendFileResult(int, String)}, the sender node can also
     * learn about the status of the file transfer request.
     */
    public void startTransfer() {
        assertFileTransferParams();
        final Uri uri = Uri.fromFile(mFile);
        String path = buildPath(mTargetName, mRequestId, mFile.length());
        final WearManager wearManager = WearManager.getInstance();
        wearManager.openChannel(mNode, path, new OnChannelReadyListener() {
            @Override
            public void onChannelReady(int statusCode, Channel channel) {
                if (statusCode != WearableStatusCodes.SUCCESS) {
                    Log.e(TAG, "transfer(): Failed to open channel; status code= " + statusCode);
                    if (mFileTransferResultListener != null) {
                        mFileTransferResultListener.onFileTransferStatusResult(statusCode);
                    }
                    return;
                }

                wearManager.sendFile(mRequestId, channel, uri, 0, -1, new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.getStatusCode() != WearableStatusCodes.SUCCESS) {
                            Log.e(TAG, "transfer(): Failed to send file; status code= " + status
                                    .getStatusCode());
                        }
                        if (mFileTransferResultListener != null) {
                            mFileTransferResultListener
                                    .onFileTransferStatusResult(status.getStatusCode());
                        }
                    }
                });
            }
        });
    }

    /**
     * Opens a channel and an {@link OutputStream} to transfer data. If the
     * intention is to transfer a file, it is recommended to use {@link #startTransfer()} method
     * instead. Using this api, the sender client needs to use
     * {@link WearConsumer#onWearableInputStreamForChannelOpened} listener to receive an
     * {@link java.io.InputStream} for writing data to. On the receiver node, the client has to
     * register to {@link WearConsumer#onOutputStreamForChannelReady(int, Channel, OutputStream)}
     * to be notified when an {@link java.io.InputStream} is available to read the bytes from.
     */
    public void requestOutputStream() {
        assertStreamParams();
        String path = Constants.PATH_FILE_TRANSFER_TYPE_STREAM + mRequestId;
        final WearManager wearManager = WearManager.getInstance();
        wearManager.getOutputStreamViaChannel(mNode, path, mOnChannelOutputStreamListener);
    }

    private String buildPath(String name, String requestId, long size) {
        String encodedName = null;
        try {
            encodedName = URLEncoder.encode(name, "utf-8");
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "buildPath(): Failed to encode name " + name, e);
        }
        return Constants.PATH_FILE_TRANSFER_TYPE_FILE + encodedName + PATH_SEPARATOR + size
                + PATH_SEPARATOR + requestId;
    }

    private void assertStreamParams() {
        if (mOnChannelOutputStreamListener == null) {
            throw new IllegalArgumentException("An OnChannelOutputStreamListener should be set");
        }
    }

    private void assertFileTransferParams() {
        if (mFile == null) {
            throw new IllegalArgumentException("File to transfer is missing");
        }
    }

    /**
     * An interface to use when a request for opening an {@link OutputStream} is made. Clients need
     * to register an implementation of this interface when they make a request for an
     * {@link OutputStream} to be notified when one is available.
     */
    public interface OnWearableChannelOutputStreamListener {

        /**
         * Is called when the request for opening an {@link OutputStream} is fulfilled.
         * {@code statusCode} shows the status code of the result and if successful, the fields
         * {@code channel} and {@code outputSteam} will, respectively, provide access to the
         * underlying {@link Channel} and the {@link OutputStream} that clients can use to write
         * data to. If the request fails, these two field will be {@code null}
         */
        void onOutputStreamForChannelReady(int statusCode, Channel channel,
                OutputStream outputStream);
    }

    /**
     * Internal only. An interface to provide feedback when the result for opening a channel is
     * available.
     */
    public interface OnChannelReadyListener {

        /**
         * Is called when the result of the request for opening a channel is ready. The
         * {@code statusCode} can be used to decide if the request was successful or not. If
         * successful, the {@code channel} will be the resulting channel, otherwise it will be
         * {@code null}.
         */
        void onChannelReady(int statusCode, Channel channel);
    }

    public interface OnFileTransferRequestListener {
        void onFileTransferStatusResult(int statusCode);
    }

    /**
     * A helper interface that clients can use to report the progress of a transfer.
     */
    public interface OnChannelTransferProgressListener {

        /**
         * Show the progress of data transfer. {@code progress} indicates the progress and
         * {@code max} shows the total (max) value.
         */
        void onProgressUpdated(long progress, long total);
    }

}
