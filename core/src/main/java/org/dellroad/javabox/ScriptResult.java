
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

    /**
     * Obtain the value returned by the final snippet.
     *
     * <p>
     * This method is equivalent to: {@link #returnValue(Class)}{@code (Object.class)}.
     *
     * @return the {@linkplain SnippetOutcome.SuccessfulWithValue#returnValue return value} of the last snippet,
     *  or null if it has outcome {@link SnippetOutcome.SuccessfulNoValue}
     * @throws IllegalArgumentException if the script contained zero snippets
     * @throws RuntimeException if the outcome of the last snippet is neither {@link SnippetOutcome.SuccessfulWithValue}
     *  nor {@link SnippetOutcome.SuccessfulNoValue}
     */
    public Object returnValue() {
        return this.returnValue(Object.class);
    }

    /**
     * Obtain the value returned by the final snippet.
     *
     * <p>
     * This is a convenience method for simple evaluation scenarios where you expect successful execution.
     * Specifically, this method does the following:
     * <ul>
     *  <li>Verify that the script contained at least one snippet
     *  <li>Verify that the outcome of the last snippet is either {@link SnippetOutcome.SuccessfulWithValue}
     *      or {@link SnippetOutcome.SuccessfulNoValue}
     *  <li>If the outcome of the last snippet is {@link SnippetOutcome.SuccessfulWithValue}, cast its
     *      {@linkplain SnippetOutcome.SuccessfulWithValue#returnValue return value} to {@code expectedType}.
     * </ul>
     * This method returns the last snippet's return value, if any, otherwise null.
     *
     * @param expectedType the expected type of the final snippet
     * @return the {@linkplain SnippetOutcome.SuccessfulWithValue#returnValue return value} of the last snippet,
     *  or null if it has outcome {@link SnippetOutcome.SuccessfulNoValue}
     * @throws IllegalArgumentException if the script contained zero snippets
     * @throws RuntimeException if the outcome of the last snippet is neither {@link SnippetOutcome.SuccessfulWithValue}
     *  nor {@link SnippetOutcome.SuccessfulNoValue}
     * @throws ClassCastException if the return value of the last snippet is not compatible with {@code expectedType}
     * @throws IllegalArgumentException if {@code expectedType} is null
     */
    public <T> T returnValue(Class<T> expectedType) {
        final SnippetOutcome outcome = this.lastOutcome().orElse(null);
        if (outcome == null)
            throw new IllegalArgumentException("script contained zero snippets");
        if (outcome instanceof SnippetOutcome.SuccessfulNoValue)
            return null;
        if (outcome instanceof SnippetOutcome.SuccessfulWithValue withValue)
            return expectedType.cast(withValue.returnValue());
        throw new RuntimeException("unexpected outcome: " + outcome);
    }
}
