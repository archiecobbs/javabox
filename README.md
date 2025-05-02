# JavaBox
_Scripting in Java, by Java, for Java_

### What is it?

JavaBox tries to answer the question, "Where is the thing that will let me run scripts written in Java within my Java application?"

JavaBox is a simple container ("sandbox") for executing scripts written in Java. JavaBox does **not** provide a secure sandbox; it's not safe for untrusted code. Rather, it provides a basic sandbox that allows you to impose simple controls like time limits, instruction count limits, and restrictions on accessible classes. This allows, for example, an application to use Java as a runtime configuration language while restricting unwanted functionality like network I/O, `System.exit()`, etc.

### How does it Work?

Each JavaBox instance relies on an underlying [JShell](https://docs.oracle.com/en/java/javase/23/jshell/introduction-jshell.html) configured for local execution mode. That means JavaBox scripts are really JShell scripts that happen to be executing on the same JVM instance. Unlike with normal JShell, in which scripts can only return strings, JavaBox scripts can return arbitrary Java objects, and those objects can then be used normally, outside of the container.

JavaBox supports imposing restrictions on scripts using [controls](https://archiecobbs.github.io/javabox/site/apidocs/org/dellroad/javabox/Control.html). Controls are allowed to intercept a script's class loading step and each execution it performs.

### Examples

Here's a simple "Hello, World" example:
```java
Config config = Config.builder().build();
try (JavaBox box = new JavaBox(config)) {
    box.initialize();
    box.setVariable("target", "World");
    rv = box.execute("""
        String.format("Hello, %s!", target);
        """).returnValue();
}
System.out.println(rv);       // prints "Hello, World!"
```

Here's an example that shows how to use a [`TimeLimitControl`](https://archiecobbs.github.io/javabox/site/apidocs/org/dellroad/javabox/control/TimeLimitControl.html) to restrict how long a script may execute:
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

* Requires Java 24+ (except for the `execution` module which requires JDK 17+)

### Where do I get it?

JavaBox is available from [Maven Central](https://central.sonatype.com/search?q=g%3Aorg.dellroad+javabox).

### More Information

  * [API Javadocs](https://archiecobbs.github.io/javabox/site/apidocs/org/dellroad/javabox/JavaBox.html)
  * [Maven Site](https://archiecobbs.github.io/javabox/site/)
