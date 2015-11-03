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

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;

import com.google.devrel.wcl.Constants;
import com.google.devrel.wcl.Utils;

import java.util.Arrays;

/**
 * A simple class to capture the configuration of the WearableListView that is provided by this
 * library. To construct this class, you need to use the {@link Builder}. A typical usage would be
 * <pre>
 * WearableListConfig config = new WearableListConfig.Builder(elements)
 *     .setIcon(ICON_RES_ID)
 *     .setHeader("My Header") // comment out if you don't want any header
 *     .setCheckedIcon(CHECKED_ICON_RES_ID)
 *     .setCheckedIndex(mSelectedIndex)
 *     .setRequestCode(REQUEST_CODE)
 *     .build();
 * WearManager.getInstance().showWearableList(activity, config);
 * </pre>
 * In order to pass this object between activities, one can build a representation of this object as
 * a {@link Bundle} by calling {@link #toBundle()}. Later on, the original object can be retrieved
 * by calling the static method {@link #fromBundle(Bundle)}.
 */
public class WearableListConfig {

    private static final String KEY_DATA_ARRAY = "data-array";
    private static final String KEY_ICON_RES_ID = "icon-res-id";
    private static final String KEY_ICON_RES_IDS = "icon-res-ids";
    private static final String KEY_REQUEST_CODE = "request-code";
    private static final String KEY_CHECKABLE = "checkable";
    private static final String KEY_CHECKED_ICON_RES_ID = "checked-res-id";
    private static final String KEY_CHECKED_INDEX = "checked-index";
    private static final String KEY_AMBIENT = "ambient";
    private static final String KEY_HEADER = "header";


    public static final int NO_SELECTION = -1;
    private final Bundle mBundle;

    private WearableListConfig(Bundle bundle) {
        mBundle = validate(bundle);
    }

    private static Bundle validate(Bundle bundle) {
        String[] dataArray = Utils.assertNotNull(bundle.getStringArray(KEY_DATA_ARRAY), "data");
        int[] iconsArray = bundle.getIntArray(KEY_ICON_RES_IDS);
        if (iconsArray != null && iconsArray.length != dataArray.length) {
            throw new IllegalArgumentException(
                    "The length of array of icons should match the length of data array");
        }
        return bundle;
    }

    /**
     * A Builder to construct an instance of {@link WearableListConfig}. Follow the
     * following pattern:
     * <pre>
     * WearableListConfig config = new WearableListConfig.Builder(elements)
     *     .setIcon(ICON_RES_ID)
     *     .setCheckedIcon(CHECKED_ICON_RES_ID)
     *     .setCheckedIndex(mSelectedIndex)
     *     .setRequestCode(REQUEST_CODE)
     *     .build();
     * </pre>
     */
    public static final class Builder {

        private final Bundle mBundle;

        /**
         * Constructor for the Builder.
         *
         * @param data The String array that is used to show the list items.
         */
        public Builder(String[] data) {
            mBundle = new Bundle();
            mBundle.putStringArray(KEY_DATA_ARRAY, data);
        }

        /**
         * Sets the resource id for the "checked" item; this icon will be used inside the default
         * circle that is used for all rows. Use an 18dp or 24dp icon.
         */
        public Builder setCheckedIcon(int resId) {
            mBundle.putInt(KEY_CHECKED_ICON_RES_ID, resId);
            return this;
        }

        /**
         * Sets the 0-based index of the item in the list that is "checked" to show the previous
         * selection for the items that are represented by this list.
         */
        public Builder setCheckedIndex(int index) {
            mBundle.putInt(KEY_CHECKED_INDEX, index);
            return this;
        }

        /**
         * Sets the resource id of the icon to be used for all rows; this will be put inside the
         * circle icon that is used by default for each row. Use an 18dp or 24dp size icon.
         *
         * @see #setIcons(int[])
         */
        public Builder setIcon(int resId) {
            mBundle.putInt(KEY_ICON_RES_ID, resId);
            return this;
        }

        /**
         * Sets an array of icons that should be used for various rows of data. The size of this
         * array has to exactly match the size of the data. Use an 18dp or 24dp size icon.
         *
         * @see #setIcon(int)
         */
        public Builder setIcons(int[] resIds) {
            mBundle.putIntArray(KEY_ICON_RES_IDS,
                    resIds == null ? null : Arrays.copyOf(resIds, resIds.length));
            return this;
        }

        /**
         * Sets the requestCode for the activity {@link WclWearableListViewActivity} when it is
         * started. This requestCode will be returned when the activity ends so the caller can
         * identify the activity.
         */
        public Builder setRequestCode(int requestCode) {
            mBundle.putInt(KEY_REQUEST_CODE, requestCode);
            return this;
        }

        /**
         * Sets the "checkable" state of the list. If {@code true}, callers can use
         * {@link #setCheckedIcon(int)} and {@link #setCheckedIndex(int)} to visually show which
         * row is selected.
         *
         * @see #setCheckedIcon(int)
         * @see #setCheckedIndex(int)
         */
        public Builder setCheckable(boolean checkable) {
            mBundle.putBoolean(KEY_CHECKABLE, checkable);
            return this;
        }

        /**
         * Sets the ambient flag on the list activity
         */
        public Builder setAmbient(boolean ambient) {
            mBundle.putBoolean(KEY_AMBIENT, ambient);
            return this;
        }

        /**
         * Sets the text for a header for the list. If {@code header} is {@code null}, no
         * header will be shown. If shown, header will scroll up along with the list. Styling of the
         * header can be done from the xml resources, see {@code res/values/wcl_list.xml} for
         * styleable parameters.
         */
        public Builder setHeader(@Nullable String header) {
            mBundle.putString(KEY_HEADER, header);
            return this;
        }

        /**
         * Builds the instance.
         */
        public WearableListConfig build() {
            return new WearableListConfig(mBundle);
        }
    }

    public String[] getDataArray() {
        return mBundle.getStringArray(KEY_DATA_ARRAY);
    }

    public int getCheckedIconResId() {
        return mBundle.getInt(KEY_CHECKED_ICON_RES_ID, NO_SELECTION);
    }

    public int getIconResId() {
        return mBundle.getInt(KEY_ICON_RES_ID, NO_SELECTION);
    }

    @Nullable
    public int[] getIconResIds() {
        return mBundle.getIntArray(KEY_ICON_RES_IDS);
    }

    public int getCheckedIndex() {
        return mBundle.getInt(KEY_CHECKED_INDEX, NO_SELECTION);
    }

    public int getRequestCode() {
        return mBundle.getInt(KEY_REQUEST_CODE);
    }

    public boolean isCheckable() {
        return mBundle.getBoolean(KEY_CHECKABLE, false);
    }

    public boolean isAmbient() {
        return mBundle.getBoolean(KEY_AMBIENT, false);
    }

    public String getHeader() {
        return mBundle.getString(KEY_HEADER);
    }

    /**
     * A class that wraps the data returned in the {@link Intent} that is returned when the
     * {@link WclWearableListViewActivity} activity is returned; the intent is passed to the
     * {@link android.app.Activity#onActivityResult(int, int, Intent)}. The recommended pattern to
     * use this wrapper is the following:
     * <pre>
     *   protected void onActivityResult(int requestCode, int resultCode, Intent data) {
     *       if(resultCode == RESULT_OK){
     *           WearableListConfig.WearableListResult result
     *               = new WearableListConfig.WearableListResult(data, requestCode);
     *           if (result.isHandled()) {
     *               Log.d(TAG, String.format("Position: %d, Value: %s", result.getSelectedIndex(),
     *                   result.getSelectedValue()));
     *               // do as needed with the selections made
     *           } else {
     *               // Some other activity is returning a result here ...
     *           }
     *       }
     *
     *   }
     * </pre>
     */
    public static final class WearableListResult {
        private final int mSelectedIndex;
        private final String mSelectedValue;
        private final boolean mHandled;

        public WearableListResult(Intent intent, int requestCode) {
            if (intent != null
                    && intent.getIntExtra(Constants.KEY_LIST_REQUEST_CODE, -1) == requestCode) {
                mSelectedIndex = intent
                        .getIntExtra(Constants.KEY_LIST_RESPONSE_POSITION, NO_SELECTION);
                mSelectedValue = intent.getStringExtra(Constants.KEY_LIST_RESPONSE_VALUE);
                mHandled = intent.getBooleanExtra(Constants.KEY_LIST_RESPONSE_HANDLED, false);
            } else {
                mSelectedIndex = NO_SELECTION;
                mSelectedValue = null;
                mHandled = false;
            }
        }

        /**
         * Returns the index (or position) of the selected item
         */
        public int getSelectedIndex() {
            return mSelectedIndex;
        }

        /**
         * Returns the String vale of the selected item
         */
        public String getSelectedValue() {
            return mSelectedValue;
        }

        /**
         * Returns {@code true} if and only of the result is considered handled, i.e. if the
         * result is from the {@link WclWearableListViewActivity}. Clients that may open different
         * activities need to use this method to handle the result appropriately.
         */
        public boolean isHandled() {
            return mHandled;
        }
    }

    /**
     * Builds a {@link Bundle} from this object. This can be used to pass an instance of this class
     * between activities.
     *
     * @see #fromBundle(Bundle)
     */
    public Bundle toBundle() {
        return new Bundle(mBundle);
    }

    /**
     * Rebuilds an instance of {@link WearableListConfig} from its representation as a
     * {@link Bundle}
     *
     * @param bundle The {@link Bundle} representation of the object
     * @see #toBundle()
     */
    public static WearableListConfig fromBundle(Bundle bundle) {
        return new WearableListConfig.Builder(bundle.getStringArray(KEY_DATA_ARRAY))
                .setCheckedIcon(bundle.getInt(KEY_CHECKED_ICON_RES_ID, NO_SELECTION))
                .setCheckedIndex(bundle.getInt(KEY_CHECKED_INDEX, NO_SELECTION))
                .setIcon(bundle.getInt(KEY_ICON_RES_ID, NO_SELECTION))
                .setIcons(bundle.getIntArray(KEY_ICON_RES_IDS))
                .setRequestCode(bundle.getInt(KEY_REQUEST_CODE))
                .setCheckable(bundle.getBoolean(KEY_CHECKABLE, false))
                .setAmbient(bundle.getBoolean(KEY_AMBIENT, false))
                .setHeader(bundle.getString(KEY_HEADER))
                .build();
    }

}
