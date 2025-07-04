
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import jdk.jshell.Snippet;

import org.dellroad.javabox.SnippetOutcome.CompilerError;
import org.dellroad.javabox.SnippetOutcome.SuccessfulNoValue;
import org.dellroad.javabox.SnippetOutcome.SuccessfulWithValue;
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
            ScriptResult r1 = box.validate(script);
            this.log.info("r1: {}", r1.snippetOutcomes());
            Assert.assertEquals(r1.snippetOutcomes().size(), 5);
            Assert.assertTrue(r1.isSuccessful());
            Assert.assertTrue(r1.snippetOutcomes().get(0) instanceof SuccessfulNoValue);
            Assert.assertTrue(r1.snippetOutcomes().get(1) instanceof SuccessfulNoValue);
            Assert.assertTrue(r1.snippetOutcomes().get(2) instanceof SuccessfulNoValue);
            Assert.assertTrue(r1.snippetOutcomes().get(3) instanceof SuccessfulNoValue);
            Assert.assertTrue(r1.snippetOutcomes().get(4) instanceof SuccessfulNoValue);

            // Test 2
            ScriptResult r2 = box.compile(script);
            this.log.info("r2: {}", r2.snippetOutcomes());
            Assert.assertEquals(r2.snippetOutcomes().size(), 5);
            Assert.assertTrue(!r2.isSuccessful());
            Assert.assertTrue(r2.snippetOutcomes().get(0) instanceof SuccessfulNoValue);
            Assert.assertTrue(r2.snippetOutcomes().get(1) instanceof SuccessfulNoValue);
            Assert.assertTrue(r2.snippetOutcomes().get(2) instanceof CompilerError ce
              && ce.diagnostics().stream().anyMatch(d -> d.getMessage(null).contains("cannot find symbol")));
            Assert.assertTrue(r2.snippetOutcomes().get(3) instanceof SuccessfulNoValue);
            Assert.assertTrue(r2.snippetOutcomes().get(4) instanceof SuccessfulNoValue);

            // Test 3
            ScriptResult r3 = box.execute(script);
            this.log.info("r3: {}", r3.snippetOutcomes());
            Assert.assertEquals(r3.snippetOutcomes().size(), 5);
            Assert.assertTrue(r3.isSuccessful());
            Assert.assertTrue(r3.snippetOutcomes().get(0) instanceof SuccessfulNoValue);
            Assert.assertEquals(r3.snippetOutcomes().get(0).snippet().subKind(), Snippet.SubKind.CLASS_SUBKIND);
            Assert.assertTrue(r3.snippetOutcomes().get(1) instanceof SuccessfulNoValue);
            Assert.assertEquals(r3.snippetOutcomes().get(1).snippet().subKind(), Snippet.SubKind.CLASS_SUBKIND);
            Assert.assertTrue(r3.snippetOutcomes().get(2) instanceof SuccessfulWithValue swv
              && swv.returnValue().getClass().getSimpleName().equals("Bar"));
            Assert.assertEquals(r3.snippetOutcomes().get(2).snippet().subKind(), Snippet.SubKind.TEMP_VAR_EXPRESSION_SUBKIND);
            Assert.assertTrue(r3.snippetOutcomes().get(3) instanceof SuccessfulNoValue);
            Assert.assertEquals(r3.snippetOutcomes().get(3).snippet().subKind(), Snippet.SubKind.CLASS_SUBKIND);
            Assert.assertTrue(r3.snippetOutcomes().get(4) instanceof SuccessfulWithValue swv
              && swv.returnValue().getClass().getSimpleName().equals("Jam"));
            Assert.assertEquals(r3.snippetOutcomes().get(4).snippet().subKind(), Snippet.SubKind.TEMP_VAR_EXPRESSION_SUBKIND);
        }
    }
}
