
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import java.util.concurrent.atomic.AtomicBoolean;

import org.dellroad.javabox.SnippetOutcome.Interrupted;
import org.dellroad.javabox.SnippetOutcome.Suspended;
import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.Test;

public class InterruptTest extends TestSupport {

    @Test
    public void testInterrupt() throws Exception {
        Config config = Config.builder().build();
        try (JavaBox box = new JavaBox(config)) {
            box.initialize();

            // Wait 1 second then interrupt
            AtomicBoolean interruptResult = new AtomicBoolean();
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // ignore
                }
                interruptResult.set(box.interrupt());
            }).start();

            // Start infinite loop script
            final ScriptResult result = box.execute(
                """
                while (true) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        continue;
                    }
                }
                """);

            // Check result
            Assert.assertTrue(interruptResult.get());
            Assert.assertEquals(result.snippetOutcomes().size(), 1);
            final SnippetOutcome outcome = result.lastAttempted().get();
            Assert.assertTrue(outcome instanceof Interrupted);

            // Check interrupting nothing
            Assert.assertFalse(box.interrupt());
        }
    }

    @Test
    public void testInterruptSuspended() throws Exception {
        Config config = Config.builder().build();
        try (JavaBox box = new JavaBox(config)) {
            box.initialize();

            // Start script that suspends itself
            ScriptResult result = box.execute(
                """
                org.dellroad.javabox.JavaBox.suspend("abc");
                """);

            // Check result - suspend parameter should be "abc"
            Assert.assertEquals(result.snippetOutcomes().size(), 1);
            SnippetOutcome outcome = result.lastAttempted().get();
            Assert.assertTrue(outcome instanceof Suspended);
            Assert.assertEquals(((Suspended)outcome).parameter(), "abc");

            // Interrupt the suspended script
            boolean r = box.interrupt();
            Assert.assertTrue(r);

            // Resume the suspended script
            result = box.resume(null);

            // Check result - should be interrupted
            Assert.assertEquals(result.snippetOutcomes().size(), 1);
            outcome = result.lastAttempted().get();
            Assert.assertTrue(outcome instanceof Interrupted);
        }
    }
}
