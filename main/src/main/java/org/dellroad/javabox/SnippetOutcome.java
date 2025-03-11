
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import com.google.common.base.Preconditions;

import java.util.List;
import java.util.Optional;

import jdk.jshell.JShell;
import jdk.jshell.Snippet;

/**
 * Captures the outcome from one of the {@link Snippet}s that constitute a {@link JavaBox} script.
 *
 * <p>
 * A snippet can fail by failing to compile or by throwing an exception when executed.
 * Some types of snippets return a value; you can use {@link SnippetOutcome.Type} to determine if so.
 *
 * <p>
 * Declaration snippets can have unresolved references; use {@link JShell#unresolvedDependencies
 * JShell.unresolvedDependencies()} to enumerate them. Use {@link JShell#status JShell.status()}
 * to inquire about overall snippet status.
 */
public class SnippetOutcome {

    private final JavaBox box;
    private final Type type;
    private final String source;
    private final Optional<Snippet> snippet;
    private final Object returnValue;
    private final Optional<Throwable> exception;
    private final Optional<List<CompilerError>> compilerErrors;

// Constructor & Factory Methods

    SnippetOutcome(JavaBox box, Type type, String source, Optional<Snippet> snippet,
      Object returnValue, Optional<Throwable> exception, Optional<List<CompilerError>> compilerErrors) {
        this.box = box;
        this.type = type;
        this.source = source;
        this.snippet = snippet;
        this.returnValue = returnValue;
        this.exception = exception;
        this.compilerErrors = compilerErrors;
    }

    static SnippetOutcome compilerErrors(JavaBox box, String source, List<CompilerError> compilerErrors) {
        return new SnippetOutcome(box, Type.COMPILER_ERRORS, source, Optional.empty(),
          Optional.empty(), Optional.empty(), Optional.of(compilerErrors));
    }

    static SnippetOutcome compilerErrors(JavaBox box, Snippet snippet, List<CompilerError> compilerErrors) {
        return new SnippetOutcome(box, Type.COMPILER_ERRORS, snippet.source(), Optional.of(snippet),
          Optional.empty(), Optional.empty(), Optional.of(compilerErrors));
    }

    static SnippetOutcome exceptionThrown(JavaBox box, Snippet snippet, Throwable exception) {
        return new SnippetOutcome(box, Type.EXCEPTION_THROWN, snippet.source(), Optional.of(snippet),
          Optional.empty(), Optional.of(exception), Optional.empty());
    }

    static SnippetOutcome success(JavaBox box, Snippet snippet, Object returnValue) {
        final Type type;
        switch (snippet.kind()) {
        case EXPRESSION:
        case VAR:
            type = Type.SUCCESSFUL_WITH_VALUE;
            break;
        default:
            type = Type.SUCCESSFUL_NO_VALUE;
            assert returnValue == null;
            break;
        }
        return new SnippetOutcome(box, type, snippet.source(),
          Optional.of(snippet), returnValue, Optional.empty(), Optional.empty());
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
     * Get the source code of the snippet.
     *
     * @return snippet source
     */
    public String source() {
        return this.source;
    }

    /**
     * Get the {@link Type} of this snippet outcome.
     *
     * @return outcome type
     */
    public Type type() {
        return this.type;
    }

    /**
     * Get the {@link Snippet} created by JShell, if any.
     *
     * @return snippet, or empty if there were compilation errors
     */
    public Optional<Snippet> snippet() {
        return this.snippet;
    }

    /**
     * Get the value returned from the snippet's execution, if any.
     *
     * <p>
     * Only snippets of type {@link Snippet.Kind#EXPRESSION} and {@link Snippet.Kind#VAR} return values.
     *
     * @return snippet return value, possibly null
     * @throws IllegalArgumentException if {@link #type} is not {@link Type#SUCCESSFUL_WITH_VALUE}
     */
    public Object returnValue() {
        Preconditions.checkState(this.type.hasValue(), "no value returned");
        return this.returnValue;
    }

    /**
     * Get the exception thrown during the snippet's execution, if any.
     *
     * <p>
     * Exceptions are only possible for snippets of type {@link Snippet.Kind#EXPRESSION}, {@link Snippet.Kind#STATEMENT},
     * and {@link Snippet.Kind#VAR}.
     *
     * @return exception thrown during snippet execution, or empty if no exception was thrown
     *  or the snippet has a type that does not immediately execute
     */
    public Optional<Throwable> exception() {
        return this.exception;
    }

    /**
     * Get the compiler errors that caused compilation to fail, if any.
     *
     * @return compilation errors, or empty if there were no compilation errors
     */
    public Optional<List<CompilerError>> compilerErrors() {
        return this.compilerErrors;
    }

// Type

    /**
     * The type of {@link SnippetOutcome}.
     */
    public enum Type {

        /**
         * The snippet failed due to one or more compiler errors.
         *
         * <p>
         * Use {@link SnippetOutcome#compilerErrors} to retrieve the error(s).
         */
        COMPILER_ERRORS,

        /**
         * The snippet failed due to throwing an exception during execution.
         *
         * <p>
         * Use {@link SnippetOutcome#exception} to retrieve the exception thrown.
         */
        EXCEPTION_THROWN,

        /**
         * The snippet was compiled and executed successfully and there is no associated return value
         * because the snippet was not of type {@link Snippet.Kind#EXPRESSION} or {@link Snippet.Kind#VAR}.
         */
        SUCCESSFUL_NO_VALUE(true, false),

        /**
         * The snippet was compiled and executed successfully and returned a value. The snippet is of
         * type {@link Snippet.Kind#EXPRESSION} or {@link Snippet.Kind#VAR}.
         *
         * <p>
         * Use {@link SnippetOutcome#returnValue} to retrieve the return value.
         */
        SUCCESSFUL_WITH_VALUE(true, true);

        private boolean successful;
        private boolean hasValue;

        Type() {
            this(false, false);
        }

        Type(boolean successful, boolean hasValue) {
            this.successful = successful;
            this.hasValue = hasValue;
        }

        /**
         * Determine whether this outcome type represents a successful outcome.
         *
         * @return true for {@link #SUCCESSFUL_WITH_VALUE} and {@link #SUCCESSFUL_WITH_VALUE}, false otherwise
         */
        public boolean isSuccessful() {
            return this.successful;
        }

        /**
         * Determine whether this outcome type implies that there was a value returned.
         *
         * @return true for {@link #SUCCESSFUL_WITH_VALUE}, false otherwise
         */
        public boolean hasValue() {
            return this.hasValue;
        }
    }
}
