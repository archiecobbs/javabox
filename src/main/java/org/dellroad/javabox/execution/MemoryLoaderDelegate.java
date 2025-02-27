
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox.execution;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import jdk.jshell.execution.LoaderDelegate;
import jdk.jshell.spi.ExecutionControl.ClassBytecodes;
import jdk.jshell.spi.ExecutionControl.ClassInstallException;
import jdk.jshell.spi.ExecutionControl.EngineTerminationException;
import jdk.jshell.spi.ExecutionControl.InternalException;

import org.dellroad.stuff.java.MemoryClassLoader;

/**
 * A JShell {@link LoaderDelegate} that uses a {@link MemoryClassLoader}.
 *
 * <p>
 * This class accepts {@code --class-path} components that refer to JAR file contents using the {@code jar:} URL syntax,
 * e.g., {@code jar:file:/some/dir/foo.jar!/BOOT-INF/lib/bar.jar}. However, this is only partially useful because
 * (a) you can't build a classpath with components that contain colon characters, and (b) the jshell tool rejects
 * classpath components that aren't real files.
 */
public class MemoryLoaderDelegate implements LoaderDelegate {

    private static final String JAR_FILE_PREFIX = "jar:file:";

    private final HashMap<String, Class<?>> classMap = new HashMap<>();
    private final MemoryClassLoader loader;

// Constructors

    /**
     * Default constructor.
     *
     * <p>
     * Uses the current thread's context loader as the parent loader for a newly created {@link MemoryClassLoader}.
     */
    public MemoryLoaderDelegate() {
        this(new MemoryClassLoader());
    }

    /**
     * Primary constructor.
     *
     * @param loader associated class loader
     * @throws IllegalArgumentException if {@code loader} is null
     */
    public MemoryLoaderDelegate(MemoryClassLoader loader) {
        if (loader == null)
            throw new IllegalArgumentException("null loader");
        this.loader = loader;
    }

    /**
     * Get the {@link MemoryClassLoader} associated with this instance.
     *
     * @return associated class loader
     */
    public MemoryClassLoader getClassLoader() {
        return this.loader;
    }

// LoaderDelegate

    @Override
    public void addToClasspath(String path) throws EngineTerminationException, InternalException {
        if (path == null)
            throw new IllegalArgumentException("null path");
        try {
            for (String component : this.splitClassPath(path)) {
                this.loader.addURL(component.startsWith(JAR_FILE_PREFIX) ?
                  new URL(component) :
                  new File(component).toURI().toURL());
            }
        } catch (Exception e) {
            throw this.wrapCause(e, new InternalException(e.getMessage()));
        }
    }

    @Override
    public void classesRedefined(ClassBytecodes[] classes) {
        if (classes == null)
            throw new IllegalArgumentException("null classes");
        for (ClassBytecodes c : classes)
            this.loader.putClass(c.name(), c.bytecodes());
    }

    @Override
    public Class<?> findClass(String className) throws ClassNotFoundException {
        if (className == null)
            throw new IllegalArgumentException("null className");
        final Class<?> cl = this.classMap.get(className);
        if (cl == null)
            throw new ClassNotFoundException("class \"" + className + "\" not found");
        return cl;
    }

    @Override
    public void load(ClassBytecodes[] classes) throws ClassInstallException, EngineTerminationException {
        if (classes == null)
            throw new IllegalArgumentException("null classes");
        int numLoaded = 0;
        try {
            for (ClassBytecodes c : classes)
                this.loader.putClass(c.name(), c.bytecodes());
            for (ClassBytecodes c : classes) {
                final Class<?> cl = this.loader.loadClass(c.name());
                this.classMap.put(c.name(), cl);
                numLoaded++;
                cl.getDeclaredMethods();
            }
        } catch (Throwable t) {
            final boolean[] loaded = new boolean[classes.length];
            Arrays.fill(loaded, 0, numLoaded, true);
            throw this.wrapCause(t, new ClassInstallException(t.getMessage(), loaded));
        }
    }

// Internal Methods

    private <T extends Throwable, T2 extends Throwable> T2 wrapCause(T t, T2 t2) {
        t2.initCause(t);
        return t2;
    }

    private List<String> splitClassPath(String path) {
        final ArrayList<String> components = new ArrayList<>();
        for (int start = 0; start < path.length(); ) {
            int look = start;
            if (path.substring(start).startsWith(JAR_FILE_PREFIX))
                look += JAR_FILE_PREFIX.length();
            final int sep = path.indexOf(File.pathSeparatorChar, look);
            final int stop = sep != -1 ? sep : path.length();
            final String component = path.substring(start, stop);
            if (!component.isEmpty())
                components.add(component);
            start = Math.max(stop, sep + 1);
        }
        return components;
    }
}
