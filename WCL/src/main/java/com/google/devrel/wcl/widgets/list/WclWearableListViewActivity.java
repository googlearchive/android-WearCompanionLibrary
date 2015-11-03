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

import android.app.Activity;
import android.content.Intent;
import android.support.wearable.view.WearableListView;

import com.google.devrel.wcl.Constants;
import com.google.devrel.wcl.WearManager;

/**
 * An activity that embeds a {@link WearableListView}. Clients can start this activity by calling
 * {@link WearManager#showWearableList(Activity, WearableListConfig)} like the following example:
 *
 * <pre>
 *     String[] data = new String[] {"List Item 1", "List Item 2", "List Item 3", "List Item 4"};
 *     WearableListConfig config = new WearableListConfig.Builder(data)
 *         .setCheckedIndex(1) // index starts from 0
 *         .setRequestCode(REQUEST_CODE_LIST_DIALOG)
 *         .setIcon(R.drawable.ic_person_24dp)
 *         .setCheckable(true)
 *         .build();
 *     WearManager.getInstance().showWearableList(this, config);
 * </pre>
 *
 * After a selection is made, the caller activity will receiver the response in
 * {@link Activity#onActivityResult(int, int, Intent)}:
 *
 * <pre>
 *     protected void onActivityResult(int requestCode, int resultCode, Intent data) {
 *         if(resultCode == RESULT_OK){
 *             WearableListConfig.WearableListResult result
 *                    = new WearableListConfig.WearableListResult(data,requestCode);
 *             if (result.isHandled()) {
 *                 Log.d(TAG, String.format("Position: %d, Value: %s",
 *                         result.getSelectedIndex(), result.getSelectedValue()));
 *             } else {
 *                 Log.d(TAG, "Another activity is returning a result here ...");
 *             }
 *          }
 *      }
 * </pre>
 *
 * @see WearableListConfig
 */
public class WclWearableListViewActivity extends AbstractListViewActivityInternal {

    @Override
    public WearableListConfig getConfigurationInternal() {
        return null;
    }

    @Override
    void onItemClicked(int position, String value, int requestCode) {
        Intent intent = new Intent();
        intent.putExtra(Constants.KEY_LIST_RESPONSE_POSITION, position);
        intent.putExtra(Constants.KEY_LIST_RESPONSE_VALUE, value);
        intent.putExtra(Constants.KEY_LIST_RESPONSE_HANDLED, true);
        intent.putExtra(Constants.KEY_LIST_REQUEST_CODE, requestCode);
        setResult(RESULT_OK, intent);
        finish();
        overridePendingTransition(0, android.R.anim.slide_out_right);
    }
}
