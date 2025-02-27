
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

/**
 * Thrown when a {@link Script} executes some action that is not allowed by the configured {@link ScriptFilter}.
 *
 * @see ScriptFilter
 */
@SuppressWarnings("serial")
public class IllegalActionException extends ScriptExecutionException {

    public IllegalActionException(String message) {
        super(message);
    }

    public IllegalActionException(String message, Throwable cause) {
        super(message, cause);
    }

    public IllegalActionException(Throwable cause) {
        super(cause);
    }

    @Override
    public IllegalActionException initCause(Throwable cause) {
        return (IllegalActionException)super.initCause(cause);
    }
}
