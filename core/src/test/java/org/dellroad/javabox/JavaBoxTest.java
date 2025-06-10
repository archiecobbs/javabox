
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jdk.jshell.DeclarationSnippet;
import jdk.jshell.JShell;
import jdk.jshell.Snippet;

import org.dellroad.javabox.SnippetOutcome.SuccessfulWithValue;
import org.dellroad.javabox.SnippetOutcome.UnresolvedReferences;
import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.Test;

public class JavaBoxTest extends TestSupport {

    @Test
    public void testNoInit() throws Exception {
        Config config = Config.builder().build();
        try (JavaBox box = new JavaBox(config)) {
            box.execute("\"hello world\"");
            throw new AssertionError("expected exception");
        } catch (IllegalStateException e) {
            this.log.debug("got expected {}", e.toString());
        }
    }

    @Test
    public void testAlreadyClosed() throws Exception {
        Config config = Config.builder().build();
        JavaBox box = new JavaBox(config);
        box.initialize();
        box.close();
        try {
            box.execute("\"hello world\"");
            throw new AssertionError("expected exception");
        } catch (IllegalStateException e) {
            this.log.debug("got expected {}", e.toString());
        }
    }

    @Test
    public void testSimpleExpression() throws Exception {
        Config config = Config.builder().build();
        final String content = "hello world";
        final String source = "\"" + content + "\"";
        final ScriptResult result;
        final JavaBox theBox;
        try (JavaBox box = new JavaBox(config)) {
            theBox = box;
            box.initialize();
            result = box.execute(source);
        }
        Assert.assertSame(result.box(), theBox);
        Assert.assertEquals(result.source(), source);
        final List<SnippetOutcome> snippetOutcomes = result.snippetOutcomes();
        Assert.assertEquals(snippetOutcomes.size(), 1);
        final SnippetOutcome snippetOutcome = snippetOutcomes.get(0);
        Assert.assertEquals(snippetOutcome.box(), theBox);
        Assert.assertEquals(snippetOutcome.snippet().source(), source);
        Assert.assertTrue(snippetOutcome instanceof SuccessfulWithValue);
        final SuccessfulWithValue s = (SuccessfulWithValue)snippetOutcome;
        Assert.assertEquals(s.snippet().kind(), Snippet.Kind.VAR);
        Assert.assertEquals(s.returnValue(), content);
    }

    @Test
    public void testUnresolvedReferences() throws Exception {
        Config config = Config.builder().build();
        try (JavaBox box = new JavaBox(config)) {
            box.initialize();
            final JShell jsh = box.getJShell();
            final ScriptResult result1 = box.execute("class Foo { public int x = Bar.y + 1; }");
            Assert.assertEquals(this.unresolvedReferences(box, result1), Set.of("variable Bar"));
            final ScriptResult result2 = box.execute("class Bar { public static int y = 17; }");
            Assert.assertEquals(this.unresolvedReferences(box, result1), Set.of());
            Assert.assertEquals(this.unresolvedReferences(box, result2), Set.of());
            final ScriptResult result3 = box.execute("new Foo().x;");
            Assert.assertEquals(((SuccessfulWithValue)result3.snippetOutcomes().get(0)).returnValue(), 18);
        }
    }

    private Set<String> unresolvedReferences(JavaBox box, ScriptResult result) {
        Assert.assertEquals(result.snippetOutcomes().size(), 1);
        return unresolvedReferences(box, result.snippetOutcomes().get(0));
    }

    private Set<String> unresolvedReferences(JavaBox box, SnippetOutcome snippetOutcome) {
        if (!(snippetOutcome instanceof UnresolvedReferences unresolved))
            return Set.of();
        return box.getJShell().unresolvedDependencies((DeclarationSnippet)unresolved.snippet())
          .collect(Collectors.toSet());
    }
}
