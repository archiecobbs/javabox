
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import jdk.jshell.Snippet;

import org.dellroad.javabox.SnippetOutcome.CompilerError;
import org.dellroad.javabox.SnippetOutcome.SuccessfulNoValue;
import org.dellroad.javabox.SnippetOutcome.SuccessfulWithValue;
import org.dellroad.javabox.SnippetOutcome.ValidationFailure;
import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ErrorsTest extends TestSupport {

    @Test
    public void test1() throws Exception {
        Config config = Config.builder().build();
        try (JavaBox box = new JavaBox(config)) {
            box.initialize();

            String script = """
                class Foo extends Jam {
                }

                class Bar extends Foo {
                }

                new Bar();

                /* this is a comment; it does not count as a snippet */

                class Jam {
                }

                new Jam();
                """;

            // Test 1
            ScriptResult r1 = box.process(script, JavaBox.ProcessMode.VALIDATE, null);
            this.log.info("r1: {}", r1.snippetOutcomes());
            Assert.assertEquals(r1.snippetOutcomes().size(), 5);
            Assert.assertTrue(r1.isSuccessful());

            // Test 2
            ScriptResult r2 = box.process(script, JavaBox.ProcessMode.VALIDATE, snippet -> {
                if (snippet.kind() == Snippet.Kind.ERRONEOUS)
                    throw new SnippetValidationException("erroneous");
            });
            this.log.info("r2: {}", r2.snippetOutcomes());
            Assert.assertEquals(r2.snippetOutcomes().size(), 5);
            Assert.assertTrue(r2.snippetOutcomes().get(0) instanceof SuccessfulNoValue);
            Assert.assertTrue(r2.snippetOutcomes().get(1) instanceof SuccessfulNoValue);
            Assert.assertTrue(r2.snippetOutcomes().get(2) instanceof ValidationFailure vf
              && vf.exception().getMessage().equals("erroneous"));
            Assert.assertTrue(r2.snippetOutcomes().get(3) instanceof SuccessfulNoValue);
            Assert.assertTrue(r2.snippetOutcomes().get(4) instanceof ValidationFailure vf
              && vf.exception().getMessage().equals("erroneous"));

            // Test 3
            ScriptResult r3 = box.process(script, JavaBox.ProcessMode.COMPILE, null);
            this.log.info("r3: {}", r3.snippetOutcomes());
            Assert.assertEquals(r3.snippetOutcomes().size(), 5);
            Assert.assertTrue(r3.snippetOutcomes().get(0) instanceof SuccessfulNoValue);
            Assert.assertTrue(r3.snippetOutcomes().get(1) instanceof SuccessfulNoValue);
            Assert.assertTrue(r3.snippetOutcomes().get(2) instanceof CompilerError ce
              && ce.diagnostics().stream().anyMatch(d -> d.getMessage(null).contains("cannot find symbol")));
            Assert.assertTrue(r3.snippetOutcomes().get(3) instanceof SuccessfulNoValue);
            Assert.assertTrue(r3.snippetOutcomes().get(4) instanceof SuccessfulNoValue);

            // Test 4
            ScriptResult r4 = box.process(script, JavaBox.ProcessMode.EXECUTE, null);
            this.log.info("r4: {}", r4.snippetOutcomes());
            Assert.assertEquals(r4.snippetOutcomes().size(), 5);
            Assert.assertTrue(r4.snippetOutcomes().get(0) instanceof SuccessfulNoValue);
            Assert.assertTrue(r4.snippetOutcomes().get(1) instanceof SuccessfulNoValue);
            Assert.assertTrue(r4.snippetOutcomes().get(2) instanceof SuccessfulWithValue swv
              && swv.returnValue().getClass().getSimpleName().equals("Bar"));
            Assert.assertTrue(r4.snippetOutcomes().get(3) instanceof SuccessfulNoValue);
            Assert.assertTrue(r4.snippetOutcomes().get(4) instanceof SuccessfulWithValue swv
              && swv.returnValue().getClass().getSimpleName().equals("Jam"));
        }
    }
}
