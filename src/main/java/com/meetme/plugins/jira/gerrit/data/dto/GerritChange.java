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
package com.meetme.plugins.jira.gerrit.data.dto;

import com.meetme.plugins.jira.gerrit.tabpanel.GerritEventKeys;

import com.sonymobile.tools.gerrit.gerritevents.GerritJsonEventFactory;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritChangeStatus;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Account;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;
import com.sonymobile.tools.gerrit.gerritevents.dto.rest.Topic;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static com.meetme.plugins.jira.gerrit.tabpanel.GerritEventKeys.LAST_UPDATED;

/**
 * @author Joe Hansche
 */
public class GerritChange extends Change implements Comparable<GerritChange> {

    /**
     * Gerrit review status enumeration, corresponding to the status string in the Gerrit change
     * payload.
     */
    public enum Status
    {
        NEW, SUBMITTED, DRAFT, MERGED, ABANDONED
    }

    private Date lastUpdated;

    private GerritPatchSet patchSet;

    private boolean isOpen;

    private GerritChangeStatus status;

    public GerritChange() {
        super();
    }

    public GerritChange(JSONObject obj) {
        super(obj);
    }

    public GerritChange(JSONObject obj, String connectionType) {

        if(connectionType.equals("ssh")) {
            this.fromJsonSSH(obj, connectionType);
        }
        else {
            this.fromJsonHTTP(obj, connectionType);
        }
    }

    /**
     * Sorts {@link GerritChange}s in order by their Gerrit change number.
     *
     * TODO: To be completely accurate, the changes should impose a dependency-tree ordering (via
     * <tt>--dependencies</tt> option) to GerritQuery! It is possible for an earlier ChangeId to be
     * refactored such that it is then dependent on a <i>later</i> change!
     */
    @Override
    public int compareTo(GerritChange obj) {
        if (this != obj && obj != null) {
            int aNum = Integer.parseInt(this.getNumber());
            int bNum = Integer.parseInt(obj.getNumber());

            if (aNum == bNum) {
                return 0;
            } else {
                return aNum < bNum ? -1 : 1;
            }
        }

        return 0;
    }

    @Override
    public void fromJson(JSONObject json) {
        super.fromJson(json);
        this.fromJsonSSH(json, "ssh"); //todo: cumva trebuie transmis ssh ca si tip de conexiune
    }

    private void fromJsonSSH(JSONObject json, String connectionType) {
        super.fromJson(json);
        this.lastUpdated = new Date(1000 * json.getLong(LAST_UPDATED));

        if (json.containsKey(GerritEventKeys.CURRENT_PATCH_SET)) {
            this.patchSet = new GerritPatchSet(json.getJSONObject(GerritEventKeys.CURRENT_PATCH_SET), connectionType);
        }

        if (json.containsKey(GerritEventKeys.STATUS)) {
            this.setStatus(GerritChangeStatus.valueOf(json.getString(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.STATUS)));
        }

        this.isOpen = json.getBoolean(GerritEventKeys.OPEN);
    }

    private void fromJsonHTTP(JSONObject json, String connectionType) {

        //todo: setteri intr-o metoda
        this.setProject(GerritJsonEventFactory.getString(json, "project"));
        this.setBranch(GerritJsonEventFactory.getString(json, "branch"));
        this.setId(GerritJsonEventFactory.getString(json, "change_id"));
        this.setNumber(GerritJsonEventFactory.getString(json, "_number"));
        this.setSubject(GerritJsonEventFactory.getString(json, "subject"));
        this.setWip(GerritJsonEventFactory.getBoolean(json, "wip", false));
        this.setPrivate(GerritJsonEventFactory.getBoolean(json, "private", false));
        if (json.containsKey("owner")) {
            this.setOwner(new Account(json.getJSONObject("owner")));
        }

        String revision = GerritJsonEventFactory.getString(json, "current_revision");
        JSONObject jsonRevision = json.getJSONObject("revisions").getJSONObject(revision);
        JSONObject jsonCommit = jsonRevision.getJSONObject("commit");
        if(jsonCommit.containsKey("message")) {
            String commitMessage = GerritJsonEventFactory.getString(jsonCommit, "message");
            this.setCommitMessage(commitMessage);
        }

        if (json.containsKey("topic")) {
            String topicName = GerritJsonEventFactory.getString(json, "topic");
            if (StringUtils.isNotEmpty(topicName)) {
                this.setTopicObject(new Topic(topicName));
            }
        }

        JSONObject urlJson = json.getJSONObject("url");
        String scheme = GerritJsonEventFactory.getString(urlJson, "scheme");
        String schemeSpecificPart = GerritJsonEventFactory.getString(urlJson, "schemeSpecificPart");
        String number = GerritJsonEventFactory.getString(json, "_number");
        //todo: variabila field pt "http://" ?
        if(!scheme.contains("http://")) {
            scheme = "http://" + scheme;
        }
        String url = scheme + ":" + schemeSpecificPart + "/" + number;
        this.setUrl(url);

        String dateUpdated = json.getString("updated");
        String dateCreated = json.getString("created");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            this.setCreatedOn(sdf.parse(dateCreated));
            this.lastUpdated = sdf.parse(dateUpdated);
            super.setLastUpdated(lastUpdated);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        convertApprovals(json);
        this.patchSet = new GerritPatchSet(json, connectionType);

        if (json.containsKey(GerritEventKeys.STATUS)) {
            this.setStatus(GerritChangeStatus.valueOf(json.getString(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.STATUS)));
            super.setStatus(this.getStatus());
        }

        String stringStatus = this.getStatus().toString();
        this.isOpen = stringStatus.equals("NEW");

    }

    private void convertApprovals(JSONObject json)
    {
        if (json.containsKey("labels")) {
            boolean existsVerifiedApproval = false;
            boolean existsCodeReviewApproval = false;
            boolean existsValidatedApproval = false;
            boolean existsPriorityApproval = false;

            JSONObject jsonInner = json.getJSONObject("labels");
            JSONArray approvals = new JSONArray();

            if (jsonInner.containsKey("Verified")) {
                existsVerifiedApproval = addVerifiedApproval(approvals, jsonInner);
            }
            if(jsonInner.containsKey("Code-Review")) {
                existsCodeReviewApproval = addCodeReviewApproval(approvals, jsonInner);
            }
            if(jsonInner.containsKey("Validated")) {
                existsValidatedApproval = addValidatedApproval(approvals, jsonInner);
            }
            if(jsonInner.containsKey("Priority")) {
                existsPriorityApproval = addPriorityApproval(approvals, jsonInner);
            }

            if(existsVerifiedApproval || existsCodeReviewApproval || existsValidatedApproval || existsPriorityApproval) {
                json.element("approvals", approvals);
            }
        }
    }

    private boolean addVerifiedApproval(JSONArray approvals, JSONObject jsonInner) {

        JSONObject verifiedApproval = new JSONObject();
        JSONArray verifiedArray = jsonInner.getJSONObject("Verified").getJSONArray("all");
        for (int i = 0; i < verifiedArray.size(); ++i) {
            JSONObject verifiedObject = verifiedArray.getJSONObject(i);
            if (!verifiedObject.getString("username").equals("builderbot")
                    && !verifiedObject.getString("value").equals("0")) {
                String value = verifiedObject.getString("value");
                String type = "Verified";
                String description = "Verified";
                String date = verifiedObject.getString("date");
                String name = verifiedObject.getString("name");
                String username = verifiedObject.getString("username");
                JSONObject verifiedBy = new JSONObject();
                verifiedBy.element("name", name);
                verifiedBy.element("username", username);
                verifiedApproval.element("type", type);
                verifiedApproval.element("description", description);
                verifiedApproval.element("value", value);
                verifiedApproval.element("grantedOn", date);
                verifiedApproval.element("by", verifiedBy);
                approvals.element(verifiedApproval);
                return true;
            }
        }
        return false;
    }

    private boolean addCodeReviewApproval(JSONArray approvals, JSONObject jsonInner) {

        JSONObject codeReviewApproval = new JSONObject();
        JSONArray codeReviewArray = jsonInner.getJSONObject("Code-Review").getJSONArray("all");
        for (int i = 0; i < codeReviewArray.size(); ++i) {
            JSONObject codeReviewObject = codeReviewArray.getJSONObject(i);
            if (!codeReviewObject.getString("username").equals("builderbot")
                    && !codeReviewObject.getString("value").equals("0")) {
                String value = codeReviewObject.getString("value");
                String type = "Code-Review";
                String description = "Code-Review";
                String date = codeReviewObject.getString("date");
                String name = codeReviewObject.getString("name");
                String username = codeReviewObject.getString("username");
                JSONObject codeReviewBy = new JSONObject();
                codeReviewBy.element("name", name);
                codeReviewBy.element("username", username);
                codeReviewApproval.element("type", type);
                codeReviewApproval.element("description", description);
                codeReviewApproval.element("value", value);
                codeReviewApproval.element("grantedOn", date);
                codeReviewApproval.element("by", codeReviewBy);
                approvals.element(codeReviewApproval);
                return true;
            }
        }
        return false;
    }

    private boolean addValidatedApproval(JSONArray approvals, JSONObject jsonInner) {

        JSONObject validatedApproval = new JSONObject();
        JSONArray validatedArray = jsonInner.getJSONObject("Validated").getJSONArray("all");
        for (int i = 0; i < validatedArray.size(); ++i) {
            JSONObject validatedObject = validatedArray.getJSONObject(i);
            if (!validatedObject.getString("username").equals("builderbot")
                    && !validatedObject.getString("value").equals("0")) {
                String value = validatedObject.getString("value");
                String type = "Validated";
                String description = "Validated";
                String date = validatedObject.getString("date");
                String name = validatedObject.getString("name");
                String username = validatedObject.getString("username");
                JSONObject validatedBy = new JSONObject();
                validatedBy.element("name", name);
                validatedBy.element("username", username);
                validatedApproval.element("type", type);
                validatedApproval.element("description", description);
                validatedApproval.element("value", value);
                validatedApproval.element("grantedOn", date);
                validatedApproval.element("by", validatedBy);
                approvals.element(validatedApproval);
                return true;
            }
        }
        return false;
    }

    private boolean addPriorityApproval(JSONArray approvals, JSONObject jsonInner) {

        JSONObject priorityApproval = new JSONObject();
        JSONArray priorityArray = jsonInner.getJSONObject("Priority").getJSONArray("all");
        for (int i = 0; i < priorityArray.size(); ++i) {
            JSONObject priorityObject = priorityArray.getJSONObject(i);
            if (!priorityObject.getString("username").equals("builderbot")
                    && !priorityObject.getString("value").equals("0")) {
                String value = priorityObject.getString("value");
                String type = "Priority";
                String description = "Priority";
                String date = priorityObject.getString("date");
                String name = priorityObject.getString("name");
                String username = priorityObject.getString("username");
                JSONObject priorityBy = new JSONObject();
                priorityBy.element("name", name);
                priorityBy.element("username", username);
                priorityApproval.element("type", type);
                priorityApproval.element("description", description);
                priorityApproval.element("value", value);
                priorityApproval.element("grantedOn", date);
                priorityApproval.element("by", priorityBy);
                approvals.element(priorityApproval);
                return true;
            }
        }
        return false;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    public GerritPatchSet getPatchSet() {
        return patchSet;
    }

    public GerritChangeStatus getStatus() {
        return status;
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public void setOpen(boolean isOpen) {
        this.isOpen = isOpen;
    }

    public void setPatchSet(GerritPatchSet patchSet) {
        this.patchSet = patchSet;
    }

    public void setStatus(GerritChangeStatus status) {
        this.status = status;
    }
}
