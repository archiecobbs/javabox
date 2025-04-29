
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox.control;

import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ClassDesc;

import org.dellroad.javabox.Config;
import org.dellroad.javabox.JavaBox;
import org.dellroad.javabox.SnippetOutcome;
import org.dellroad.javabox.SnippetOutcome.ControlViolation;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ClassReferenceTest extends ControlTestSupport {

    @Test
    public void testClassReference() throws Exception {
        final ClassDesc disallowed = ClassDesc.of("java.util.ArrayList");
        Config config = Config.builder()
          .withControl(new ClassReferenceControl(c -> !c.asSymbol().equals(disallowed)))
          .build();
        try (JavaBox box = new JavaBox(config)) {
            box.initialize();

            // These should work
            this.checkResult(box.execute("import java.util.*;"));
            this.checkResult(box.execute("var x = new LinkedList<String>();"));
            this.checkResult(box.execute("x.add(\"foo\");"));

            // This should fail with a control violation
            final SnippetOutcome outcome = this.checkResult(box.execute("new ArrayList<String>();"), ControlViolation.class);
            final ControlViolation violation = (ControlViolation)outcome;
            final IllegalPoolEntryException e = (IllegalPoolEntryException)violation.exception();
            Assert.assertTrue(e.getPoolEntry() instanceof ClassEntry ce && ce.asSymbol().equals(disallowed));
        }
    }
}
