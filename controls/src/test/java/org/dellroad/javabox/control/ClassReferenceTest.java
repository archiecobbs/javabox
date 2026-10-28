
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox.control;

import java.lang.constant.ClassDesc;

import org.dellroad.javabox.Config;
import org.dellroad.javabox.JavaBox;
import org.dellroad.javabox.ScriptResult;
import org.dellroad.javabox.SnippetOutcome;
import org.dellroad.javabox.SnippetOutcome.ControlViolation;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ClassReferenceTest extends ControlTestSupport {

    @Test
    public void testClassReference() throws Exception {
        final ClassDesc disallowed = ClassDesc.of("java.util.ArrayList");
        try (JavaBox box = new JavaBox(this.configDisallowing(disallowed))) {
            box.initialize();

            // These should work
            this.checkResult(box.execute("import java.util.*;"));
            this.checkResult(box.execute("var x = new LinkedList<String>();"));
            this.checkResult(box.execute("x.add(\"foo\");"));

            // This should fail with a control violation
            this.verifyDisallowed(disallowed, box.execute("new ArrayList<String>();"));
        }
    }

    @Test
    public void testMethodTypeEntry() throws Exception {
        this.shouldFail(ClassDesc.of("java.io.Console"),
            """
            ThreadLocal.<Object>withInitial(System::console);
            """);
    }

    @Test
    public void testNameAndTypeEntry() throws Exception {
        this.shouldFail(ClassDesc.of("java.io.Console"),
            """
            public Object method() {
                return System.console();
            }
            """);
    }

    private void shouldFail(ClassDesc disallowed, String script) {
        try (JavaBox box = new JavaBox(this.configDisallowing(disallowed))) {
            box.initialize();
            this.verifyDisallowed(disallowed, box.execute(script));
        }
    }

    private Config configDisallowing(ClassDesc disallowed) {
        return Config.builder()
          .withControl(new ClassReferenceControl(c -> !c.equals(disallowed)))
          .build();
    }

    private void verifyDisallowed(ClassDesc disallowed, ScriptResult result) {
        final SnippetOutcome outcome = this.checkResult(result, ControlViolation.class);
        final ControlViolation violation = (ControlViolation)outcome;
        final IllegalPoolEntryException e = (IllegalPoolEntryException)violation.exception();
        Assert.assertTrue(e.toString().contains(disallowed.displayName()));
    }
}
