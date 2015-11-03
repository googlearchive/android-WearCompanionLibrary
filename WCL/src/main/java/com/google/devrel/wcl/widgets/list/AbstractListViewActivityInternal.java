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

import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.WatchViewStub;
import android.support.wearable.view.WearableListView;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.devrel.wcl.Constants;
import com.google.devrel.wcl.R;
import com.google.devrel.wcl.Utils;

/**
 * An abstract class that provides most of the logic and components to present a
 * {@link WearableListView}. The idea is to provide two different ways for a client to show a
 * {@link WearableListView} in the client code: either allow the client to call a method to show
 * a dialog-type activity containing a {@link WearableListView}, or allow the client to create their
 * own activity that embeds a {@link WearableListView}. The main ingredients to support both of
 * these use cases are contained here.
 *
 * @see WclWearableListViewActivity
 * @see AbstractWearableListViewActivity
 */
abstract class AbstractListViewActivityInternal extends WearableActivity {
    private static final String TAG = "AbstractListViewAct";
    private WearableListView mWearableListView;
    private String[] mDataSet;
    private SelectableWearableListAdapter mAdapter;
    private int mRequestCode;
    private WearableListConfig mConfig;
    private WearableListView.OnScrollListener mScrollListener;

    /**
     * Called when user selects an item from the list.
     *
     * @param position The position (based 0) of the selected row
     * @param value The string value of the selection
     * @param requestCode The request code that was used to start this activity.
     */
    abstract void onItemClicked(int position, String value, int requestCode);

    /**
     * Returns the {@link WearableListConfig} configuration used in this ListView.
     */
    abstract WearableListConfig getConfigurationInternal();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle extras = getIntent().getExtras();
        final WearableListView.ClickListener wearableListViewClickListener
                = new WearableListView.ClickListener() {

            @Override
            public void onClick(WearableListView.ViewHolder viewHolder) {
                AbstractListViewActivityInternal.this.onClick(viewHolder);
            }

            @Override
            public void onTopEmptyRegionClick() {
                Utils.LOGD(TAG, "Top was clicked");
            }
        };
        mConfig = getConfigurationInternal();
        if (mConfig == null && extras != null) {
            mConfig = WearableListConfig.fromBundle(extras.getBundle(Constants.KEY_LIST_CONFIG));
        }
        if (mConfig == null) {
            Log.e(TAG, "No configuration was specified");
            finish();
            return;
        }
        mRequestCode = mConfig.getRequestCode();
        mDataSet = mConfig.getDataArray();
        setContentView(R.layout.wcl_wearable_list_activity);
        if (mConfig.isAmbient()) {
            setAmbientEnabled();
        }
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mWearableListView = (WearableListView) stub.findViewById(R.id.wearable_list);
                if (mConfig.getHeader() != null) {
                    final TextView header = (TextView) stub.findViewById(R.id.header);
                    header.setText(mConfig.getHeader());
                    header.setVisibility(View.VISIBLE);
                    mScrollListener = new WearableListView.OnScrollListener() {
                        @Override
                        public void onScroll(int scroll) {
                            // scroll the header as we scroll the list
                            header.setY(header.getY() - scroll);
                        }

                        @Override
                        public void onAbsoluteScrollChange(int scroll) {
                            // no-op
                        }

                        @Override
                        public void onScrollStateChanged(int scroll) {
                            // no-op
                        }

                        @Override
                        public void onCentralPositionChanged(int scroll) {
                            // no-op
                        }
                    };
                    mWearableListView.addOnScrollListener(mScrollListener);
                }
                // Assign an adapter to the list
                if (mConfig.getIconResIds() != null) {
                    mAdapter = new SelectableWearableListAdapter(
                            AbstractListViewActivityInternal.this, mDataSet,
                            mConfig.getIconResIds());
                } else {
                    mAdapter = new SelectableWearableListAdapter(
                            AbstractListViewActivityInternal.this, mDataSet,
                            mConfig.getIconResId());
                }
                if (mConfig.isCheckable()) {
                    mAdapter.setCheckable(true);
                    if (mConfig.getCheckedIndex() != WearableListConfig.NO_SELECTION) {
                        mAdapter.setChecked(mConfig.getCheckedIndex(),
                                mConfig.getCheckedIconResId());
                    }
                }

                mWearableListView.setAdapter(mAdapter);
                // Set a click listener
                mWearableListView.setClickListener(wearableListViewClickListener);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mScrollListener != null) {
            mWearableListView.removeOnScrollListener(mScrollListener);
        }

    }

    private void onClick(WearableListView.ViewHolder v) {
        Integer tag = (Integer) v.itemView.getTag();
        mAdapter.setChecked(tag, mConfig.getCheckedIconResId());
        mAdapter.notifyDataSetChanged();
        onItemClicked(tag, mDataSet[tag], mRequestCode);
    }
}
