
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import com.google.common.base.Preconditions;

import java.util.List;

import jdk.jshell.Snippet;

/**
 * The result from executing a script in a {@link JavaBox}.
 *
 * <p>
 * Instances are thread safe.
 *
 * @see JavaBox#execute JavaBox.execute()
 */
public class ScriptResult {

    private final JavaBox box;
    private final String source;
    private final List<SnippetOutcome> snippetOutcomes;

    /**
     * Constructor.
     *
     * @param source the Java source for the script
     * @throws IllegalArgumentException if {@code source} is null
     */
    ScriptResult(JavaBox box, String source, List<SnippetOutcome> snippetOutcomes) {
        Preconditions.checkArgument(box != null, "null box");
        Preconditions.checkArgument(source != null, "null source");
        Preconditions.checkArgument(snippetOutcomes != null, "null snippets");
        this.box = box;
        this.source = source;
        this.snippetOutcomes = snippetOutcomes;
    }

// Properties

    /**
     * Get the associated {@link JavaBox}.
     *
     * @return associated container
     */
    public JavaBox javaBox() {
        return this.box;
    }

    /**
     * Get the original script source.
     *
     * @return script source
     */
    public String source() {
        return this.source;
    }

    /**
     * Get the outcomes of the individual JShell {@link Snippet}s created from the script.
     *
     * @return script snippet outcomes
     */
    public List<SnippetOutcome> snippetOutcomes() {
        return this.snippetOutcomes;
    }

    /**
     * Determine whether the script exeuction was successful.
     *
     * <p>
     * A successful script execution is one in which all of the individual {@link #snippetOutcomes}
     * were {@linkplain SnippetOutcome#isSuccessful successful}.
     *
     * @return script snippet outcomes
     */
    public boolean isSuccessful() {
        return this.snippetOutcomes.stream()
          .map(SnippetOutcome::type)
          .allMatch(SnippetOutcome.Type::isSuccessful);
    }
}
