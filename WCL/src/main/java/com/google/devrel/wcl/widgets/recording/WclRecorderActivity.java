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
 * limitations under the License.
 */

package com.google.devrel.wcl.widgets.recording;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.ColorRes;
import android.support.v4.content.ContextCompat;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.CircularButton;
import android.util.Log;
import android.view.View;

import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableStatusCodes;
import com.google.devrel.wcl.R;
import com.google.devrel.wcl.Utils;
import com.google.devrel.wcl.WearManager;
import com.google.devrel.wcl.connectivity.WearFileTransfer;
import com.google.devrel.wcl.filters.NearbyFilter;
import com.google.devrel.wcl.filters.SingleNodeFilter;

import java.io.OutputStream;
import java.util.Set;

/**
 * An activity that shows a circular button for microphone to enable users record or stream sound.
 * Client applications can declare this activity in their manifest and then start this activity by
 * calling {@link android.app.Activity#startActivityForResult(Intent, int)}. Recording starts when
 * user clicks on the button and ends on a second click; ending recording will result in the
 * activity to close; user can also swipe away the activity. When this activity
 * finishes, it will return an {@code Intent} to the caller
 * (via {@link android.app.Activity#onActivityResult(int, int, Intent)}). A number of configuration
 * parameters can be specified via the intent that starts this activity:
 * <ul>
 *     <li>{@link #EXTRA_OFF_COLOR_RES_ID}
 *     <li>{@link #EXTRA_ON_COLOR_RES_ID}
 *     <li>{@link #EXTRA_MIC_RES_ID}
 *     <li>{@link #EXTRA_RIPPLE_COLOR_RES_ID}
 *     <li>{@link #EXTRA_RECORDING_FILE_NAME}
 *     <li>{@link #EXTRA_STREAMING}
 *     <li>{@link #EXTRA_MIC_RES_ID}
 *     <li>{@link #EXTRA_NODE_CAPABILITY}
 *     <li>{@link #EXTRA_NODE_ID}
 * </ul>
 *
 * When the activity ends, the following parameters will be in the {@link Intent} returned
 * in {@link android.app.Activity#onActivityResult(int, int, Intent)}:
 * <ul>
 *     <li>{@link #EXTRA_RECORDING_FILE_NAME}
 *     <li>{@link #EXTRA_RECORDING_STATUS}
 * </ul>
 *
 * The status returned via {@code EXTRA_RECORDING_STATUS} can be one of the following:
 * <ul>
 *     <li>{@link #STATUS_SUCCESS}
 *     <li>{@link #STATUS_ERROR_IO_EXCEPTION}
 *     <li>{@link #STATUS_ERROR_NODE_NOT_FOUND}
 *     <li>{@link #STATUS_ERROR_INVALID_CONFIGURATION}
 * </ul>
 */
public class WclRecorderActivity extends WearableActivity {

    private static final String TAG = "WclRecorderActivity";

    /**
     * The key used to set the color of the button around the "microphone" image when recording is
     * inactive.
     */
    public static final String EXTRA_OFF_COLOR_RES_ID
            = "com.google.devrel.wcl.widgets.wclrecorderactivity.EXTRA_OFF_COLOR_RES_ID";

    /**
     * The key used to set the color of the button around the "microphone" image when recording is
     * active.
     */
    public static final String EXTRA_ON_COLOR_RES_ID
            = "com.google.devrel.wcl.widgets.wclrecorderactivity.EXTRA_ON_COLOR_RES_ID";

    /**
     * The key to set the name of the output file for recording.
     */
    public static final String EXTRA_RECORDING_FILE_NAME
            = "com.google.devrel.wcl.widgets.wclrecorderactivity.EXTRA_RECORDING_FILE_NAME";

    /**
     * The key to set if recorded sound should be streamed.
     */
    public static final String EXTRA_STREAMING
            = "com.google.devrel.wcl.widgets.wclrecorderactivity.EXTRA_STREAMING";

    /**
     * The key to obtain the status of recording as it is reported back to the caller activity.
     */
    public static final String EXTRA_RECORDING_STATUS
            = "com.google.devrel.wcl.widgets.wclrecorderactivity.EXTRA_RECORDING_STATUS";

    /**
     * The key for setting the Resource Id for the bitmap used for the "microphone".
     */
    public static final String EXTRA_MIC_RES_ID
            = "com.google.devrel.wcl.widgets.wclrecorderactivity.EXTRA_MIC_RES_ID";


    /**
     * The key to set the capability for streaming.Internally, if more than one connected node
     * provides this capability, one will be selected arbitrarily. Note that when streaming, either
     * this or {@link #EXTRA_NODE_ID} must be set, but not both.
     */
    public static final String EXTRA_NODE_CAPABILITY
            = "com.google.devrel.wcl.widgets.wclrecorderactivity.EXTRA_NODE_CAPABILITY";

    /**
     * The key to set the NodeId for streaming. Note that when streaming, either this or
     * {@link #EXTRA_NODE_CAPABILITY} must be set, but not both.
     */
    public static final String EXTRA_NODE_ID
            = "com.google.devrel.wcl.widgets.wclrecorderactivity.EXTRA_NODE_ID";

    /**
     * The key to set the Resource Id for the color used to create the ripple effect when pressing
     * on the "microphone" image.
     */
    public static final String EXTRA_RIPPLE_COLOR_RES_ID
            = "com.google.devrel.wcl.widgets.wclrecorderactivity.EXTRA_RIPPLE_COLOR_RES_ID";

    /**
     * A status indicating that the recording was done successfully.
     */
    public static final int STATUS_SUCCESS = 0;

    /**
     * Error code indicating that the target node is could not be found.
     *
     * <p>This will be reported to the caller activity as an intent's extra on
     * {@link android.app.Activity#onActivityResult(int, int, Intent)}.
     */
    public static final int STATUS_ERROR_NODE_NOT_FOUND = 1;

    /**
     * Error code indicating that an IO issue was encountered..
     *
     * <p>This will be reported to the caller activity as an intent's extra on
     * {@link android.app.Activity#onActivityResult(int, int, Intent)}.
     */
    public static final int STATUS_ERROR_IO_EXCEPTION = 2;

    /**
     * Error code indicating that the configuration provided in
     * {@link com.google.devrel.wcl.widgets.list.WearableListConfig} was not correct.
     *
     * <p>This will be reported to the caller activity as an intent's extra on
     * {@link android.app.Activity#onActivityResult(int, int, Intent)}.
     */
    public static final int STATUS_ERROR_INVALID_CONFIGURATION = 3;

    /**
     * Error code indicating that the AudioRecord api encountered an issue while trying to record
     * the sound data from the microphone.
     *
     * <p>This will be reported to the caller activity as an intent's extra on
     * {@link android.app.Activity#onActivityResult(int, int, Intent)}.
     */
    public static final int STATUS_ERROR_RECORDER_FAILED = 4;

    @ColorRes private int mOffColorResId = R.color.wcl_voice_recorder_off;
    @ColorRes private int mOnColorResId = R.color.wcl_voice_recorder_on;
    private boolean mRecording;
    private CircularButton mCircularButton;
    private WclSoundManager mSoundManager;
    private String mRecordingFileName;
    private WearManager mWearManager;
    private boolean mStreaming;
    private String mNodeId;
    private String mCapability;
    private MyWearableChannelOutputListener mChannelListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wcl_recorder);
        mWearManager = WearManager.getInstance();
        mCircularButton = (CircularButton) findViewById(R.id.button);
        mCircularButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WclRecorderActivity.this.toggleRecording();
            }
        });
        setAmbientEnabled();
    }

    @Override
    protected void onStop() {
        if (mChannelListener != null) {
            mChannelListener.cleanUp();
            mChannelListener = null;
        }
        if (mSoundManager != null) {
            mSoundManager.cleanUp();
            mSoundManager = null;
        }
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        setUpRecording();
    }

    private void setUpRecording() {
        int rippleColor = R.color.wcl_voice_recorder_ripple;
        int micResource = R.drawable.wcl_voice_recorder_mic;
        mStreaming = false;
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mOffColorResId = extras.getInt(EXTRA_OFF_COLOR_RES_ID, R.color.wcl_voice_recorder_off);
            mOnColorResId = extras.getInt(EXTRA_ON_COLOR_RES_ID, R.color.wcl_voice_recorder_on);
            rippleColor = extras.getInt(EXTRA_RIPPLE_COLOR_RES_ID,
                    R.color.wcl_voice_recorder_ripple);
            micResource = extras.getInt(EXTRA_MIC_RES_ID,
                    R.drawable.wcl_voice_recorder_mic);
            mRecordingFileName = extras.getString(EXTRA_RECORDING_FILE_NAME);
            mStreaming = extras.getBoolean(EXTRA_STREAMING, false);
            mNodeId = extras.getString(EXTRA_NODE_ID);
            mCapability = extras.getString(EXTRA_NODE_CAPABILITY);
        }
        mCircularButton.setColor(ContextCompat.getColor(this, mOffColorResId));
        mCircularButton.setRippleColor(rippleColor);
        mCircularButton.setImageResource(micResource);
    }

    private boolean isNodeSettingsValidForStreaming() {
        if (mCapability != null && mNodeId != null) {
            // we can't have both of these null
            Log.e(TAG, "When streaming, only one of nodeId or capability should be specified");
            Intent intent = new Intent();
            finishWithResult(intent, STATUS_ERROR_INVALID_CONFIGURATION);
            return false;
        } else if (mCapability == null && mNodeId == null) {
            Log.e(TAG, "When streaming, nodeId or capability should be specified");
            Intent intent = new Intent();
            finishWithResult(intent, STATUS_ERROR_INVALID_CONFIGURATION);
            return false;
        }
        return true;
    }

    private Node getNodeForStream() {
       if (mCapability != null) {
            Set<Node> nodes = mWearManager.getNodesForCapability(mCapability,
                    new SingleNodeFilter(new NearbyFilter()));
            if (nodes.isEmpty()) {
                Log.e(TAG, "No node with the specified capability was found");
                return null;
            } else {
                return nodes.iterator().next();
            }

        } else {
            Node node = mWearManager.getNodeById(mNodeId);
            if (node == null) {
                Log.e(TAG, "No node with the specified node id was found");
                return null;
            } else {
                return node;
            }
        }
    }

    private void toggleRecording() {
        if (mRecording) {
            // currently recording, so this click should stop recording
            mRecording = false;
            mSoundManager.stopRecording();
            Intent intent = new Intent();
            intent.putExtra(EXTRA_RECORDING_FILE_NAME, mRecordingFileName);
            finishWithResult(intent, STATUS_SUCCESS);
        } else {
            mCircularButton.setColor(ContextCompat.getColor(this, mOnColorResId));
            if (mStreaming) {
                // we want to stream the data from microphone
                if (!isNodeSettingsValidForStreaming()) {
                    return;
                }
                Node targetNode = getNodeForStream();
                if (targetNode == null) {
                    finishWithResult(new Intent(), STATUS_ERROR_NODE_NOT_FOUND);
                    return;
                }
                mRecording = true;
                mSoundManager = new WclSoundManager(this);
                Utils.LOGD(TAG, "Targeting node: " + targetNode);
                if (mChannelListener != null) {
                    mChannelListener.cleanUp();
                }
                mChannelListener = new MyWearableChannelOutputListener(this, mSoundManager);
                new WearFileTransfer.Builder(targetNode)
                        .setOnChannelOutputStreamListener(mChannelListener)
                        .build()
                        .requestOutputStream();
            } else {
                // we want to save the MIC bytes to a file
                mSoundManager = new WclSoundManager(this);
                mRecording = true;
                mSoundManager.record(mRecordingFileName,
                        new WclSoundManager.OnVoiceRecordingFinishedListener() {
                            @Override
                            public void onRecordingFinished(int reason, String reasonMessage) {
                                finishWithRecorderError(reason);
                            }
                        });
            }
        }
    }

    private void finishWithRecorderError(int reason) {
        Intent intent = new Intent();
        switch (reason) {
            case WclSoundManager.ERROR_AUDIO_RECORD_FAILED:
                finishWithResult(intent, STATUS_ERROR_RECORDER_FAILED);
                break;
            case WclSoundManager.ERROR_INVALID_CONFIGURATION:
                finishWithResult(intent, STATUS_ERROR_INVALID_CONFIGURATION);
                break;
        }
    }

    private void finishWithResult(Intent intent, int status) {
        mSoundManager = null;
        mCircularButton.setColor(ContextCompat.getColor(this, mOffColorResId));
        intent.putExtra(EXTRA_RECORDING_STATUS, status);
        setResult(RESULT_OK, intent);
        finish();
        overridePendingTransition(0, android.R.anim.slide_out_right);
    }

    private static class MyWearableChannelOutputListener
            implements WearFileTransfer.OnWearableChannelOutputStreamListener {

        private WclRecorderActivity mActivity;
        private WclSoundManager mSoundManager;

        MyWearableChannelOutputListener(WclRecorderActivity activity,
                WclSoundManager soundManager) {
            mActivity = activity;
            mSoundManager = soundManager;
        }

        @Override
        public void onOutputStreamForChannelReady(int statusCode, Channel channel,
                OutputStream outputStream) {
            if (statusCode == WearableStatusCodes.SUCCESS) {
                mSoundManager.record(outputStream,
                        new WclSoundManager.OnVoiceRecordingFinishedListener() {
                            @Override
                            public void onRecordingFinished(final int reason,
                                    String reasonMessage) {
                                if (mActivity != null) {
                                    mActivity.finishWithRecorderError(reason);
                                }
                            }
                        });
            } else {
                Log.e(TAG, "Failed to open a channel, status code: " + statusCode);
                Intent intent = new Intent();
                if (mActivity != null) {
                    mActivity.finishWithResult(intent, STATUS_ERROR_IO_EXCEPTION);
                }
                cleanUp();
            }
        }

        private void cleanUp() {
            mActivity = null;
        }
    }
}
