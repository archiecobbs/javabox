
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import com.google.common.base.Preconditions;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * A checker for {@link ScriptResult}s that imposes some set of requirements that must be satisfied.
 *
 * <p>
 * By convention, instances should throw a {@link ResultCheckerException} (or some subtype) if a
 * requirement is not met, specifying a specific {@link SnippetOutcome} (by its index) if appropriate.
 *
 * @see JavaBox#execute JavaBox.execute()
 */
public interface ResultChecker extends Consumer<ScriptResult> {

    /**
     * Apply this instance to the given result.
     *
     * <p>
     * This method is a synonym for {@link #accept accept()}.
     *
     * @throws IllegalArgumentException if a requirement is violated
     */
    default void check(ScriptResult result) {
        this.accept(result);
    }

// Factory Methods

    /**
     * Create an instance that verifies that all {@link SnippetOutcome}s are successful.
     *
     * <p>
     * This just checks every {@link SnippetOutcome} is an instance of {@link SnippetOutcome.Successful}.
     */
    static ResultChecker allSuccessful() {
        return ResultChecker.checkSnippets((result, index) -> {
            final SnippetOutcome outcome = result.snippetOutcomes().get(index);
            if (!(outcome instanceof SnippetOutcome.Successful))
                throw new ResultCheckerException(result, index, "snippet not successful: " + outcome);
        });
    }

    /**
     * Create an instance that verifies that every {@link SnippetOutcome}s meets some requirement.
     *
     * <p>
     * The {@code snippetChecker} will be invoked with the index of each {@link SnippetOutcome}.
     * It should throw {@link ResultCheckerException} using its given parameters if the corresponding
     * {@link SnippetOutcome} fails to meet the requirement.
     *
     * @param snippetChecker checks each {@link SnippetOutcome}
     * @throws IllegalArgumentException if {@code snippetChecker} is null
     */
    static ResultChecker checkSnippets(BiConsumer<? super ScriptResult, ? super Integer> snippetChecker) {
        Preconditions.checkArgument(snippetChecker != null, "null snippetChecker");
        return result -> IntStream.range(0, result.snippetOutcomes().size())
                            .forEach(snippetIndex -> snippetChecker.accept(result, snippetIndex));
    }

    /**
     * Create an instance that verifies that the last {@link SnippetOutcome} was successful
     * and returned an object having the specified type.
     *
     * @param type expected return type
     * @throws IllegalArgumentException if {@code type} is null or primitive
     */
    static ResultChecker lastValueHasType(Class<?> type) {
        Preconditions.checkArgument(type != null, "null type");
        Preconditions.checkArgument(!type.isPrimitive(), "primitive type");
        return result -> {
            final int numSnippets = result.snippetOutcomes().size();
            if (numSnippets == 0)
                throw new ResultCheckerException(result, "script is empty");
            final int snippetIndex = numSnippets - 1;
            final SnippetOutcome outcome = result.snippetOutcomes().get(numSnippets - 1);
            if (!(outcome instanceof SnippetOutcome.SuccessfulWithValue success))
                throw new ResultCheckerException(result, snippetIndex, "final snippet was not successful: " + outcome);
            final Object value = success.returnValue();
            if (!type.isInstance(value)) {
                throw new ResultCheckerException(result, snippetIndex, String.format("script returned %s (expected %s)",
                  value != null ? "value of type " + value.getClass().getName() : "null value", type.getClass().getName()));
            }
        };
    }
}
