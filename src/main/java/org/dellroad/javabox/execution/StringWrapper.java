
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox.execution;

import jdk.jshell.execution.DirectExecutionControl;

/**
 * This is a simple wrapper around a {@link String} in which {@link #toString} returns the wrapped {@link String}.
 *
 * <p>
 * The purpose of this class is simply to be unrecognized by JShell when {@link DirectExecutionControl#valueString}
 * displays the return value from some operation. Normally when that method sees a {@link String}, it displays it as
 * if it were a Java literal, adding double quotes and backslashes. If you instead wrap that {@link String} in this class,
 * then JShell will display it as-is.
 */
public record StringWrapper(String string) {

    /**
     * Constructor.
     *
     * @param string the actual string
     * @throws IllegalArgumentException if {@code string} is null
     */
    public StringWrapper {
        if (string == null)
            throw new IllegalArgumentException("null string");
    }

    /**
     * Returns the wrapped string.
     *
     * @return wrapped string
     */
    @Override
    public String toString() {
        return this.string();
    }
}
