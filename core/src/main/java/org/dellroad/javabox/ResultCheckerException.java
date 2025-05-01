
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import com.google.common.base.Preconditions;

import java.io.Serial;

/**
 * Exception thrown by a {@link ResultChecker} when a requirement is violated.
 *
 * @see ResultChecker
 */
public class ResultCheckerException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = -7718141137523676294L;

    private final ScriptResult result;
    private final int snippetIndex;

    /**
     * Constructor for exceptions that are not specific to a single snippet.
     *
     * @param result the result that generated this exception
     * @param message exception message, or null for none
     */
    public ResultCheckerException(ScriptResult result, String message) {
        this(result, -1, message, null);
    }

    /**
     * Constructor for exceptions that are not specific to a single snippet.
     *
     * @param result the result that generated this exception
     * @param message exception message, or null for none
     * @param cause underlying cause, or null for none
     */
    public ResultCheckerException(ScriptResult result, String message, Throwable cause) {
        this(result, -1, message, cause);
    }

    /**
     * Constructor.
     *
     * @param result the result that generated this exception
     * @param snippetIndex offending snippet index, or -1 if not specific to a single snippet
     * @param message exception message, or null for none
     */
    public ResultCheckerException(ScriptResult result, int snippetIndex, String message) {
        this(result, snippetIndex, message, null);
    }

    /**
     * Constructor.
     *
     * @param result the result that generated this exception
     * @param snippetIndex offending snippet index, or -1 if not specific to a single snippet
     * @param message exception message, or null for none
     * @param cause underlying cause, or null for none
     */
    public ResultCheckerException(ScriptResult result, int snippetIndex, String message, Throwable cause) {
        super(message, cause);
        Preconditions.checkArgument(result != null, "null result");
        Preconditions.checkArgument(snippetIndex >= -1, "invalid snippetIndex");
        Preconditions.checkArgument(snippetIndex < result.snippetOutcomes().size(), "invalid snippetIndex");
        this.result = result;
        this.snippetIndex = snippetIndex;
    }

    /**
     * Get the {@link ScriptResult} which generated this exception.
     *
     * @return the result that generated this exception
     */
    public ScriptResult result() {
        return this.result;
    }

    /**
     * Get the index of the snippet that generated this exception.
     *
     * @return snippet index, or -1 if this error is not specific to a single snippet
     */
    public int snippetIndex() {
        return this.snippetIndex;
    }
}
