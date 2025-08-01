
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import com.google.common.base.Preconditions;

import java.util.List;
import java.util.Optional;

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
    public JavaBox box() {
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
     * Get the outcome of the first {@link Snippet} whose outcome was not an instance of {@link SnippetOutcome.Successful}.
     *
     * @return last snippet outcome, or empty if there were zero snippets
     */
    public Optional<SnippetOutcome> firstUnsuccessful() {
        return this.snippetOutcomes.stream()
          .filter(outcome -> !(outcome instanceof SnippetOutcome.Successful))
          .findFirst();
    }

    /**
     * Get the outcome of the last {@link Snippet}.
     *
     * @return last snippet outcome, or empty if there were zero snippets
     */
    public Optional<SnippetOutcome> lastOutcome() {
        if (this.snippetOutcomes.isEmpty())
            return Optional.empty();
        return Optional.of(this.snippetOutcomes.get(this.snippetOutcomes.size() - 1));
    }

    /**
     * Get the outcome of the last {@link Snippet} whose execution was attempted.
     *
     * <p>
     * This returns the last outcome which is not an instance of {@link SnippetOutcome.Skipped}.
     *
     * @return last snippet outcome, or empty if there were zero snippets
     */
    public Optional<SnippetOutcome> lastAttempted() {
        // TODO: In JDK 21+, can use this.snippetOutcomes.reversed().stream()...
        SnippetOutcome outcome = null;
        for (int index = this.snippetOutcomes.size() - 1; index >= 0; index--) {
            if (!((outcome = this.snippetOutcomes.get(index)) instanceof SnippetOutcome.Skipped))
                break;
        }
        return Optional.ofNullable(outcome);
    }

    /**
     * Determine whether the script exeuction was successful.
     *
     * <p>
     * A successful script execution is one in which all of the individual {@link #snippetOutcomes}
     * are instances of {@link SnippetOutcome.Successful}.
     *
     * @return script snippet outcomes
     */
    public boolean isSuccessful() {
        return this.snippetOutcomes.stream()
          .allMatch(SnippetOutcome.Successful.class::isInstance);
    }
}
