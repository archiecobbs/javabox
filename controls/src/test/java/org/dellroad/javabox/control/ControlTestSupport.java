
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox.control;

import java.util.List;
import java.util.stream.Stream;

import org.dellroad.javabox.ScriptResult;
import org.dellroad.javabox.SnippetOutcome;
import org.dellroad.javabox.SnippetOutcome.Successful;
import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;

public class ControlTestSupport extends TestSupport {

    protected SnippetOutcome checkResult(ScriptResult result, Class<?>... types) {
        final List<SnippetOutcome> outcomes = result.snippetOutcomes();
        Assert.assertEquals(outcomes.size(), 1);
        final SnippetOutcome outcome = outcomes.get(0);
        if (types.length == 0)
            types = new Class<?>[] { Successful.class };
        Assert.assertTrue(Stream.of(types).anyMatch(type -> type.isInstance(outcome)), "unexpected outcome " + outcome);
        return outcome;
    }

    // Re-encode hex data via this command: xxd -c 16 -p -r
    protected void hexDump(String headline, byte[] bytes) {
        this.log.info("{}", headline);
        final StringBuilder buf = new StringBuilder(32);
        for (int i = 0; i < bytes.length; i++) {
            buf.append(String.format("%02x", bytes[i] & 0xff));
            if (i % 16 == 15) {
                System.out.println(buf);
                buf.setLength(0);
            }
        }
        if (buf.length() > 0)
            System.out.println(buf);
    }
}
