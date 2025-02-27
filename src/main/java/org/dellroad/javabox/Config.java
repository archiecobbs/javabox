
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import com.google.common.base.Preconditions;

import java.util.Map;

import jdk.jshell.JShell;

/**
 * Configuration object for {@link JavaBox} instances.
 */
public record Config(

    /**
     * The {@link JShell.Builder} associated with this instance.
     *
     * @return {@link JShell} builder
     */
    JShell.Builder jshellBuilder,

    /**
     * The delegate {@link ClassLoader} associated with this instance.
     *
     * @return delegate {@link ClassLoader}
     */
    ClassLoader delegateLoader,

    /**
     * The {@link ScriptFilter} associated with this instance.
     *
     * @return script filter
     */
    ScriptFilter scriptFilter
    ) {

    private Config(Builder builder) {
        this(builder.jshellBuilder, builder.delegateLoader, builder.scriptFilter);
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
        private volatile ScriptFilter scriptFilter = ScriptFilter.identity();

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
         * Configure a {@link ScriptFilter}.
         *
         * <p>
         * The default value is {@link ScriptFilter#identity}.
         *
         * @param scriptFilter script filter
         * @throws IllegalArgumentException if {@code scriptFilter} is null
         */
        public Builder withScriptFilter(ScriptFilter scriptFilter) {
            Preconditions.checkArgument(scriptFilter != null, "null scriptFilter");
            this.scriptFilter = scriptFilter;
            return this;
        }
    }
}
