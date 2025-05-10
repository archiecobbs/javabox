
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import org.dellroad.javabox.SnippetOutcome.SuccessfulWithValue;
import org.dellroad.javabox.SnippetOutcome.Suspended;
import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SuspendResumeTest extends TestSupport {

    @Test
    public void testSuspendResume() throws Exception {
        Config config = Config.builder().build();
        try (JavaBox box = new JavaBox(config)) {
            box.initialize();

            // Start script that suspends itself
            ScriptResult result = box.execute(
                """
                var x = 123;
                org.dellroad.javabox.JavaBox.suspend("abc");
                """);

            // Check result - suspend parameter should be "abc"
            Assert.assertEquals(result.snippetOutcomes().size(), 2);
            SnippetOutcome outcome = result.lastOutcome().get();
            Assert.assertTrue(outcome instanceof Suspended);
            final Suspended suspended = (Suspended)outcome;
            Assert.assertEquals(suspended.parameter(), "abc");

            // Now resume it
            result = box.resume("def");

            // Check result - should have returned "def"
            Assert.assertEquals(result.snippetOutcomes().size(), 2);
            outcome = result.lastOutcome().get();
            Assert.assertTrue(outcome instanceof SuccessfulWithValue);
            final SuccessfulWithValue successful = (SuccessfulWithValue)outcome;
            Assert.assertEquals(successful.returnValue(), "def");
        }
    }

    @Test
    public void testSuspendThenClose() throws Exception {
        Config config = Config.builder().build();
        try (JavaBox box = new JavaBox(config)) {
            box.initialize();
            ScriptResult result = box.execute("org.dellroad.javabox.JavaBox.suspend(123);");
        }
    }
}
