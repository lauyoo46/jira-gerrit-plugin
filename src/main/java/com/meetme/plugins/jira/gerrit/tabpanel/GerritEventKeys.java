/*
 * Copyright 2012 MeetMe, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.meetme.plugins.jira.gerrit.tabpanel;

/**
 * Extension of {@link com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys}
 * to provide additional missing keys.
 * 
 * @author Joe Hansche
 */
public interface GerritEventKeys {
    public static final String APPROVALS = "approvals";
    public static final String BY = "by";
    public static final String CURRENT_PATCH_SET = "currentPatchSet";
    public static final String LAST_UPDATED = "lastUpdated";
    public static final String OPEN = "open";
    public static final String STATUS = "status";
    public static final String CONNECTION_TYPE_SSH = "ssh";
    public static final String CONNECTION_TYPE_HTTP = "http";
}
