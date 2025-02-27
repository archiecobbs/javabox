
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import java.util.Set;

import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.Test;

public class JavaBoxTest extends TestSupport {

    @Test
    public void test1() throws Exception {
        Config config = Config.builder().build();
        try (JavaBox box = new JavaBox(config)) {
            Script script = box.apply("\"hello world\"");
            Assert.assertEquals(script.returnValue(), "hello world");
        }
    }

    @Test
    public void test2() throws Exception {
        Config config = Config.builder().build();
        try (JavaBox box = new JavaBox(config)) {
            Script script1 = box.apply("class Foo { public int x = Bar.y + 1; }");
            Assert.assertEquals(script1.unresolvedDependencies(), Set.of("variable Bar"));
            Script script2 = box.apply("class Bar { public static int y = 17; }");
            Assert.assertEquals(script1.unresolvedDependencies(), Set.of());
            Script script3 = box.apply("new Foo().x;");
            Assert.assertEquals(script3.returnValue(), 18);
        }
    }
}
