/*
 * Copyright 2019 JBoss Inc
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
 * limitations under the License.
 */
package io.apicurio.test.integration.arquillian.testprocessors;

import io.apicurio.hub.core.editing.sessionbeans.BaseOperation;
import io.apicurio.hub.core.editing.sessionbeans.JoinLeaveOperation;
import io.apicurio.hub.core.util.JsonUtil;
import org.junit.Assert;

/**
 * Test a join operation
 *
 * @see JoinLeaveOperation
 * @author Marc Savy {@literal <marc@rhymewithgravy.com>}
 */
public class TestJoinOperationProcessor implements ITestOperationProcessor {
    @Override
    public void assertEquals(BaseOperation base, String actualRaw) {
        JoinLeaveOperation expected = (JoinLeaveOperation) base;
        JoinLeaveOperation actual = JsonUtil.fromJson(actualRaw, unmarshallKlazz());

        Assert.assertEquals(expected.getUser(), actual.getUser());
        //Assert.assertEquals(expected.getId(), actual.getId());
    }

    @Override
    public String getOperationName() {
        return "join";
    }

    @Override
    public Class<JoinLeaveOperation> unmarshallKlazz() {
        return JoinLeaveOperation.class;
    }
}