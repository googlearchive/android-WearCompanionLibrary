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

import android.support.annotation.NonNull;

import com.google.devrel.wcl.Utils;

/**
 * An abstract activity that embeds a {@link android.support.wearable.view.WearableListView}.
 * Clients can extend this class and implement its two abstract methods to build a complete
 *{@link android.support.wearable.view.WearableListView} in their client application that
 * is backed by a {@link SelectableWearableListAdapter} adapter; all the details of setting up the
 * adapter and wiring that up to the embedded {@link android.support.wearable.view.WearableListView}
 * are handled by the library.
 *
 * <p>Here is an example of how this can be done:
 * <pre>
 *    public class MyListActivity extends AbstractWearableListViewActivity {
 *
 *        public void onItemClicked(int position, String value) {
 *            Log.d(TAG, "Item clicked: position=" + position + ", value=" + value);
 *            // use the selected value ...
 *        }
 *
 *        public WearableListConfig getConfiguration() {
 *            String[] data = new String[] {"Row 1", "Row 2", "Row 3"};
 *            int[] icons = new int[] { R.drawable.icon1, R.drawable.icon2, R.drawable.icon3};
 *            WearableListConfig listConfig = new WearableListConfig.Builder(data)
 *                .setIcons(icons)
 *                .setCheckable(true)
 *                .build();
 *            return listConfig;
 *        }
 *    }
 * </pre>
 */
public abstract class AbstractWearableListViewActivity extends AbstractListViewActivityInternal {

    @Override
    void onItemClicked(int position, String value, int requestCode) {
        onItemClicked(position, value);
    }

    @Override
    WearableListConfig getConfigurationInternal() {
        return Utils.assertNotNull(getConfiguration(), "getConfiguration()");
    }

    /**
     * Called when a row from the list is selected.
     *
     * @param position The 0-based position of the selected row in the list
     * @param value The string value of the selection
     */
    public abstract void onItemClicked(int position, String value);

    /**
     * Returns the {@link WearableListConfig} that should be used in setting up this list view.
     * Here is an example:
     * <pre>
     *     public WearableListConfig getConfiguration() {
     *         String[] data = new String[] {"Row 1", "Row 2", "Row 3"};
     *         int[] icons = new int[] { R.drawable_row1, R.drawable.row2, R.drawable.row3};
     *         WearableListConfig listConfig = new WearableListConfig.Builder(data)
     *             .setIcons(icons)
     *             .setCheckable(true)
     *             .build();
     *         return listConfig;
     *     }
     * </pre>
     */
    @NonNull
    public abstract WearableListConfig getConfiguration();
}
