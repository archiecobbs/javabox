
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import org.dellroad.javabox.SnippetOutcome.SuccessfulWithValue;
import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
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
            Assert.assertTrue(result.snippetOutcomes().get(0) instanceof SuccessfulWithValue s && s.returnValue().equals(124));

            box.setVariable("bar", "byte", (byte)37);
            value = box.getVariable("bar");
            Assert.assertEquals(value, (byte)37);

            result = box.execute("bar + 1");
            Assert.assertTrue(result.snippetOutcomes().get(0) instanceof SuccessfulWithValue s && s.returnValue().equals(38));
        }
    }

    @Test
    public void testUnknown() throws Exception {
        Config config = Config.builder().build();
        try (JavaBox box = new JavaBox(config)) {
            box.initialize();

            try {
                box.getVariable("foo");
                throw new AssertionError();
            } catch (IllegalArgumentException e) {
                // expected
            }
        }
    }

    @Test(dataProvider = "implicitCases")
    public void testImplicitType(Object value) throws Exception {
        Config config = Config.builder().build();
        try (JavaBox box = new JavaBox(config)) {
            box.initialize();

            box.setVariable("value", value);
            Object value2 = box.execute("value").returnValue();
            Assert.assertEquals(value2, value);
        }
    }

    @DataProvider(name = "implicitCases")
    public Object[][] implicitCases() {
        return new Object[][] {

            // Null
            { null },

            // Primitives & wrappers
            { true },
            { false },
            { (Boolean)true },
            { (Boolean)false },
            { (byte)42 },
            { (Byte)(byte)42 },
            { (short)42 },
            { (Short)(short)42 },
            { 'x' },
            { (Character)'x' },
            { 42 },
            { (Integer)42 },
            { 42.0f },
            { (Float)42.0f },
            { 42L },
            { (Long)42L },
            { 42.0 },
            { (Double)42.0 },

            // Arrays
            { new Object[0] },
            { new Object[0] },
            { new boolean[5] },
            { new boolean[5][3] },
            { new byte[5] },
            { new byte[5][3] },
            { new char[5] },
            { new char[5][3] },
            { new short[5] },
            { new short[5][3] },
            { new int[5] },
            { new int[5][3] },
            { new float[5] },
            { new float[5][3] },
            { new long[5] },
            { new long[5][3] },
            { new double[5] },
            { new double[5][3] },
            { new String[][] { { "aa", "ab" }, { "ba", "bb" } } },

            // Others
            { "abc" },
            { new Object() },
            { new java.util.ArrayList<Void>() },
            { new java.util.AbstractMap.SimpleEntry<Void, Void>(null, null) },
        };
    }
}
