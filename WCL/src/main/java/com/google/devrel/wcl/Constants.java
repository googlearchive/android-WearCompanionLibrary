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

/**
 * A collection of constants that are used across this library.
 */
public class Constants {

    // Paths for sending file/data transfer via ChannelApi
    public static final String PATH_FILE_TRANSFER_TYPE_FILE
            = "/com.google.devrel.wcl/transfer/file/";
    public static final String PATH_FILE_TRANSFER_TYPE_STREAM
            = "/com.google.devrel.wcl/transfer/stream/";

    // Path to use for launching app
    public static final String PATH_LAUNCH_APP = "/com.google.devrel.wcl/launch-app";

    // Request and Response paths for making http calls
    public static final String PATH_HTTP_REQUEST = "/com.google.devrel.wcl/PATH_HTTP_REQUEST";
    public static final String PATH_HTTP_RESPONSE = "/com.google.devrel.wcl/PATH_HTTP_RESPONSE";

    // used in passing an array of strings to the wearable list activity
    public static final String KEY_LIST_REQUEST_CODE
            = "com.google.devrel.wcl.widgets.KEY_LIST_REQUEST_CODE";
    public static final String KEY_LIST_RESPONSE_POSITION
            = "com.google.devrel.wcl.widgets.KEY_LIST_RESPONSE_POSITION";
    public static final String KEY_LIST_RESPONSE_VALUE
            = "com.google.devrel.wcl.widgets.KEY_LIST_RESPONSE_VALUE";
    public static final String KEY_LIST_RESPONSE_HANDLED
            = "com.google.devrel.wcl.widgets.KEY_LIST_RESPONSE_HANDLED";
    public static final String KEY_LIST_CONFIG = "com.google.devrel.wcl.widgets.KEY_LIST_CONFIG";

    public static final String KEY_TIMESTAMP = "com.google.devrel.wcl.KEY_TIMESTAMP";

}
