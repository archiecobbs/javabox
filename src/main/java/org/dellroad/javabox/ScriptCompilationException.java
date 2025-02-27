
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

/**
 * Thrown when a {@link Script} fails to compile.
 *
 * @see ScriptFilter
 */
@SuppressWarnings("serial")
public class ScriptCompilationException extends JavaBoxException {

    public ScriptCompilationException(String message) {
        super(message);
    }

    public ScriptCompilationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ScriptCompilationException(Throwable cause) {
        super(cause);
    }

    @Override
    public ScriptCompilationException initCause(Throwable cause) {
        return (ScriptCompilationException)super.initCause(cause);
    }
}
