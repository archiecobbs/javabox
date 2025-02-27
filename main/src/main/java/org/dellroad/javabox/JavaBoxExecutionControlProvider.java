
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import org.dellroad.javabox.execution.LocalContextExecutionControlProvider;
import org.dellroad.javabox.execution.MemoryLoaderDelegate;

/**
 * The {@link LocalContextExecutionControlProvider} used by {@link JavaBox}.
 */
public class JavaBoxExecutionControlProvider extends LocalContextExecutionControlProvider {

    public static final String NAME = "javaBox";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected JavaBoxExecutionControl createLocalExecutionControl(MemoryLoaderDelegate delegate) {
        return new JavaBoxExecutionControl(delegate);
    }
}
