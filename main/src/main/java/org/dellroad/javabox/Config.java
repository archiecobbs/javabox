
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
        this(builder.jshellBuilder, builder.delegateLoader, Collections.unmodifiableList(builder.controls));
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

        private volatile JShell.Builder jshellBuilder = JShell.builder();
        private volatile ClassLoader delegateLoader = Thread.currentThread().getContextClassLoader();
        private volatile List<Control> controls = new ArrayList<>();

        Builder() {
            this.jshellBuilder.executionEngine(new JavaBoxExecutionControlProvider(), Map.of());
        }

        /**
         * Build the {@link Config}.
         *
         * @return new {@link Config}
         */
        public Config build() {
            return new Config(this);
        }

        /**
         * Configure the {@link JShell.Builder} used to create the {@link JShell} instance used by the {@link JavaBox}.
         *
         * <p>
         * For experts only.
         *
         * @param jshellBuilder {@link JShell} builder
         * @throws IllegalArgumentException if {@code jshellBuilder} is null
         */
        public Builder withJshellBuilder(JShell.Builder jshellBuilder) {
            Preconditions.checkArgument(jshellBuilder != null, "null jshellBuilder");
            this.jshellBuilder = jshellBuilder;
            return this;
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
        public Builder withDelegateLoader(ClassLoader delegateLoader) {
            this.delegateLoader = delegateLoader;
            return this;
        }

        /**
         * Configure the {@link Control}s.
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
        public Builder withControls(List<Control> controls) {
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
        public Builder withControl(Control control) {
            Preconditions.checkArgument(control != null, "null control");
            this.controls.add(control);
            return this;
        }
    }
}
