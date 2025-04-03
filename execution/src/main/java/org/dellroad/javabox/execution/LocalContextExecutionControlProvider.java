
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox.execution;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import jdk.jshell.execution.LocalExecutionControl;
import jdk.jshell.execution.LocalExecutionControlProvider;
import jdk.jshell.spi.ExecutionControl;
import jdk.jshell.spi.ExecutionControlProvider;
import jdk.jshell.spi.ExecutionEnv;

import org.dellroad.stuff.java.MemoryClassLoader;

/**
 * Same as {@link LocalExecutionControlProvider}, but with additional support for handling certain
 * class path and class loading issues.
 *
 * <p>
 * When executing JShell locally, it is often desirable that any classes visible to the thread
 * that starts JShell should also be visible to any scripts and command inputs given to that JShell
 * instance.
 *
 * <p>
 * Unfortunately, this doesn't always happen automatically when using the standard {@code "local"}
 * execution provided by {@link LocalExecutionControl}.
 *
 * <p>
 * When executing JShell locally, there are two class path/loading issues to worry about:
 * <ul>
 *  <li>When JShell compiles source snippets, what classes are available on the "source class path"?
 *      That is, what class names can you refer to by name in your scripts or snippets?
 *  <li>When a compiled script or snippet is loaded as a Java class file by the execution engine, what
 *      classes are available on the "binary class path" when resolving symbolic references?
 * </ul>
 *
 * <p>
 * The standard {@link LocalExecutionControl} requires non-standard classes to be explicitly added
 * via the {@code --class-path} command line flag. Moreover, the {@link ClassLoader} that it uses delegates
 * to the system class loader by default, which means that in certain more complex class loading scenarios
 * (for example, when running as a servlet in the Tomcat web container), compiled snippets classes will fail
 * to load due to resolution errors.
 *
 * <p>
 * This class tries to workaround these issues as follows:
 * <ul>
 *  <li>To address the "binary class path", this class uses a {@link ClassLoader} that delegates
 *      to the current thread's context class loader instead of the system class loader. This should
 *      fix linking problems in complex class loading scenarios.
 *  <li>To address the "souce class path", this class introspects the current thread's context class
 *      loader and its parents (recursively), attempting to glean what's on the class path. This works
 *      for any {@link ClassLoader} that are instances of {@link URLClassLoader}. However, Java's standard
 *      application class loader is not, so items on the JVM application class path are missed by this
 *      strategy. To include the application class loader, hacky instrospection relying on illegal accesses
 *      is attempted (failures are silently ignored). To ensure these efforts succeed, the following flag
 *      must be added to JVM startup: {@code --add-opens=java.base/jdk.internal.loader=ALL-UNNAMED}.
 *      Note there is also another ugly workaround, which is to write JShell code that only accesses classes
 *      on the application class path via reflection.
 * </ul>
 *
 * <p>
 * To utilize this class, include the flags returned by {@link #modifyJShellFlags modifyJShellFlags()}
 * as parameters to JShell startup.
 *
 * <p>
 * This provider uses a {@link MemoryLoaderDelegate}.
 *
 * @see <a href="https://bugs.openjdk.org/browse/JDK-8314327">JDK-8314327</a>
 */
public class LocalContextExecutionControlProvider implements ExecutionControlProvider {

    public static final String NAME = "localContext";

// Public Methods

    /**
     * Modify a list of JShell tool command line flags to enable use of this class.
     *
     * @param loader loader to copy from, or null for the current thread's context class loader
     * @param flags modifiable list of command line flags
     * @throws IllegalArgumentException if {@code flags} is null
     */
    public static void modifyJShellFlags(ClassLoader loader, List<String> flags) {

        // Sanity check
        if (flags == null)
            throw new IllegalArgumentException("null flags");

        // Use our local execution engine, unless another is specified
        if (LocalContextExecutionControlProvider.getExecutionFlag(flags) == null)
            LocalContextExecutionControlProvider.setExecutionFlag(flags, NAME);

        // Infer the classpath from the class loader hierarchy
        List<String> classpath = LocalContextExecutionControlProvider.inferClassPath(loader);

        // Augment "--class-path" command line flag
        LocalContextExecutionControlProvider.addToClassPath(flags, new ArrayList<>(classpath));
    }

    /**
     * Infer the classpath from the {@code java.class.path} system property and
     * the given {@link ClassLoader} hierarchy.
     *
     * <p>
     * Works best when the JVM is started with {@code --add-opens=java.base/jdk.internal.loader=ALL-UNNAMED}.
     *
     * @param loader loader to copy from, or null for the current thread's context class loader
     * @return list of class path entries
     */
    public static List<String> inferClassPath(ClassLoader loader) {

        // Default to context loader
        if (loader == null)
            loader = Thread.currentThread().getContextClassLoader();

        // Start by grabbing anything from the "java.class.path" system property
        final LinkedHashSet<String> classpath = new LinkedHashSet<>();
        final String cp = System.getProperty("java.class.path");
        if (cp != null) {
            for (String item : cp.split(Pattern.quote(File.pathSeparator))) {
                if (!item.isEmpty())
                    classpath.add(item);
            }
        }

        // Visit class loader hierarchy and try to infer application classpath
        for ( ; loader != null; loader = loader.getParent()) {

            // Extract classpath URLs from this loader
            final URL[] urls;
            if (loader instanceof URLClassLoader)
                urls = ((URLClassLoader)loader).getURLs();
            else {
                try {
                    // Ugly hack; we are trying to do this: "urls = loader.ucp.getURLs();"
                    final Field field = loader.getClass().getDeclaredField("ucp");
                    field.setAccessible(true);
                    final Object ucp = field.get(loader);
                    final Method method = ucp.getClass().getMethod("getURLs");
                    urls = (URL[])method.invoke(ucp);
                } catch (ReflectiveOperationException | SecurityException | InaccessibleObjectException e) {
                    continue;
                }
            }

            // Convert URLs to class path entries
            for (URL url : urls) {
                final URI uri;
                try {
                    uri = url.toURI();
                } catch (URISyntaxException e) {
                    continue;
                }
                final File file;
                try {
                    file = Paths.get(uri).toFile();
                } catch (IllegalArgumentException | FileSystemNotFoundException e) {
                    continue;
                }
                classpath.add(file.toString());
            }
        }

        // Done
        return new ArrayList<>(classpath);
    }

    /**
     * Get the execution provider name specified via the {@code --execution} flag, if any.
     *
     * @param flags list of command line flags
     * @return execution provider name configured via {@code --execution} flag, or null if none
     * @throws IllegalArgumentException if {@code flags} is null
     * @see ExecutionControlProvider#name
     */
    public static String getExecutionFlag(List<String> flags) {
        final int index = flags.indexOf("--execution");
        return index >= 0 && index < flags.size() - 1 ? flags.get(index + 1) : null;
    }

    /**
     * Modify a list of JShell tool command line flags to force the use of the named
     * execution provider, overriding any previous.
     *
     * @param flags modifiable list of command line flags
     * @param providerName execution provider name
     * @throws IllegalArgumentException if {@code flags} is null
     * @see ExecutionControlProvider#name
     */
    public static void setExecutionFlag(List<String> flags, String providerName) {

        // Sanity check
        if (flags == null)
            throw new IllegalArgumentException("null flags");
        if (providerName == null)
            throw new IllegalArgumentException("null providerName");

        // Use specified execution engine
        final int index = flags.indexOf("--execution");
        if (index >= 0) {
            if (index < flags.size() - 1)
                flags.set(index + 1, providerName);
            else
                flags.add(providerName);    // weird, there was a bogus trailing "--execution" flag
        } else {
            flags.add("--execution");
            flags.add(providerName);
        }
    }

    /**
     * Utility method to modify the given JShell command line flags to add/augment the {@code --class-path}
     * flag(s) to (also) include the given classpath components.
     *
     * @param commandLine jshell command line, possibly including exisiting {@code --class-path} flag(s)
     * @param components new classpath components to add to {@code commandLine}
     * @throws IllegalArgumentException if either parameter is null
     */
    public static void addToClassPath(List<String> commandLine, List<String> components) {

        // Sanity check
        if (commandLine == null)
            throw new IllegalArgumentException("null commandLine");
        if (components == null)
            throw new IllegalArgumentException("null components");

        // Prepare new classpath
        final StringBuilder classPath = new StringBuilder();
        final String pathSeparator = System.getProperty("path.separator", ":");
        final Consumer<String> pathAdder = component -> {
            if (classPath.length() > 0)
                classPath.append(pathSeparator);
            classPath.append(component);
        };

        // Copy any existing classpath components and remove them from the command line
        int i = 0;
        while (i < commandLine.size()) {
            final String flag = commandLine.get(i);
            if (flag.startsWith("--class-path=")) {
                commandLine.remove(i);
                pathAdder.accept(flag.substring("--class-path=".length()));
            } else if (flag.equals("--class-path")) {
                commandLine.remove(i);
                if (i < commandLine.size())
                    pathAdder.accept(commandLine.remove(i));
            } else
                i++;
        }

        // Append new classpath components
        components.forEach(pathAdder);

        // Add back combined classpath to the command line (only one "--class-path" flag allowed)
        if (classPath.length() > 0) {
            commandLine.add("--class-path");
            commandLine.add(classPath.toString());
        }
    }

// ExecutionControlProvider

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Map<String, String> defaultParameters() {
        return ExecutionControlProvider.super.defaultParameters();
    }

    @Override
    public ExecutionControl generate(ExecutionEnv env, Map<String, String> params) {

        // Create our class loader
        final MemoryClassLoader memoryLoader = this.createMemoryClassLoader();

        // Create our delegate thingie
        final MemoryLoaderDelegate delegate = this.createMemoryLoaderDelegate(memoryLoader);

        // Create local ExecutionControl using delegate
        return this.createLocalExecutionControl(delegate);
    }

// Subclass Methods

    protected MemoryLoaderDelegate createMemoryLoaderDelegate(MemoryClassLoader memoryLoader) {
        return new MemoryLoaderDelegate(memoryLoader);
    }

    protected MemoryClassLoader createMemoryClassLoader() {
        return new MemoryClassLoader();
    }

    /**
     * Build a new {@link LocalExecutionControl} using the given delegate.
     *
     * <p>
     * The implementation in {@link LocalContextExecutionControlProvider} returns a new {@link LocalContextExecutionControl}.
     *
     * @param delegate the delegate to handle loading classes
     * @return new {@link LocalExecutionControl} instance
     */
    protected LocalExecutionControl createLocalExecutionControl(MemoryLoaderDelegate delegate) {
        return new LocalContextExecutionControl(delegate);
    }
}
