# JavaBox
_Scripting in Java, by Java, for Java_

### What is it?

JavaBox tries to answer the question, "Where is the thing that will let me run scripts written in Java within my Java application?"

JavaBox is a simple container ("sandbox") for executing scripts written in Java. JavaBox does **not** provide a secure sandbox; it is not safe for untrusted Java code. Rather, it provides a basic sandbox that allows you to impose simple controls like time limits, instruction count limits, and restrictions on accessible classes. This allows, for example, an application to use Java as a runtime configuration language while disallowing unwanted functionality like network I/O, `System.exit()`, etc.

### How does it Work?

JavaBox uses [JShell](https://docs.oracle.com/en/java/javase/23/jshell/introduction-jshell.html) in local execution mode. That means scripts execute on the same JVM instance as the container itself. In particular, scripts can return Java objects, and these objects can be used normally, outside of the container. The mechanism by which the container imposes controls on scripts is by using [controls](https://archiecobbs.github.io/javabox/site/apidocs/org/dellroad/javabox/Control.html) which intercept the class loading step.

### Where do I get it?

JavaBox is available from [Maven Central](https://search.maven.org/search?q=a:javabox*%20AND%20g:org.dellroad).

```xml
    <dependency>
        <groupId>org.dellroad</groupId>
        <artifactId>javabox-main</artifactId>
    </dependency>
```

### Links

  * [Javadocs](https://archiecobbs.github.io/javabox/site/apidocs/org/dellroad/javabox/JavaBox.html)
  * [Maven Site](https://archiecobbs.github.io/javabox/site/)
