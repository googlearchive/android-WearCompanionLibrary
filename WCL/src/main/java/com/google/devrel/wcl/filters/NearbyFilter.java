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

import com.google.android.gms.wearable.Node;
import com.google.devrel.wcl.Utils;

import java.util.HashSet;
import java.util.Set;

/**
 * A simple {@link NodeSelectionFilter} where we select the subset of input nodes that are also
 * nearby.
 */
public final class NearbyFilter implements NodeSelectionFilter {

    @Override
    public Set<Node> filterNodes(Set<Node> nodes) {
        Utils.assertNotNull(nodes, "nodes");
        Set<Node> result = new HashSet<>();
        for(Node node : nodes) {
            if (node.isNearby()) {
                result.add(node);
            }
        }
        return result;
    }

    @Override
    public String describe() {
        return "NearbyFilter:Selects the subset of nearby nodes";
    }
}
