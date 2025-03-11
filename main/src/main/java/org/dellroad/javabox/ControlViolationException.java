
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

/**
 * Thrown when a script does something that exceeds imposed limits or otherwise is not allowed
 * by a configured {@link Control}.
 *
 * @see Control
 */
@SuppressWarnings("serial")
public class ControlViolationException extends JavaBoxException {

    public ControlViolationException(String message) {
        super(message);
    }

    public ControlViolationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ControlViolationException(Throwable cause) {
        super(cause);
    }

    @Override
    public ControlViolationException initCause(Throwable cause) {
        return (ControlViolationException)super.initCause(cause);
    }
}
