
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

/**
 * Thrown by a {@link SnippetValidator} passed to {@link JavaBox#process JavaBox.process()} when validation fails.
 *
 * @see JavaBox#process JavaBox.process()
 */
@SuppressWarnings("serial")
public class SnippetValidationException extends JavaBoxException {

    public SnippetValidationException(String message) {
        super(message);
    }

    public SnippetValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public SnippetValidationException(Throwable cause) {
        super(cause);
    }

    @Override
    public SnippetValidationException initCause(Throwable cause) {
        return (SnippetValidationException)super.initCause(cause);
    }
}
