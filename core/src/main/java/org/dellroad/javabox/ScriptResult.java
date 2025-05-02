
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
     * Convenience method to get the return value from a script's final snippet.
     *
     * <p>
     * This is useful in scenarios where the purpose of the script is to produce some result.
     * This checks that:
     * <ul>
     *  <li>The script was not empty, i.e., it contained at least one snippet
     *  <li>The outcome of every snippet in the script was {@linkplain SnippetOutcome.Successful successful}
     *  <li>The final snippet is an expression whose value is an instance of the given type
     * </ul>
     * If the above criteria are not met, a {@link ResultCheckerException} is thrown.
     *
     * @param type required return type
     * @return script return value (possibly null)
     * @throws IllegalArgumentException if {@code type} is null or primitive
     * @throws ResultCheckerException if script failed or did not return a value
     * @see ResultChecker#returns
     */
    public <T> T returnValue(Class<T> type) {
        ResultChecker.returns(type, true).check(this);
        final SnippetOutcome outcome = this.snippetOutcomes.get(this.snippetOutcomes.size() - 1);
        final SnippetOutcome.SuccessfulWithValue success = (SnippetOutcome.SuccessfulWithValue)outcome;
        return type.cast(success.returnValue());
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
