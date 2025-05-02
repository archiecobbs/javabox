
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import com.google.common.base.Preconditions;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import jdk.jshell.Snippet;

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
     * Create an instance that verifies that the last {@link SnippetOutcome} was a successful
     * expression returning a value having the specified type.
     *
     * <p>
     * This is useful in scenarios where the purpose of the script is to produce some result.
     * This checks that:
     * <ul>
     *  <li>The script was not empty, i.e., it contained at least one snippet
     *  <li>Every snippet in the script was {@linkplain SnippetOutcome.Successful successful}
     *  <li>The final snippet was an expression having the specified type
     *  <li>If {@code allowNull} is false, the returned value is not null
     * </ul>
     * If the above criteria are not met, a {@link ResultCheckerException} is thrown.
     *
     * @param type expected return type
     * @param allowNull true to allow null values, otherwise false
     * @throws IllegalArgumentException if {@code type} is null or primitive
     */
    static ResultChecker returns(Class<?> type, boolean allowNull) {
        Preconditions.checkArgument(type != null, "null type");
        Preconditions.checkArgument(!type.isPrimitive(), "primitive type");
        return result -> {

            // There must be at least one snippet
            final int numSnippets = result.snippetOutcomes().size();
            if (numSnippets == 0)
                throw new ResultCheckerException(result, "script is empty");

            // The final snippet must have been successful
            final int snippetIndex = numSnippets - 1;
            final SnippetOutcome outcome = result.snippetOutcomes().get(numSnippets - 1);
            if (!(outcome instanceof SnippetOutcome.Successful success))
                throw new ResultCheckerException(result, snippetIndex, "final snippet was not successful: " + outcome);
            final Snippet snippet = success.snippet();

            // The final snippet must be an expression
            switch (snippet.subKind()) {
            case ASSIGNMENT_SUBKIND:
            case OTHER_EXPRESSION_SUBKIND:
            case TEMP_VAR_EXPRESSION_SUBKIND:
            case VAR_VALUE_SUBKIND:
                break;
            default:
                throw new ResultCheckerException(result, snippetIndex,
                  String.format("final snippet was not an expression: kind=%s, subKind=%s", snippet.kind(), snippet.subKind()));
            }
            final SnippetOutcome.SuccessfulWithValue successWithValue = (SnippetOutcome.SuccessfulWithValue)success;

            // The snippet's return value must have the correct type
            final Object value = successWithValue.returnValue();
            if (value == null) {
                if (!allowNull) {
                    throw new ResultCheckerException(result, snippetIndex,
                      String.format("script returned null (expected %s)", type.getClass().getName()));
                }
            } else if (!type.isInstance(value)) {
                throw new ResultCheckerException(result, snippetIndex,
                  String.format("script returned value of type %s (expected %s)",
                  value.getClass().getName(), type.getClass().getName()));
            }
        };
    }
}
