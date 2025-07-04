# JavaBox
_Scripting in Java, by Java, for Java_

### What is it?

JavaBox tries to answer the question, "Where is the thing that will let me run scripts written in Java within my Java application?"

JavaBox is a simple container ("sandbox") for executing scripts written in Java. JavaBox does **not** provide a secure sandbox; it's not safe for untrusted code.

Instead, it provides a basic sandbox that allows you to impose simple controls like time limits, instruction count limits, and restrictions on accessible classes. This allows, for example, an application to use Java as a runtime configuration language while having the ability to restrict unwanted functionality like network I/O, `System.exit()`, etc.

### How does it Work?

Each JavaBox instance relies on an underlying [JShell](https://docs.oracle.com/en/java/javase/24/jshell/introduction-jshell.html) instance configured for local execution mode. That means JavaBox scripts are really JShell scripts that happen to be executing on the same JVM instance. Unlike normal JShell scripts, which can only return strings, JavaBox scripts can return arbitrary Java objects, and those objects can then be used outside of the scripting environment.

JavaBox supports imposing restrictions on scripts using [Controls](https://archiecobbs.github.io/javabox/site/apidocs/org/dellroad/javabox/Control.html). Controls are allowed to [intercept](https://archiecobbs.github.io/javabox/site/apidocs/org/dellroad/javabox/Control.html#modifyBytecode(java.lang.constant.ClassDesc,byte%5B%5D)) a script's class loading step and [each time](https://archiecobbs.github.io/javabox/site/apidocs/org/dellroad/javabox/Control.html#startExecution(org.dellroad.javabox.Control.ContainerContext)) it executes.

JavaBox supports [interrupting](https://archiecobbs.github.io/javabox/site/apidocs/org/dellroad/javabox/JavaBox.html#interrupt()) scripts in the middle of execution if necessary, and it also has [suspend](https://archiecobbs.github.io/javabox/site/apidocs/org/dellroad/javabox/JavaBox.html#suspend(java.lang.Object)) and [resume](https://archiecobbs.github.io/javabox/site/apidocs/org/dellroad/javabox/JavaBox.html#resume(java.lang.Object)) functions, allowing an application thread to suspend scripts on certain operations and regain control.

### Examples

Here's a "Hello, World" example:
```java
Object returnValue;
try (JavaBox box = new JavaBox(Config.builder().build())) {
    box.initialize();
    box.setVariable("target", "World");
    String script = "String.format(\"Hello, %s!\", target);";
    returnValue = box.execute(script).returnValue();
}
System.out.println(returnValue);     // prints "Hello, World!"
```

Here's an example that shows how to use a [`TimeLimitControl`](https://archiecobbs.github.io/javabox/site/apidocs/org/dellroad/javabox/control/TimeLimitControl.html) to kill a script that takes too long:
```java
// Set up control
Config config = Config.builder()
  .withControl(new TimeLimitControl(Duration.ofSeconds(5)))
  .build();

// Execute script
ScriptResult result;
try (JavaBox box = new JavaBox(config)) {
    box.initialize();
    result = box.execute("""
        while (true) {
            Thread.yield();
        }
    """);
}

// Check result
switch (result.snippetOutcomes().get(0)) {
case SnippetOutcome.ExceptionThrown e when e.exception() instanceof TimeLimitExceededException
  -> System.out.println("script was taking too long");
}
```

### Caveats

* The `controls` module requires Java 24+ because it uses the new `ClassFile` API; everything else requires JDK 17+.

### Where do I get it?

JavaBox is available from [Maven Central](https://central.sonatype.com/search?q=g%3Aorg.dellroad+javabox).

### More Information

  * [API Javadocs](https://archiecobbs.github.io/javabox/site/apidocs/org/dellroad/javabox/JavaBox.html)
  * [Maven Site](https://archiecobbs.github.io/javabox/site/)
