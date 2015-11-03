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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This class registers an {@link Application.ActivityLifecycleCallbacks} with the client
 * application to detect whether the application is in the foreground or background. All methods
 * must be called on the main thread.
 */
public class ApplicationVisibilityDetector {

    private static final int WHAT_UI_VISIBLE = 0;
    private static final int WHAT_UI_HIDDEN = 1;

    /**
     * To avoid having a false alarm when one activity is going away while the next one hasn't come
     * up yet, we add a small delay
     */
    private static final int UI_VISIBILITY_DELAY_MS = 300;
    private static final String TAG = "AppVisibilityDetector";

    private final Handler mHandler = new Handler(Looper.getMainLooper(),
            new UpdateVisibilityHandlerCallback());
    private final List<Listener> mListeners = new CopyOnWriteArrayList<>();
    private final LifecycleCallbacks mLifecycleCallbacks;

    private int mCounter;
    private boolean mUiVisible;
    private int mLastHandledMessage = WHAT_UI_HIDDEN;

    public interface Listener {

        void onAppEnterForeground();

        void onAppEnterBackground();
    }

    /**
     * Builds an {@link ApplicationVisibilityDetector} for the given {@code application}
     */
    public static ApplicationVisibilityDetector forApp(Application application) {
        ApplicationVisibilityDetector detector = new ApplicationVisibilityDetector();
        application.registerActivityLifecycleCallbacks(detector.getLifecycleCallbacks());
        return detector;
    }

    private ApplicationVisibilityDetector() {
        mLifecycleCallbacks = new LifecycleCallbacks();
    }

    private LifecycleCallbacks getLifecycleCallbacks() {
        return mLifecycleCallbacks;
    }

    public void addListener(Listener listener) {
        if (listener == null) {
            throw new NullPointerException("Listener cannot be null");
        }
        mListeners.add(listener);
    }

    public void removeListener(Listener listener) {
        if (listener == null) {
            throw new NullPointerException("Listener cannot be null");
        }
        mListeners.remove(listener);
    }

    private class LifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

        @Override
        public void onActivityResumed(Activity activity) {
           // no-op
        }

        @Override
        public void onActivityPaused(Activity activity) {
            // no-op
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle bundle) {
            // no-op
        }

        @Override
        public void onActivityStarted(Activity activity) {
            Utils.LOGD(TAG, "onActivityResumed()");
            incrementUiCounter();
        }

        @Override
        public void onActivityStopped(Activity activity) {
            Utils.LOGD(TAG, "onActivityPaused()");
            decrementUiCounter();
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
            // no-op
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            // no-op
        }
    }

    private void incrementUiCounter() {
        Utils.LOGD(TAG,
                "decrementUiCounter() Old Values: counter = " + mCounter + ", mUiVisible: " + mUiVisible);
        mCounter++;
        if (!mUiVisible) {
            mUiVisible = true;
            mHandler.removeMessages(WHAT_UI_HIDDEN);
            mHandler.sendEmptyMessageDelayed(WHAT_UI_VISIBLE, UI_VISIBILITY_DELAY_MS);
        }
    }

    private void decrementUiCounter() {
        Utils.LOGD(TAG,
                "decrementUiCounter() Old Values: counter = " + mCounter + ", mUiVisible: " + mUiVisible);
        if (--mCounter <= 0) {
            mCounter = 0;
            if (mUiVisible) {
                mUiVisible = false;
                mHandler.removeMessages(WHAT_UI_VISIBLE);
                mHandler.sendEmptyMessageDelayed(WHAT_UI_HIDDEN, UI_VISIBILITY_DELAY_MS);
            }
        }
    }

    private class UpdateVisibilityHandlerCallback implements Handler.Callback {

        @Override
        public boolean handleMessage(Message msg) {
            if (mLastHandledMessage == msg.what) {
                return true;
            }
            mLastHandledMessage = msg.what;
            if (mLastHandledMessage == WHAT_UI_VISIBLE) {
                for (Listener listener : mListeners) {
                    listener.onAppEnterForeground();
                }
            } else {
                for (Listener listener : mListeners) {
                    listener.onAppEnterBackground();
                }
            }
            return true;
        }
    }
}
