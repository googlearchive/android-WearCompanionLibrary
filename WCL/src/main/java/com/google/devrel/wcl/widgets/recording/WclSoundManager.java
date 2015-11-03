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

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.google.devrel.wcl.Utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A helper class to provide methods to record audio input from the mic and its playback. Recording
 * is supported to a file or to an {@link OutputStream} and playback is supported from a file or
 * an {@link InputStream}. After constructing an instance of this class, you can call the
 * appropriate methods to start or stop recording or playback. Callers can also provide listeners
 * that would be notified when recording or playback tasks end, whether on their own or due to an
 * exception or interruption. Note that for recording, caller should obtain
 * {@code android.permission.RECORD_AUDIO} permission.
 */
public class WclSoundManager {

    private static final String TAG = "WclSoundManager";
    private static final int DEFAULT_SAMPLE_RATE = 8000;
    private static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNELS_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    public static final int SUCCESS = 1;
    public static final int ERROR_IO_EXCEPTION = 2;
    public static final int ERROR_PLAYBACK = 3;
    public static final int INTERRUPTED = 4;
    public static final int ERROR_FILE_NOT_FOUND = 5;
    public static final int ERROR_INVALID_CONFIGURATION = 6;
    public static final int ERROR_AUDIO_RECORD_FAILED = 7;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({SUCCESS, ERROR_IO_EXCEPTION, ERROR_PLAYBACK, INTERRUPTED, ERROR_FILE_NOT_FOUND,
            ERROR_INVALID_CONFIGURATION})
    public @interface PlaybackFinishedReason {}

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({INTERRUPTED, ERROR_INVALID_CONFIGURATION, ERROR_AUDIO_RECORD_FAILED})
    public @interface RecordingFinishedReason {}

    private final Context mContext;
    private int mSampleRate;
    private int mBufferSize;
    private State mState = State.IDLE;

    private AsyncTask<Void, Void, Integer> mRecordingAsyncTask;
    private AsyncTask<Void, Void, Integer> mPlayingAsyncTask;

    enum State {
        IDLE, RECORDING, PLAYING
    }

    /**
     * Builds an instance of this class which uses the default sample rate
     * {@link #DEFAULT_SAMPLE_RATE}.
     * @see #WclSoundManager(Context, int)
     */
    public WclSoundManager(Context context) {
        this(context, DEFAULT_SAMPLE_RATE);
    }

    /**
     * Builds an instance of this class with the provided {@code sampleRate}.
     */
    public WclSoundManager(Context context, int sampleRate) {
        mContext = Utils.assertNotNull(context, "context");
        mSampleRate = sampleRate;
        mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, CHANNEL_IN, FORMAT);
    }

    /**
     * Starts recording from the mic and writing the result into a file with the given
     * {@code fileName} in the application's private data storage. This method
     * handles various errors gracefully and logs any errors that it encounters.
     *
     * <p><b>Note. </b>This method should be called on the UI thread.
     *
     * @see #record(String, OnVoiceRecordingFinishedListener)
     * @see #record(OutputStream)
     */
    public void record(String fileName) {
        record(fileName, null);
    }

    /**
     * Starts recording from the mic and writing the result into a file with the given
     * {@code fileName} in the application's private data storage. This method
     * handles various errors gracefully and reports the status through the {@code listener} if
     * it is not {@code null}.
     *
     * <p><b>Note. </b>This method should be called on the UI thread.
     *
     * @see OnVoiceRecordingFinishedListener
     * @see #record(String)
     * @see #record(OutputStream)
     * @see #record(OutputStream, OnVoiceRecordingFinishedListener)
     */
    public void record(String fileName, @Nullable final OnVoiceRecordingFinishedListener listener) {
        Utils.assertUiThread();
        if (TextUtils.isEmpty(fileName)) {
            String msg = "Output filename for saving the recording was empty or null";
            Log.e(TAG, msg);
            if (listener != null) {
                listener.onRecordingFinished(ERROR_INVALID_CONFIGURATION, msg);
            }
            return;
        }

        try {
            OutputStream outputStream = mContext.openFileOutput(fileName, Context.MODE_PRIVATE);
            record(outputStream, listener);
        } catch (FileNotFoundException e) {
            String msg = "Failed to open an OutputStream to an internal file";
            Log.e(TAG, msg, e);
            if (listener != null) {
                listener.onRecordingFinished(ERROR_INVALID_CONFIGURATION, msg);
            }
        }
    }

    /**
     * Starts recording from the mic and writing the result to the {@code outputStream}. This method
     * handles various errors gracefully and logs any errors that it encounters. Caller should
     * obtain {@code android.permission.RECORD_AUDIO} permission to record from the microphone.
     *
     * <p><b>Note. </b>This method should be called on the UI thread.
     *
     * @param outputStream The stream to write the recording to.
     * @see #record(OutputStream, OnVoiceRecordingFinishedListener)
     * @see #record(String)
     * @see #record(String, OnVoiceRecordingFinishedListener)
     */
    public void record(final OutputStream outputStream) {
        record(outputStream, null);
    }

    /**
     * Starts recording from the mic and writing the result to the {@code outputStream}. This method
     * handles various errors gracefully and reports the status through the {@code listener} if
     * it is not {@code null}. Upon completion, it closes the {@code outputStream}.  Caller should
     * obtain {@code android.permission.RECORD_AUDIO} permission to record from the microphone.
     *
     * <p><b>Note. </b>This method should be called on the UI thread.
     *
     * @param outputStream The stream to write the recording to. Upon completion, the stream will be
     * closed.
     * @param listener A listener to be called with the status of this call when recording is done.
     * @see OnVoiceRecordingFinishedListener
     * @see #record(OutputStream)
     * @see #record(String)
     * @see #record(String, OnVoiceRecordingFinishedListener)
     */
    public void record(final OutputStream outputStream,
            @Nullable final OnVoiceRecordingFinishedListener listener) {
        Utils.assertUiThread();
        Utils.assertNotNull(outputStream, "outputStream");

        if (mState != State.IDLE) {
            Log.w(TAG, "Requesting to start recording while state was not IDLE");
            return;
        }

        mRecordingAsyncTask = new AsyncTask<Void, Void, Integer>() {

            private AudioRecord mAudioRecord;
            private String mReasonMessage;

            @Override
            protected void onPreExecute() {
                mState = State.RECORDING;
            }

            @Override
            protected Integer doInBackground(Void... params) {
                BufferedOutputStream bufferedOutputStream = null;
                try {
                    bufferedOutputStream = new BufferedOutputStream(outputStream);
                    mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                            mSampleRate, CHANNEL_IN, FORMAT, mBufferSize * 3);
                    byte[] buffer = new byte[mBufferSize];
                    mAudioRecord.startRecording();
                    while (!isCancelled()) {
                        int read = mAudioRecord.read(buffer, 0, buffer.length);
                        bufferedOutputStream.write(buffer, 0, read);
                    }
                    mReasonMessage = "Interrupted";
                    return INTERRUPTED;
                } catch (IOException | NullPointerException | IndexOutOfBoundsException e) {
                    mReasonMessage = "Failed to write the recording data";
                    Log.e(TAG, mReasonMessage, e);
                    return ERROR_AUDIO_RECORD_FAILED;
                } catch (IllegalStateException e) {
                    mReasonMessage = "AudioRecord encountered an error";
                    Log.e(TAG, mReasonMessage, e);
                    return ERROR_AUDIO_RECORD_FAILED;
                } catch (IllegalArgumentException e) {
                    mReasonMessage = "Incorrect arguments were used to set up the AudioRecord";
                    Log.e(TAG, mReasonMessage, e);
                    return ERROR_AUDIO_RECORD_FAILED;
                } finally {
                    if (bufferedOutputStream != null) {
                        try {
                            bufferedOutputStream.close();
                        } catch (IOException e) {
                            // no-op
                        }
                    }

                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        // no-op
                    }
                    mAudioRecord.release();
                    mAudioRecord = null;
                }
            }

            @Override
            protected void onPostExecute(Integer status) {
                mState = State.IDLE;
                mRecordingAsyncTask = null;
                sendRecordingStatus(status, mReasonMessage);
            }

            @Override
            protected void onCancelled() {
                if (mState == State.RECORDING) {
                    Utils.LOGD(TAG, "Stopping the recording ...");
                    mState = State.IDLE;
                } else {
                    Log.w(TAG, "Requesting to stop recording while state was not RECORDING");
                }
                mRecordingAsyncTask = null;
                sendRecordingStatus(INTERRUPTED, "Interrupted");
            }

            private void sendRecordingStatus(int reasonCode, String reasonMessage) {
                if (listener != null) {
                    listener.onRecordingFinished(reasonCode, reasonMessage);
                }
            }
        };
        mRecordingAsyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void stopRecording() {
        if (mRecordingAsyncTask != null) {
            mRecordingAsyncTask.cancel(true);
        }
    }

    /**
     * Plays a recorded audio file from the application's private data storage with the given name
     * {@code fileName}. This method handles errors gracefully and logs them if one encountered.
     *
     * <p><b>Note. </b>This method should be called on the UI thread.
     *
     * @see #play(InputStream)
     * @see #play(InputStream, OnVoicePlaybackFinishedListener)
     * @see #play(String, OnVoicePlaybackFinishedListener)
     */
    public void play(final String fileName) {
        play(fileName, null);
    }

    /**
     * Plays a recorded audio file from the application's private data storage with the given name
     * {@code fileName}.
     *
     * <p><b>Note. </b>This method should be called on the UI thread.
     *
     * @see #play(InputStream)
     */
    public void play(final String fileName, @Nullable OnVoicePlaybackFinishedListener listener) {
        Utils.assertUiThread();

        if (TextUtils.isEmpty(fileName)) {
            String msg = "fileName cannot be empty or null";
            Log.e(TAG, msg);
            if (listener != null) {
                listener.onPlaybackFinished(ERROR_INVALID_CONFIGURATION, msg);
            }
            return;
        }
        try {
            InputStream inputStream =  mContext.openFileInput(fileName);
            play(inputStream, listener);
        } catch (FileNotFoundException e) {
            String msg = "Failed to find the file for playing";
            Log.e(TAG, msg, e);
            if (listener != null) {
                listener.onPlaybackFinished(ERROR_FILE_NOT_FOUND, msg);
            }
        }
    }

    /**
     * Starts the playback of incoming bytes from the provided {@code inputStream}. This method
     * handles various errors internally and ends after logging them, it gracefully ends. This
     * method will close the
     * {@code inputStream} upon completion.
     *
     * <p><b>Note. </b>This method should be called on the UI thread.
     *
     * @param inputStream The InputStream to read from. Upon completion, it will be closed.
     * @see #play(InputStream, OnVoicePlaybackFinishedListener)
     * @see #play(String)
     * @see #play(String, OnVoicePlaybackFinishedListener)
     */
    public void play(final InputStream inputStream) {
        play(inputStream, null);
    }

    /**
     * Starts the playback of a recorded bytes from the provided {@code inputStream}. This method
     * handles various errors internally and ends gracefully when one is encountered. It calls the
     * {@code listener} to report its status when it ends. This method will close the
     * {@code inputStream} upon completion.
     *
     * <p><b>Note. </b>This method should be called on the UI thread.
     *
     * @param inputStream The InputStream to read from. It will be closed upon completion.
     * @see OnVoicePlaybackFinishedListener
     * @see #play(InputStream)
     * @see #play(String)
     * @see #play(String, OnVoicePlaybackFinishedListener)
     */
    public void play(final InputStream inputStream,
            @Nullable final OnVoicePlaybackFinishedListener listener) {
        Utils.assertUiThread();

        if (mState != State.IDLE) {
            Log.w(TAG, "Requesting to play while state was not IDLE");
            return;
        }

        if (inputStream == null) {
            String msg = "inputStream cannot be null";
            Log.e(TAG, msg);
            if (listener != null) {
                listener.onPlaybackFinished(ERROR_INVALID_CONFIGURATION, msg);
            }
            return;
        }

        final int minimumBufferSize = AudioTrack
                .getMinBufferSize(mSampleRate, CHANNELS_OUT, FORMAT);

        mPlayingAsyncTask = new AsyncTask<Void, Void, Integer>() {

            private AudioTrack mAudioTrack;
            private String mReasonMessage;

            @Override
            protected void onPreExecute() {
                AudioManager audioManager = (AudioManager) mContext
                        .getSystemService(Context.AUDIO_SERVICE);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0 /* flags */);
                mState = State.PLAYING;
            }

            @Override
            protected Integer doInBackground(Void... params) {
                BufferedInputStream bis = null;
                try {
                    bis = new BufferedInputStream(inputStream);
                    int read;
                    mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate,
                            CHANNELS_OUT, FORMAT, minimumBufferSize, AudioTrack.MODE_STREAM);
                    byte[] buffer = new byte[minimumBufferSize * 2];
                    mAudioTrack.play();
                    while (!isCancelled() && (read = bis.read(buffer, 0, buffer.length)) > 0) {
                        mAudioTrack.write(buffer, 0, read);
                    }
                } catch (IOException e) {
                    mReasonMessage = "Failed to read the sound file into a byte array";
                    Log.e(TAG, mReasonMessage, e);
                    return ERROR_IO_EXCEPTION;
                } catch (IllegalStateException e) {
                    mReasonMessage = "Failed to play the audio file";
                    Log.e(TAG, mReasonMessage, e);
                    return ERROR_PLAYBACK;
                } catch (IllegalArgumentException e) {
                    mReasonMessage = "Wrong parameters were used to instantiate the AudioTrack";
                    Log.e(TAG, mReasonMessage, e);
                    return ERROR_PLAYBACK;
                } finally {
                    try {
                        inputStream.close();
                    } catch (IOException e) { /* ignore */}
                    try {
                        if (bis != null) {
                            bis.close();
                        }
                    } catch (IOException e) { /* ignore */}

                    mAudioTrack.release();
                }
                return SUCCESS;
            }

            @Override
            protected void onPostExecute(Integer status) {
                cleanup(status, mReasonMessage);
            }

            @Override
            protected void onCancelled() {
                cleanup(INTERRUPTED, "Interrupted");
            }

            private void cleanup(int status, String reasonMessage) {
                sendPlayingStatus(status, reasonMessage);
                mState = State.IDLE;
                mPlayingAsyncTask = null;
            }
            private void sendPlayingStatus(int reasonCode, String reasonMessage) {
                if (listener != null) {
                    listener.onPlaybackFinished(reasonCode, reasonMessage);
                }
            }

        };

        mPlayingAsyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Stops the playback
     */
    public void stopPlaying() {
        if (mPlayingAsyncTask != null) {
            mPlayingAsyncTask.cancel(true);
        }
    }

    /**
     * An interface that can inform the caller about the reason the playback was ended.
     */
    public interface OnVoicePlaybackFinishedListener {

        /**
         * Called when the playback of the audio file ends. This will be called on the UI thread.
         *
         * @param reason Can be
         * <ul>
         *     <li>{@link #SUCCESS} Playback ended successfully</li>
         *     <li>{@link #ERROR_IO_EXCEPTION} An IO Exception was encountered</li>
         *     <li>{@link #ERROR_PLAYBACK} Player encountered an {@link IllegalStateException}</li>
         *     <li>{@link #ERROR_FILE_NOT_FOUND} File was not found</li>
         *     <li>{@link #INTERRUPTED} Playback was interrupted</li>
         * </ul>
         * @param reasonMessage A textual reason, may be {@code null}
         */
        void onPlaybackFinished(@PlaybackFinishedReason int reason, @Nullable String reasonMessage);
    }

    /**
     * An interface to report the result of a recording request.
     */
    public interface OnVoiceRecordingFinishedListener {

        /**
         * Called when recording ends normally or with an exception.
         *
         * @param reason can be
         * <ul>
         *     <li>{@link #ERROR_INVALID_CONFIGURATION} Configuration is not valid</li>
         *     <li>{@link #ERROR_AUDIO_RECORD_FAILED} AudioRecorder has failed</li>
         *     <li>{@link #INTERRUPTED} Recording was interrupted</li>
         * </ul>
         * @param reasonMessage A textual reason, may be {@code null}
         */
        void onRecordingFinished(@RecordingFinishedReason int reason,
                @Nullable String reasonMessage);
    }

    /**
     * Cleans up some resources related to {@link AudioTrack} and {@link AudioRecord}
     */
    public void cleanUp() {
        Utils.LOGD(TAG, "cleanUp() is called");
        stopPlaying();
        stopRecording();
    }

}
