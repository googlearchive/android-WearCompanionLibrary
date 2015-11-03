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

package com.google.devrel.wcl.filters;

import android.support.annotation.Nullable;

import com.google.android.gms.wearable.Node;

import java.util.HashSet;
import java.util.Set;

/**
 * An implementation of {@link NodeSelectionFilter} which allows narrowing down a list of nodes to
 * at most one node. Clients can use this filter through a pattern such as:
 * <pre>
 *     new SingleNodeFilter(aNodeSelectionFilter).filterNodes(nodes)
 * </pre>
 * If "aNodeSelectionFilter" is non-null, then the above pattern first applies aNodeSelectionFilter
 * to the set of {@code nodes} and then a single node from the result is picked and returned. if
 * "aNodeSelectionFilter" is {@code null}, a single node from the original set of {@code nodes} will
 * be selected.
 */
public class SingleNodeFilter implements NodeSelectionFilter {

    private final NodeSelectionFilter mDelegate;

    /**
     * A node filter which selects at most one node from
     * the set nodes passed to it.
     *
     * @param delegate a filter to apply before picking the node,
     *         or {@code null} if all nodes should be considered.
     */
    public SingleNodeFilter(@Nullable NodeSelectionFilter delegate) {
        mDelegate = delegate;
    }

    /**
     * A node filter which selects at most one node from
     * the set nodes passed to it.
     */
    public SingleNodeFilter() {
        this(null);
    }

    @Override
    public Set<Node> filterNodes(Set<Node> nodes) {
        if (mDelegate != null) {
            nodes = mDelegate.filterNodes(nodes);
        }
        if (nodes.isEmpty()) {
            return nodes;
        }
        Set<Node> result = new HashSet<>();
        result.add(nodes.iterator().next());
        return result;
    }

    @Override
    public String describe() {
        return "SingleNodeFilter: Arbitrarily selects a single node from a set of nodes";
    }
}
