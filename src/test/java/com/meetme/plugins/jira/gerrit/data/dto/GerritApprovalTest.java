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

import static org.junit.Assert.*;
import static org.mockito.MockitoAnnotations.initMocks;
import net.sf.json.JSONObject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import com.atlassian.jira.user.ApplicationUser;


public class GerritApprovalTest {

    private final JSONObject BASE_TEST = new JSONObject();

    private static final String EXPECTED_NAME = "Name";
    private static final String EXPECTED_USERNAME = "Username";
    private static final String EXPECTED_EMAIL = "user@email.local";
    private static final String EXPECTED_TYPE = "Code-Review";
    private static final String EXPECTED_VALUE = "1";
    private static final String GREATER_VALUE = "2";

    @Mock
    private ApplicationUser EXPECTED_USER;

    @Before
    public void setUp() throws Exception {
        initMocks(this);

        BASE_TEST.element("type", EXPECTED_TYPE).element("value", EXPECTED_VALUE);
    }

    @After
    public void tearDown() throws Exception {
        BASE_TEST.clear();
    }

    private static void setUpAccountJson(JSONObject obj) {
        JSONObject by = new JSONObject();
        by.element("name", EXPECTED_NAME).element("email", EXPECTED_EMAIL).element("username", EXPECTED_USERNAME);
        obj.element("by", by);
    }

    private static void assertFull(GerritApproval obj) {
        assertEquals(EXPECTED_NAME, obj.getBy().getName());
        assertEquals(EXPECTED_EMAIL, obj.getBy().getEmail());
        assertEquals(EXPECTED_USERNAME, obj.getBy().getUsername());
        assertEquals(EXPECTED_TYPE, obj.getType());
        assertEquals(EXPECTED_VALUE, obj.getValue());
        assertEquals(1, obj.getValueAsInt());
    }

    @Test
    public void testEmpty() {
        GerritApproval obj = new GerritApproval(new JSONObject());

        assertNull(obj.getType());
        assertNull(obj.getValue());

        assertNull(obj.getBy());
        assertEquals(0, obj.getValueAsInt());
    }

    @Test
    public void testBaseOnly() {
        GerritApproval obj = new GerritApproval(BASE_TEST);

        assertNull(obj.getBy());

        assertEquals(EXPECTED_TYPE, obj.getType());
        assertEquals(EXPECTED_VALUE, obj.getValue());
        assertEquals(1, obj.getValueAsInt());
    }

    @Test
    public void testParseByFromJson() {
        setUpAccountJson(BASE_TEST);

        GerritApproval obj = new GerritApproval(BASE_TEST);
        assertFull(obj);
    }

    @Test
    public void testParseFromJson_NoEmail() {
        JSONObject by = new JSONObject();
        by.element("name", EXPECTED_NAME).element("username", EXPECTED_USERNAME);
        BASE_TEST.element("by", by);

        GerritApproval obj = new GerritApproval(BASE_TEST);
        assertNull(obj.getBy().getEmail());
        assertEquals(EXPECTED_NAME, obj.getBy().getName());
        assertEquals(EXPECTED_USERNAME, obj.getBy().getUsername());
    }

    @Test
    public void testParseFromJson_NoName() {
        JSONObject by = new JSONObject();
        by.element("email", EXPECTED_EMAIL).element("username", EXPECTED_USERNAME);
        BASE_TEST.element("by", by);

        GerritApproval obj = new GerritApproval(BASE_TEST);
        assertNull(obj.getBy().getName());
        assertEquals(EXPECTED_EMAIL, obj.getBy().getEmail());
        assertEquals(EXPECTED_USERNAME, obj.getBy().getUsername());
    }

    @Test
    public void testParseFromJson_NoUsername() {
        JSONObject by = new JSONObject();
        by.element("name", EXPECTED_NAME).element("email", EXPECTED_EMAIL);
        BASE_TEST.element("by", by);

        GerritApproval obj = new GerritApproval(BASE_TEST);
        assertNull(obj.getBy().getUsername());
        assertEquals(EXPECTED_NAME, obj.getBy().getName());
        assertEquals(EXPECTED_EMAIL, obj.getBy().getEmail());
    }

    @Test
    public void testSetters() {
        setUpAccountJson(BASE_TEST);
        GerritApproval obj = new GerritApproval(BASE_TEST);

        obj.setType(EXPECTED_TYPE);
        obj.setValue(EXPECTED_VALUE);
        obj.setUser(EXPECTED_USER);

        assertFull(obj);
        assertNotNull(obj.getUser());
        assertEquals(EXPECTED_USER, obj.getUser());
    }

    @Test
    public void testToString() {
        setUpAccountJson(BASE_TEST);
        GerritApproval obj = new GerritApproval(BASE_TEST);

        assertEquals("+1 by Name", obj.toString());
    }

    @Test
    public void testCompareEquals() {
        GerritApproval obj = new GerritApproval(BASE_TEST);
        GerritApproval obj2 = new GerritApproval(BASE_TEST);

        assertEquals(0, obj.compareTo(obj));
        assertEquals(obj, obj);

        assertEquals(0, obj.compareTo(obj2));
        assertEquals(obj, obj2);
    }

    @Test
    public void testCompareNotEquals() {
        GerritApproval obj = new GerritApproval(BASE_TEST);
        GerritApproval obj2 = new GerritApproval(BASE_TEST);
        obj2.setValue(GREATER_VALUE);

        // obj1 < obj2
        assertEquals(-1, obj.compareTo(obj2));
        assertNotEquals(obj, obj2);

        // obj1 > obj2
        assertEquals(1, obj2.compareTo(obj));
        assertNotEquals(obj2, obj);
    }
}
