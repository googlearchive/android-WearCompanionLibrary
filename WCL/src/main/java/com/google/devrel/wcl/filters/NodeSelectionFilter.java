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

import java.util.Set;

/**
 * An interface that clients can use to provide a mechanism for filtering a set of nodes.
 * Typically is useful when we want to narrow down the list of nodes returned by the
 * {@link com.google.android.gms.wearable.CapabilityApi} methods.
 */
public interface NodeSelectionFilter {

    /**
     * Given a set of {@link Node}s, this method returns a subset based on the desired criteria. If
     * there are no such nodes, it will return an empty set.
     *
     * @param nodes Set of input nodes, cannot be (@code null}
     */
    Set<Node> filterNodes(Set<Node> nodes);

    /**
     * A textual description of the filter, mostly used for logging purposes.
     */
    String describe();
}
