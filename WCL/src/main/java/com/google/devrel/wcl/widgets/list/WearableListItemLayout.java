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

package com.google.devrel.wcl.widgets.list;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.support.wearable.view.CircledImageView;
import android.support.wearable.view.WearableListView;
import android.util.AttributeSet;
import android.util.Property;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.devrel.wcl.R;

/**
 * A simple layout that presents a row for a list, made of a simple circle and a line of text.
 * The off-center items are faded out and the centered one has a larger circle to stand out.
 */
public class WearableListItemLayout extends LinearLayout
        implements WearableListView.OnCenterProximityListener {

    /**
     * Animation duration for changing the look of centered <--> off-centered items
     */
    public static final long DEFAULT_ANIMATION_DURATION_MS = 150;

    private CircledImageView mCircle;
    private TextView mTextView;
    private final float mFadedTextAlpha;
    private final int mFadedCircleColor;
    private final int mChosenCircleColor;
    private float minValue;
    private float maxValue;
    private ObjectAnimator scalingUpAnimator;
    private ObjectAnimator scalingDownAnimator;
    private static final Property<CircledImageView, Float> CIRCLE_RADIUS =
            new Property<CircledImageView, Float>(Float.class, "circleRadius") {

                @Override
                public void set(CircledImageView object, Float value) {
                    object.setCircleRadius(value);
                }

                @Override
                public Float get(CircledImageView object) {
                    return object.getCircleRadius();
                }
            };

    public WearableListItemLayout(Context context) {
        this(context, null);
    }

    public WearableListItemLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WearableListItemLayout(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);

        mFadedTextAlpha = getResources()
                .getInteger(R.integer.wcl_action_text_faded_alpha) / 100f;
        mFadedCircleColor = getResources().getColor(R.color.wcl_list_circle_faded_color);
        mChosenCircleColor = getResources().getColor(R.color.wcl_list_circle_selected_color);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mCircle = (CircledImageView) findViewById(R.id.circle);
        mTextView = (TextView) findViewById(R.id.text);
        Resources res = getResources();
        minValue = res.getDimension(R.dimen.wcl_list_circle_radius);
        mCircle.setCircleRadius(minValue);
        maxValue = res.getDimension(R.dimen.wcl_list_circle_radius_selected);
        scalingUpAnimator = ObjectAnimator.ofFloat(mCircle, CIRCLE_RADIUS, minValue, maxValue);
        scalingUpAnimator.setDuration(DEFAULT_ANIMATION_DURATION_MS);
        scalingDownAnimator = ObjectAnimator.ofFloat(mCircle, CIRCLE_RADIUS, maxValue, minValue);
        scalingDownAnimator.setDuration(DEFAULT_ANIMATION_DURATION_MS);
    }

    @Override
    public void onCenterPosition(boolean animate) {
        if (animate) {
            scalingDownAnimator.cancel();
            if (!scalingUpAnimator.isRunning()) {
                scalingUpAnimator.setFloatValues(mCircle.getCircleRadius(), maxValue);
                scalingUpAnimator.start();
            }
        } else {
            scalingUpAnimator.cancel();
            mCircle.setCircleRadius(maxValue);
        }
        mCircle.setCircleColor(mChosenCircleColor);
        mTextView.setAlpha(1);
    }

    @Override
    public void onNonCenterPosition(boolean animate) {
        scalingUpAnimator.cancel();
        if (animate) {
            if (!scalingDownAnimator.isRunning()) {
                scalingDownAnimator.setFloatValues(mCircle.getCircleRadius(), minValue);
                scalingDownAnimator.start();
            }
        } else {
            scalingDownAnimator.cancel();
            mCircle.setCircleRadius(minValue);
        }
        mCircle.setCircleColor(mFadedCircleColor);
        mTextView.setAlpha(mFadedTextAlpha);
    }

}
