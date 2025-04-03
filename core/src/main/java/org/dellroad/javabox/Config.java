
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import com.google.common.base.Preconditions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import jdk.jshell.JShell;

/**
 * Configuration object for {@link JavaBox} instances.
 *
 * @param jshellBuilder The {@link JShell.Builder} associated with this instance
 * @param delegateLoader The delegate {@link ClassLoader} associated with this instance
 * @param controls The list of {@link Control}s associated with this instance
 */
public record Config(JShell.Builder jshellBuilder, ClassLoader delegateLoader, List<Control> controls) {

    private Config(Builder builder) {
        this(builder.createJShellBuilder(), builder.delegateLoader, Collections.unmodifiableList(builder.controls));
    }

    /**
     * Create a new {@link Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }

// Builder

    /**
     * Builder for {@link Config} instances.
     *
     * <p>
     * Instances are thread safe.
     */
    public static class Builder {

        private JShell.Builder jshellBuilder;
        private final List<Consumer<? super JShell.Builder>> builderMods = new ArrayList<>();
        private ClassLoader delegateLoader = Thread.currentThread().getContextClassLoader();
        private List<Control> controls = new ArrayList<>();

        Builder() {
        }

        /**
         * Build the {@link Config}.
         *
         * @return new {@link Config}
         */
        public synchronized Config build() {
            return new Config(this);
        }

        /**
         * Apply a modification to the {@link JShell.Builder} that will be used to create the {@link JShell} instance.
         *
         * <p>
         * The given modification will not actually be applied until {@link #build} is invoked. Modifications are
         * applied in the same order as this method is invoked.
         *
         * @param configurer {@link JShell} builder configurer
         * @throws IllegalArgumentException if {@code jshellBuilder} is null
         */
        public synchronized Builder withJShellMods(Consumer<? super JShell.Builder> configurer) {
            Preconditions.checkArgument(jshellBuilder != null, "null builderConfigurer");
            this.builderMods.add(configurer);
            return this;
        }

        /**
         * Provide a custom {@link JShell.Builder} to be used to create the {@link JShell} instance.
         *
         * <p>
         * For experts only.
         *
         * @param jshellBuilder {@link JShell} builder
         * @throws IllegalArgumentException if {@code jshellBuilder} is null
         */
        public synchronized Builder withJShellBuilder(JShell.Builder jshellBuilder) {
            Preconditions.checkArgument(jshellBuilder != null, "null jshellBuilder");
            this.jshellBuilder = jshellBuilder;
            return this;
        }

        private synchronized JShell.Builder createJShellBuilder() {

            // Custom builder?
            if (this.jshellBuilder != null)
                return this.jshellBuilder;

            // Apply our default special magic
            JShell.Builder builder = JShell.builder();
            builder.executionEngine(new JavaBoxExecutionControlProvider(), Map.of());
            final String path = JavaBoxExecutionControlProvider.inferClassPath(this.delegateLoader).stream()
              .collect(Collectors.joining(File.pathSeparator));
            if (!path.isEmpty())
                builder.compilerOptions("--class-path", path);

            // Apply modifications
            builderMods.forEach(mod -> mod.accept(builder));

            // Done
            return builder;
        }

        /**
         * Configure the {@link ClassLoader} to be used as the delegate loader
         * of the loader that loads the classes generated for applied scripts.
         *
         * <p>
         * The default value is the current thread's {@linkplain Thread#getContextClassLoader context class loader}
         * at the point this builder is instantiated.
         *
         * @param delegateLoader delegate {@link ClassLoader}, or null for the system class loader
         */
        public synchronized Builder withDelegateLoader(ClassLoader delegateLoader) {
            this.delegateLoader = delegateLoader;
            return this;
        }

        /**
         * Configure the list of {@link Control}s.
         *
         * <p>
         * In general, controls are applied in the order the appear in the list. For example, if a control
         * modifies bytecode, it will see the modifications of all controls earlier in the list.
         *
         * <p>
         * The default value is an empty list.
         *
         * @param controls list of controls
         * @throws IllegalArgumentException if {@code controls} or any element therein is null
         */
        public synchronized Builder withControls(List<Control> controls) {
            Preconditions.checkArgument(controls != null, "null controls");
            this.controls = new ArrayList<>(controls);
            this.controls.forEach(control -> Preconditions.checkArgument(control != null, "null control"));
            return this;
        }

        /**
         * Add a {@link Control} to the current list of {@link Control}s.
         *
         * @param control script control
         * @throws IllegalArgumentException if {@code control} is null
         */
        public synchronized Builder withControl(Control control) {
            Preconditions.checkArgument(control != null, "null control");
            this.controls.add(control);
            return this;
        }
    }
}
