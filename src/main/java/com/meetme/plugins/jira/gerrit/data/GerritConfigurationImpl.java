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

import com.atlassian.jira.issue.Issue;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.sonymobile.tools.gerrit.gerritevents.GerritDefaultValues;

import java.io.File;
import java.net.URI;
import java.util.List;

import static org.apache.commons.collections.CollectionUtils.isEmpty;

/**
 * {@link GerritConfiguration} implementation that uses {@link PluginSettings} to store
 * configuration data.
 *
 * @author Joe Hansche
 */
public class GerritConfigurationImpl implements GerritConfiguration {
    private static final String PLUGIN_STORAGE_KEY = "com.meetme.plugins.jira.gerrit.data";
    private final PluginSettings settings;

    public GerritConfigurationImpl(PluginSettingsFactory pluginSettingsFactory) {
        this.settings = pluginSettingsFactory.createSettingsForKey(PLUGIN_STORAGE_KEY);
    }

    @Override
    public String getConnectionType() {
        return (String) settings.get(FIELD_CONNECTION_TYPE);
    }

    @Override
    public void setConnectionType(String connectionType) {
        settings.put(FIELD_CONNECTION_TYPE, connectionType);
    }

    @Override
    public URI getHttpBaseUrl() {
        String uri = (String) settings.get(FIELD_HTTP_BASE_URL);
        return uri == null ? null : URI.create(uri);
    }

    @Override
    public void setHttpBaseUrl(String httpBaseUrl) {
        settings.put(FIELD_HTTP_BASE_URL, httpBaseUrl == null ? null : URI.create(httpBaseUrl).toASCIIString());
    }

    @Override
    public String getHttpPassword() {
        return (String) settings.get(FIELD_HTTP_PASSWORD);
    }

    @Override
    public void setHttpPassword(String httpPassword) {
        settings.put(FIELD_HTTP_PASSWORD, httpPassword);
    }

    @Override
    public String getHttpUsername() {
        return (String) settings.get(FIELD_HTTP_USERNAME);
    }

    @Override
    public void setHttpUsername(String httpUsername) {
        settings.put(FIELD_HTTP_USERNAME, httpUsername);
    }

    @Override
    public String getIssueSearchQuery() {
        String query = (String) settings.get(FIELD_QUERY_ISSUE);
        return query == null ? DEFAULT_QUERY_ISSUE : query;
    }

    @Override
    public void setIssueSearchQuery(String query) {
        settings.put(FIELD_QUERY_ISSUE, query);
    }

    @Override
    public String getProjectSearchQuery() {
        String query = (String) settings.get(FIELD_QUERY_PROJECT);
        return query == null ? DEFAULT_QUERY_PROJECT : query;
    }

    @Override
    public void setProjectSearchQuery(String query) {
        settings.put(FIELD_QUERY_PROJECT, query);
    }

    @Override
    public String getSshHostname() {
        return (String) settings.get(FIELD_SSH_HOSTNAME);
    }

    @Override
    public void setSshHostname(String hostname) {
        settings.put(FIELD_SSH_HOSTNAME, hostname);
    }

    @Override
    public int getSshPort() {
        String port = (String) settings.get(FIELD_SSH_PORT);
        return port == null ? DEFAULT_SSH_PORT : Integer.parseInt(port);
    }

    @Override
    public void setSshPort(int port) {
        settings.put(FIELD_SSH_PORT, Integer.toString(port));
    }

    @Override
    public File getSshPrivateKey() {
        String path = (String) settings.get(FIELD_SSH_PRIVATE_KEY);
        return path == null ? null : new File(path);
    }

    @Override
    public void setSshPrivateKey(File sshPrivateKey) {
        settings.put(FIELD_SSH_PRIVATE_KEY, sshPrivateKey == null ? null : sshPrivateKey.getPath());
    }

    @Override
    public String getSshUsername() {
        return (String) settings.get(FIELD_SSH_USERNAME);
    }

    @Override
    public void setSshUsername(String username) {
        settings.put(FIELD_SSH_USERNAME, username);
    }

    @Override
    public int getConnectionTimeout() {
        String timeout = (String) settings.get(FIELD_SSH_TIMEOUT);
        return timeout == null ? DEFAULT_SSH_TIMEOUT : Integer.parseInt(timeout);
    }

    @Override
    public void setConnectionTimeout(int connectionTimeout) {
        settings.put(FIELD_SSH_TIMEOUT, connectionTimeout < 0 ? GerritDefaultValues.DEFAULT_GERRIT_SSH_CONNECTION_TIMEOUT : Integer.toString(connectionTimeout));
    }

    @Override
    public long getCacheTimeout() {
        String timeout = (String) settings.get(FIELD_CACHE_TIMEOUT);
        return timeout == null ? DEFAULT_CACHE_TIMEOUT : Integer.parseInt(timeout);
    }

    @Override
    public void setCacheTimeout(long cacheTimeout) {
        settings.put(FIELD_CACHE_TIMEOUT, cacheTimeout < 0 ? DEFAULT_CACHE_TIMEOUT : Long.toString(cacheTimeout));
    }


    @Override
    public boolean getShowsEmptyPanel() {
        String shows = (String) settings.get(FIELD_SHOW_EMPTY_PANEL);
        // if not already set, defaults to true
        return shows == null || "true".equals(shows);
    }

    @Override
    public void setShowEmptyPanel(boolean show) {
        settings.put(FIELD_SHOW_EMPTY_PANEL, String.valueOf(show));
    }

    @Override
    public boolean isSshValid() {
        return !Strings.isNullOrEmpty(getSshHostname())
                && !Strings.isNullOrEmpty(getSshUsername())
                && getSshPrivateKey() != null
                && getSshPrivateKey().exists();
    }

    @Override
    public boolean isHttpValid() {
        boolean username = Strings.isNullOrEmpty(getHttpUsername());
        boolean password = Strings.isNullOrEmpty(getHttpPassword());
        return (username && password) || (!username && !password);
    }

    @Override
    public boolean isGerritProject(final Issue issue) {
        return issue.getProjectId() != null && !isEmpty(getIdsOfKnownGerritProjects()) && getIdsOfKnownGerritProjects().contains(issue.getProjectId().toString());
    }

    @Override
    public List<String> getIdsOfKnownGerritProjects() {
        List<String> idsOfKnownGerritProjects = (List) settings.get(FIELD_KNOWN_GERRIT_PROJECTS);
        return idsOfKnownGerritProjects != null ? idsOfKnownGerritProjects : Lists.newArrayList();
    }

    @Override
    public void setIdsOfKnownGerritProjects(final List<String> idsOfSelectedGerritProjects) {
        settings.put(FIELD_KNOWN_GERRIT_PROJECTS, idsOfSelectedGerritProjects);
    }

    @Override
    public boolean getUseGerritProjectWhitelist() {
        String useGerritProjectWhitelist = (String) settings.get(FIELD_USE_GERRIT_PROJECT_WHITELIST);
        // Defaults to the behavior without whitelist:
        if (useGerritProjectWhitelist == null || useGerritProjectWhitelist.isEmpty()){
            return false;
        }
        return "true".equals(useGerritProjectWhitelist);
    }

    @Override
    public void setUseGerritProjectWhitelist(boolean useGerritProjectWhitelist) {
        settings.put(FIELD_USE_GERRIT_PROJECT_WHITELIST, String.valueOf(useGerritProjectWhitelist));
    }

    @Override
    public String toString() {
        return String.format("GerritConfigurationImpl[ssh://{0}@{1}:*****/, {3}; http://{4}:*****@{6}/]", getSshUsername(), getSshHostname(),
                getSshPort(), getSshPrivateKey(), getHttpUsername(), getHttpPassword(), getHttpBaseUrl());
    }
}
