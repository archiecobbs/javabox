
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

/**
 * Thrown when some error occurs during a {@link JavaBox} operation.
 */
@SuppressWarnings("serial")
public class JavaBoxException extends RuntimeException {

    public JavaBoxException(String message) {
        super(message);
    }

    public JavaBoxException(String message, Throwable cause) {
        super(message, cause);
    }

    public JavaBoxException(Throwable cause) {
        super(cause);
    }

    @Override
    public JavaBoxException initCause(Throwable cause) {
        return (JavaBoxException)super.initCause(cause);
    }
}
