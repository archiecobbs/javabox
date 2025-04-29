
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox.control;

import java.time.Duration;

import org.dellroad.javabox.Config;
import org.dellroad.javabox.JavaBox;
import org.dellroad.javabox.ScriptResult;
import org.dellroad.javabox.SnippetOutcome.ExceptionThrown;
import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TimeLimitTest extends TestSupport {

    @Test
    public void testTimeLimit() throws Exception {

        // Set up control
        final Duration timeLimit = Duration.ofSeconds(3);
        final Duration maxDiff = Duration.ofMillis(500);
        Config config = Config.builder()
          .withControl(new TimeLimitControl(timeLimit))
          .build();

        // Execute infinite loop script
        final long elapsedTime;
        final ScriptResult result;
        try (JavaBox box = new JavaBox(config)) {
            box.initialize();
            final long startTime = System.nanoTime();
            this.log.info("expecting script to fail in {}ms...", timeLimit.toMillis());
            result = box.execute("while (true) { Thread.yield(); }");
            elapsedTime = System.nanoTime() - startTime;
        }

        // Check result
        Assert.assertEquals(result.snippetOutcomes().size(), 1);
        final ExceptionThrown snippetOutcome = (ExceptionThrown)result.snippetOutcomes().get(0);
        final Throwable exception = snippetOutcome.exception();
        if (!(exception instanceof TimeLimitExceededException))
            throw new AssertionError("wrong exception: " + exception);
        this.log.debug("got expected " + exception);

        // Check timing
        final long mismatch = (elapsedTime - timeLimit.toNanos()) / 1000000L;
        if (Duration.ofMillis(Math.abs(mismatch)).compareTo(maxDiff) > 0) {
            throw new AssertionError(String.format(
              "elapsed time differs from %dms by %dms > %dms", timeLimit.toMillis(), mismatch, maxDiff.toMillis()));
        }
    }
}
