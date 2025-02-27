
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

/**
 * Thrown when a {@link Script} throws an exception.
 */
@SuppressWarnings("serial")
public class ScriptExecutionException extends JavaBoxException {

    public ScriptExecutionException(String message) {
        super(message);
    }

    public ScriptExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public ScriptExecutionException(Throwable cause) {
        super(cause);
    }

    @Override
    public ScriptExecutionException initCause(Throwable cause) {
        return (ScriptExecutionException)super.initCause(cause);
    }
}
