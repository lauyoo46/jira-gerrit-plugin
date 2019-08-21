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
package com.meetme.plugins.jira.gerrit.data;

import com.atlassian.jira.user.preferences.ExtendedPreferences;
import com.meetme.plugins.jira.gerrit.data.dto.GerritChange;

import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.meetme.plugins.jira.gerrit.tabpanel.GerritEventKeys;
import com.sonymobile.tools.gerrit.gerritevents.*;
import com.sonymobile.tools.gerrit.gerritevents.http.HttpAuthentication;
import com.sonymobile.tools.gerrit.gerritevents.ssh.Authentication;
import com.sonymobile.tools.gerrit.gerritevents.ssh.SshException;

import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IssueReviewsImpl implements IssueReviewsManager {
    private static final Logger log = LoggerFactory.getLogger(IssueReviewsImpl.class);
    private final Map<String, List<GerritChange>> lruCache;

    private GerritConfiguration configuration;

    private IssueManager jiraIssueManager;

    private GerritQueryHandlerHttp queryHandlerHttp;

    private GerritQueryHandlerWithPersistedConnection queryHandler;

    private QueryHandlerConfig queryHandlerConfig;

    public IssueReviewsImpl(GerritConfiguration configuration, IssueManager jiraIssueManager) {
        this.configuration = configuration;
        this.jiraIssueManager = jiraIssueManager;
        this.lruCache = IssueReviewsCache.getCache();
    }

    @Override
    public Set<String> getIssueKeys(Issue issue) {
        return jiraIssueManager.getAllIssueKeys(issue.getId());
    }

    @Override
    public List<GerritChange> getReviewsForIssue(Issue issue) throws GerritQueryException {
        List<GerritChange> gerritChanges = new ArrayList<>();

        Set<String> allIssueKeys = getIssueKeys(issue);
        for (String key : allIssueKeys) {
            List<GerritChange> changes;

            if (lruCache.containsKey(key)) {
                log.debug("Getting issues from cache");
                changes = lruCache.get(key);
            } else {
                log.debug("Getting issues from Gerrit");
                changes = getReviewsFromGerrit(String.format(configuration.getIssueSearchQuery(), key));
                lruCache.put(key, changes);
            }

            gerritChanges.addAll(changes);
        }

        return gerritChanges;
    }

    protected List<GerritChange> getReviewsFromGerrit(String searchQuery) throws GerritQueryException {
        List<GerritChange> changes;

        String connectionType = configuration.getConnectionType();
        List<JSONObject> reviews;

        if(connectionType.equals(GerritEventKeys.CONNECTION_TYPE_SSH)) {

            if (!configuration.isSshValid()) {
                throw new GerritConfiguration.NotConfiguredException("Not configured for SSH access");
            }

            try {
                GerritQueryHandler querySsh = getQueryHandlerSsh(configuration);
                reviews = querySsh.queryJava(searchQuery, false, true, false);
            } catch (SshException e) {
                throw new GerritQueryException("An ssh error occurred while querying for reviews.", e);
            } catch (IOException e) {
                throw new GerritQueryException("An error occurred while querying for reviews.", e);
            }
        }
        else {

            if(!configuration.isHttpValid()) {
                throw new GerritConfiguration.NotConfiguredException("Not configured for HTTP access");
            }

            try {
                GerritQueryHandlerHttp queryHttp = getQueryHandlerHttp(configuration);
                reviews = queryHttp.queryJava(searchQuery, true, true, true);
            } catch(IOException e) {
                throw new GerritQueryException("An error occurred while querying for reviews.", e);
            }
        }

        changes = new ArrayList<>(reviews.size());

        for (JSONObject obj : reviews) {
            if (obj.has("type") && "stats".equalsIgnoreCase(obj.getString("type"))) {
                // The final JSON object in the query results is just a set of statistics
                if (log.isDebugEnabled()) {
                    log.trace("Results from QUERY: " + obj.optString("rowCount", "(unknown)") + " rows; runtime: "
                            + obj.optString("runTimeMilliseconds", "(unknown)") + " ms");
                }
                continue;
            }

            if(connectionType.equals(GerritEventKeys.CONNECTION_TYPE_HTTP)) {
                obj.element("url", configuration.getHttpBaseUrl());
            }
            changes.add(new GerritChange(obj, connectionType));
        }

        Collections.sort(changes);
        return changes;
    }

    private GerritQueryHandlerHttp getQueryHandlerHttp(GerritConfiguration configuration) {
        HttpAuthentication httpAuth = new HttpAuthentication(configuration.getHttpUsername(), configuration.getHttpPassword());
        this.queryHandlerHttp = new GerritQueryHandlerHttp(configuration.getHttpBaseUrl().toString(), httpAuth);
        return queryHandlerHttp;
    }

    private GerritQueryHandler getQueryHandlerSsh(GerritConfiguration configuration) {
        QueryHandlerConfig config = new QueryHandlerConfig(configuration.getSshPrivateKey(),
                configuration.getSshUsername(), configuration.getSshHostname(),
                configuration.getSshPort(), configuration.getConnectionTimeout());
        boolean configChanged = !config.equals(queryHandlerConfig);

        if (queryHandler == null || configChanged) {
            if (queryHandler != null) {
                log.debug("QueryHandler configuration has changed, creating a fresh connection.");
                queryHandler.disconnect();
            }
            Authentication auth = new Authentication(configuration.getSshPrivateKey(), configuration.getSshUsername());
            queryHandler = new GerritQueryHandlerWithPersistedConnection(configuration.getSshHostname(), configuration.getSshPort(),
                    null, auth, configuration.getConnectionTimeout());
            queryHandlerConfig = config;
            log.debug("QueryHandler with a fresh SSH connection was created.");
        }
        return queryHandler;
    }

    @Override
    public boolean doApprovals(Issue issue, List<GerritChange> changes, String args, ExtendedPreferences prefs) throws IOException {
        Set<String> issueKeys = getIssueKeys(issue);

        boolean result = true;
        for (String issueKey : issueKeys) {
            GerritCommand command = new GerritCommand(configuration,prefs);

            boolean commandResult = command.doReviews(changes, args);
            result &= commandResult;

            if (log.isDebugEnabled()) {
                log.trace("doApprovals " + issueKey + ", " + changes + ", " + args + "; result=" + commandResult);
            }

            // Something probably changed!
            lruCache.remove(issueKey);
        }

        return result;
    }

    private static class QueryHandlerConfig {
        File currentSshPrivateKey;
        String currentSshUsername;
        String currentSshHostname;
        int currentSshPort;
        int currentConnectionTimeout;

        QueryHandlerConfig(File currentSshPrivateKey, String currentSshUsername, String currentSshHostname,
                           int currentSshPort, int currentConnectionTimeout) {
            this.currentSshPrivateKey = currentSshPrivateKey;
            this.currentSshUsername = currentSshUsername;
            this.currentSshHostname = currentSshHostname;
            this.currentSshPort = currentSshPort;
            this.currentConnectionTimeout = currentConnectionTimeout;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            QueryHandlerConfig that = (QueryHandlerConfig) o;

            if (currentSshPort != that.currentSshPort) return false;
            if (currentConnectionTimeout != that.currentConnectionTimeout) return false;
            if (!currentSshPrivateKey.equals(that.currentSshPrivateKey)) return false;
            if (!currentSshUsername.equals(that.currentSshUsername)) return false;
            return currentSshHostname.equals(that.currentSshHostname);
        }

        // we don't care about hashcode
        @Override
        public int hashCode() {
            return 0;
        }
    }
}
