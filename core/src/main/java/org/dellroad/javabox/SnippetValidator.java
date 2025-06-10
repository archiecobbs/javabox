
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import jdk.jshell.Snippet;
import jdk.jshell.SourceCodeAnalysis;

/**
 * Validator for snippets.
 *
 * @see JavaBox#process JavaBox.process()
 */
@FunctionalInterface
public interface SnippetValidator {

    /**
     * Validate a snippet.
     *
     * <p>
     * Note: The {@code snippet} will be <i>unassociated</i> (as described by
     * {@link SourceCodeAnalysis#sourceToSnippets SourceCodeAnalysis.sourceToSnippets()}).
     *
     * @param snippet snippet to validate
     * @throws SnippetValidationException if snippet is invalid
     */
    void validate(Snippet snippet) throws SnippetValidationException;
}
