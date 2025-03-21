
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

/**
 * Captures a compilation error.
 *
 * @param lineNumber the line number at which the error occurred (starting with one)
 * @param columnNumber the column number at which the error occurred (starting with one)
 * @param errorMessage the error message
 */
public record CompilerError(int lineNumber, int columnNumber, String errorMessage) {

    /**
     * Format this instance like {@code "12:53: continue outside of loop"}.
     */
    @Override
    public String toString() {
        return String.format("%d:%d: %s", lineNumber(), columnNumber(), errorMessage());
    }
}
