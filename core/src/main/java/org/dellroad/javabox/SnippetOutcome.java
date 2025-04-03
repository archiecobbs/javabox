
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

    static SnippetOutcome controlViolation(JavaBox box, String source, ControlViolationException exception) {
        return new SnippetOutcome(box, Type.CONTROL_VIOLATION, source, Optional.empty(),
          Optional.empty(), Optional.of(exception), Optional.empty());
    }

    static SnippetOutcome exceptionThrown(JavaBox box, Snippet snippet, Throwable exception) {
        return new SnippetOutcome(box, Type.EXCEPTION_THROWN, snippet.source(), Optional.of(snippet),
          Optional.empty(), Optional.of(exception), Optional.empty());
    }

    static SnippetOutcome unresolvedReferences(JavaBox box, Snippet snippet) {
        return new SnippetOutcome(box, Type.UNRESOLVED_REFERENCES, snippet.source(), Optional.of(snippet),
          Optional.empty(), Optional.empty(), Optional.empty());
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

    SnippetOutcome toOverwritten() {
        Preconditions.checkState(this.snippet.isPresent());
        return new SnippetOutcome(this.box, Type.OVERWRITTEN, this.source,
          this.snippet, this.returnValue, this.exception, this.compilerErrors);
    }

    SnippetOutcome toValid() {
        Preconditions.checkState(this.snippet.isPresent() && this.type == Type.UNRESOLVED_REFERENCES);
        return new SnippetOutcome(this.box, Type.SUCCESSFUL_NO_VALUE, this.source,
          this.snippet, this.returnValue, this.exception, this.compilerErrors);
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

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(type.name());
        switch (type) {
        case COMPILER_ERRORS:
            buf.append(':');
            this.compilerErrors.get().stream()
              .map(e -> "\n  " + e)
              .forEach(buf::append);
            break;
        case EXCEPTION_THROWN:
            buf.append(": ").append(this.exception.get());
            break;
        case SUCCESSFUL_WITH_VALUE:
            buf.append(": ").append(this.returnValue);
            break;
        default:
            break;
        }
        return buf.toString();
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
        COMPILER_ERRORS(true, false, false),

        /**
         * The snippet failed because it tried to do something disallowed by
         * a configured {@link Control}.
         *
         * <p>
         * Use {@link SnippetOutcome#exception} to retrieve the {@link ControlViolationException}.
         */
        CONTROL_VIOLATION(true, false, false),

        /**
         * The snippet failed due to throwing an exception during execution.
         *
         * <p>
         * Use {@link SnippetOutcome#exception} to retrieve the exception thrown.
         */
        EXCEPTION_THROWN(true, false, false),

        /**
         * The snippet was successfully compiled but contains one or more unresolved references
         * that did not become resolved by any of the subsequent snippets in the same script.
         */
        UNRESOLVED_REFERENCES(false, false, false),

        /**
         * The snippet was successfully compiled but then got overwritten by a subsequent snippet
         * in the same script.
         *
         * <p>
         * This outcome is only possible after a subsequent snippet has been processed.
         */
        OVERWRITTEN(false, false, false),

        /**
         * The snippet was compiled and executed successfully and there is no associated return value
         * because the snippet was not of type {@link Snippet.Kind#EXPRESSION} or {@link Snippet.Kind#VAR}.
         */
        SUCCESSFUL_NO_VALUE(false, true, false),

        /**
         * The snippet was compiled and executed successfully and returned a value. The snippet is of
         * type {@link Snippet.Kind#EXPRESSION} or {@link Snippet.Kind#VAR}.
         *
         * <p>
         * Use {@link SnippetOutcome#returnValue} to retrieve the return value.
         */
        SUCCESSFUL_WITH_VALUE(false, true, true);

        private boolean haltsScript;
        private boolean successful;
        private boolean hasValue;

        Type(boolean haltsScript, boolean successful, boolean hasValue) {
            this.haltsScript = haltsScript;
            this.successful = successful;
            this.hasValue = hasValue;
        }

        /**
         * Determine whether this outcome will cause a partially executed script to stop execution.
         *
         * @return true for {@link #COMPILER_ERRORS}, {@link #CONTROL_VIOLATION} and {@link #EXCEPTION_THROWN},
         *  false otherwise
         */
        public boolean isHaltsScript() {
            return this.haltsScript;
        }

        /**
         * Determine whether this outcome type represents a successful outcome.
         *
         * @return true for {@link #SUCCESSFUL_NO_VALUE} and {@link #SUCCESSFUL_WITH_VALUE}, false otherwise
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
