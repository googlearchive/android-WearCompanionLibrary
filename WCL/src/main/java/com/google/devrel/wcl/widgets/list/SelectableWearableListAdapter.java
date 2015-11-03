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


import android.annotation.SuppressLint;
import android.content.Context;
import android.support.wearable.view.CircledImageView;
import android.support.wearable.view.WearableListView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.devrel.wcl.R;
import com.google.devrel.wcl.Utils;

/**
 * A simple {@link WearableListView.Adapter} that accepts an array of strings as its data set.
 * Developers can instantiate this adapter and pass a string array to the constructor. The default
 * behavior is to use a filled-in circle for each row. The off-centered rows will be using a faded
 * color for both the text and the circle while the centered row will have a larger circle to stand
 * out. Developers can optionally add an icon to the circles.
 *
 * <p>A number of resources can be set to change the look and feel of each row; consult the resource
 * file {@code wcl_list.xml} for details (to customize these values, copy this resource to your
 * client application and change the values as needed).
 *
 * <p>To be notified when a row is clicked on, implement
 * {@link WearableListView.ClickListener#onClick(WearableListView.ViewHolder)}:
 * <pre>
 *   public void onClick(WearableListView.ViewHolder v) {
 *        Integer position = (Integer) v.itemView.getTag();
 *        // use this data to complete some action ...
 *    }
 * </pre>
 *
 * It is also possible to show the "checked" state of an item. To enable this, call
 * {@code setCheckable(true)} (the default value is {@code false}).
 *
 * <p>The same method can also be used to make the rows dynamically selectable; you would
 * need to add the following snippet to the
 * {@link WearableListView.ClickListener#onClick(WearableListView.ViewHolder)}:
 * <pre>
 *        public void onClick(WearableListView.ViewHolder v) {
 *            Integer tag = (Integer) v.itemView.getTag();
 *            adapter.setChecked(tag, WearableListConfig.NO_SELECTION);
 *            adapter.notifyDataSetChanged();
 *            // use this selection to complete some action ...
 *        }
 * </pre>
 *
 * @see WclWearableListViewActivity
 *
 */
public class SelectableWearableListAdapter extends WearableListView.Adapter {

    private int mCheckedPosition = WearableListConfig.NO_SELECTION;
    private static final int DEFAULT_CHECKED_ICON_RES = R.drawable.wcl_list_default_checked_icon;
    private int mCheckedIconResourceId;
    private final String[] mDataSet;
    private final LayoutInflater mInflater;
    private final int mIconResourceId;
    private final int[] mIconResourceIds;
    private boolean mCheckable;

    public SelectableWearableListAdapter(Context context, String[] dataSet, int centerIconResId) {
        mInflater = LayoutInflater.from(context);
        mDataSet = Utils.assertNotNull(dataSet, "dataSet");
        mIconResourceId = centerIconResId;
        mIconResourceIds = null;
    }

    public SelectableWearableListAdapter(Context context, String[] dataSet,
            int[] centerIconResIds) {
        mInflater = LayoutInflater.from(context);
        mDataSet = Utils.assertNotNull(dataSet, "dataSet");
        mIconResourceIds = centerIconResIds;
        mIconResourceId = WearableListConfig.NO_SELECTION;
    }

    private static class ItemViewHolder extends WearableListView.ViewHolder {
        private TextView textView;
        protected CircledImageView circle;
        public ItemViewHolder(View itemView) {
            super(itemView);
            textView = (TextView) itemView.findViewById(R.id.text);
            circle = (CircledImageView) itemView.findViewById(R.id.circle);
        }
    }

    @SuppressLint("InflateParams")
    @Override
    public WearableListView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ItemViewHolder(mInflater.inflate(R.layout.wcl_list_item, null));
    }

    /**
     * Sets the state of the row to "checked" at the index {@code position} and uses the icon
     * {@code checkedResourceIcon} to visually show that state. In order for this method to be
     * take effect, you also need to make the list "checkable" by calling
     * {@code setCheckable(true)}. If {@code WearableListConfig.NO_SELECTION} is passed for the
     * icon, the default resource identified in the {@code R.drawable.wcl_list_default_checked_icon}
     * alias will be used. For {@code checkedPosition < 0} or {@code checkedPosition > n - 1} where
     * {@code n} is the size of the data array, calling this method will not do anything.
     */
    public void setChecked(int checkedPosition, int checkedResourceId) {
        mCheckedPosition = checkedPosition;
        mCheckedIconResourceId = checkedResourceId;
        if (mCheckedIconResourceId == WearableListConfig.NO_SELECTION) {
            mCheckedIconResourceId = DEFAULT_CHECKED_ICON_RES;
        }
    }

    public void setCheckable(boolean checkable) {
        mCheckable = checkable;
    }

    @Override
    public void onBindViewHolder(WearableListView.ViewHolder holder, int position) {
        ItemViewHolder itemHolder = (ItemViewHolder) holder;
        TextView view = itemHolder.textView;
        view.setText(mDataSet[position]);
        if (mIconResourceId != WearableListConfig.NO_SELECTION) {
            itemHolder.circle.setImageResource(mIconResourceId);
        } else if (mIconResourceIds != null) {
            itemHolder.circle.setImageResource(mIconResourceIds[position]);
        } else {
            itemHolder.circle.setImageResource(0);
        }
        holder.itemView.setTag(position);

        if (mCheckable && position == mCheckedPosition) {
            itemHolder.circle.setImageResource(mCheckedIconResourceId);
        }
    }

    @Override
    public int getItemCount() {
        return mDataSet.length;
    }
}
