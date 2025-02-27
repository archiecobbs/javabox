# JavaBox
_Scripting in Java, by Java, for Java_

JavaBox tries to answer the question, "Where is the thing that will let me do basic scripting in Java?"

JavaBox is a container in which scripts written in Java can be safely controlled and executed, where the precise definition of "safely" is up to you.

JavaBox uses [JShell](https://docs.oracle.com/en/java/javase/23/jshell/introduction-jshell.html) in local execution mode. That means scripts execute on the same JVM as the JavaBox container. In particular, scripts can return Java objects, and these objects can be retrieved and used outside of the container. The container imposes controls on what the script can do by intercepting the class loading step. This allows, for example, an application to use Java as a runtime configuration language while disallowing unwanted functionality like network I/O, `System.exit()`, etc.