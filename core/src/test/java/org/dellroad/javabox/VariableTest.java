
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.Test;

public class VariableTest extends TestSupport {

    @Test
    public void testNoInit() throws Exception {
        Config config = Config.builder().build();
        try (JavaBox box = new JavaBox(config)) {
            box.setVariable("foo", 123);
            throw new AssertionError("expected exception");
        } catch (IllegalStateException e) {
            this.log.debug("got expected {}", e.toString());
        }
    }

    @Test
    public void testSetAndGet() throws Exception {
        Config config = Config.builder().build();
        try (JavaBox box = new JavaBox(config)) {
            box.initialize();

            box.setVariable("foo", 123);
            Object value = box.getVariable("foo");
            Assert.assertEquals(value, 123);

            ScriptResult result = box.execute("foo + 1");
            Assert.assertEquals(result.snippetOutcomes().get(0).returnValue(), 124);

            box.setVariable("bar", "byte", (byte)37);
            value = box.getVariable("bar");
            Assert.assertEquals(value, (byte)37);

            result = box.execute("bar + 1");
            Assert.assertEquals(result.snippetOutcomes().get(0).returnValue(), 38);
        }
    }
}
