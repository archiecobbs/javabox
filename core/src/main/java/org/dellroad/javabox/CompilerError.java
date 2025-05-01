
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import com.google.common.base.Preconditions;

/**
 * Captures a compilation error.
 *
 * @param lineCol the location where the error occurred (one based)
 * @param errorMessage the error message
 */
public record CompilerError(LineAndColumn lineCol, String errorMessage) {

    public CompilerError {
        Preconditions.checkArgument(lineCol != null, "null lineCol");
        Preconditions.checkArgument(errorMessage != null, "null errorMessage");
    }

    /**
     * Format this instance like {@code "12:34: continue outside of loop"}.
     */
    @Override
    public String toString() {
        return String.format("%d:%d: %s", lineCol.lineOffset() + 1, lineCol.columnOffset() + 1, errorMessage);
    }
}
