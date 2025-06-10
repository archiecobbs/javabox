
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import java.util.List;

import jdk.jshell.Snippet;

final class SnippetOutcomes {

    private SnippetOutcomes() {
    }

// Abstract classes

    abstract static sealed class AbstractSnippetOutcome implements SnippetOutcome {

        private final JavaBox box;
        private final LineAndColumn offset;
        private final Snippet snippet;

        AbstractSnippetOutcome(JavaBox box, LineAndColumn offset, Snippet snippet) {
            this.box = box;
            this.offset = offset;
            this.snippet = snippet;
        }

        @Override
        public JavaBox box() {
            return this.box;
        }

        @Override
        public LineAndColumn offset() {
            return this.offset;
        }

        @Override
        public Snippet snippet() {
            return this.snippet;
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName();
        }
    }

    abstract static sealed class AbstractCompilerErrors extends AbstractSnippetOutcome implements SnippetOutcome.CompilerErrors {

        private final List<CompilerError> compilerErrors;

        AbstractCompilerErrors(JavaBox box, LineAndColumn offset, Snippet snippet, List<CompilerError> compilerErrors) {
            super(box, offset, snippet);
            this.compilerErrors = compilerErrors;
        }

        @Override
        public List<CompilerError> compilerErrors() {
            return this.compilerErrors;
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append(super.toString()).append(':');
            this.compilerErrors.stream()
              .map(e -> "\n  " + e)
              .forEach(buf::append);
            return buf.toString();
        }
    }

    abstract static sealed class AbstractHasException<T extends Throwable> extends AbstractSnippetOutcome
      implements SnippetOutcome.HasException<T> {

        private final T exception;

        AbstractHasException(JavaBox box, LineAndColumn offset, Snippet snippet, T exception) {
            super(box, offset, snippet);
            this.exception = exception;
        }

        @Override
        public T exception() {
            return this.exception;
        }

        @Override
        public String toString() {
            return String.format("%s: %s", super.toString(), this.exception.getMessage());
        }
    }

// Concrete classes

    static final class Skipped extends AbstractSnippetOutcome implements SnippetOutcome.Skipped {

        Skipped(JavaBox box, LineAndColumn offset, Snippet snippet) {
            super(box, offset, snippet);
        }
    }

    static final class CompilerSyntaxErrors extends AbstractCompilerErrors implements SnippetOutcome.CompilerSyntaxErrors {

        CompilerSyntaxErrors(JavaBox box, LineAndColumn offset, Snippet snippet, List<CompilerError> compilerErrors) {
            super(box, offset, snippet, compilerErrors);
        }
    }

    static final class CompilerSemanticErrors extends AbstractCompilerErrors implements SnippetOutcome.CompilerSemanticErrors {

        private final Snippet snippet;

        CompilerSemanticErrors(JavaBox box, LineAndColumn offset, Snippet snippet, List<CompilerError> compilerErrors) {
            super(box, offset, snippet, compilerErrors);
            this.snippet = snippet;
        }

        @Override
        public Snippet snippet() {
            return this.snippet;
        }
    }

    static final class ControlViolation extends AbstractHasException<ControlViolationException>
      implements SnippetOutcome.ControlViolation {

        ControlViolation(JavaBox box, LineAndColumn offset, Snippet snippet, ControlViolationException exception) {
            super(box, offset, snippet, exception);
        }
    }

    static final class ValidationFailure extends AbstractHasException<SnippetValidationException>
      implements SnippetOutcome.ValidationFailure {

        ValidationFailure(JavaBox box, LineAndColumn offset, Snippet snippet, SnippetValidationException exception) {
            super(box, offset, snippet, exception);
        }
    }

    static final class UnresolvedReferences extends AbstractSnippetOutcome implements SnippetOutcome.UnresolvedReferences {

        UnresolvedReferences(JavaBox box, LineAndColumn offset, Snippet snippet) {
            super(box, offset, snippet);
        }
    }

    static final class Overwritten extends AbstractSnippetOutcome implements SnippetOutcome.Overwritten {

        Overwritten(JavaBox box, LineAndColumn offset, Snippet snippet) {
            super(box, offset, snippet);
        }
    }

    static final class Suspended extends AbstractSnippetOutcome implements SnippetOutcome.Suspended {

        private final Object parameter;

        Suspended(JavaBox box, LineAndColumn offset, Snippet snippet, Object parameter) {
            super(box, offset, snippet);
            this.parameter = parameter;
        }

        @Override
        public Object parameter() {
            return this.parameter;
        }

        @Override
        public String toString() {
            return String.format("%s: %s", super.toString(), this.parameter);
        }
    }

    static final class Interrupted extends AbstractSnippetOutcome implements SnippetOutcome.Interrupted {

        Interrupted(JavaBox box, LineAndColumn offset, Snippet snippet) {
            super(box, offset, snippet);
        }
    }

    static final class ExceptionThrown extends AbstractSnippetOutcome implements SnippetOutcome.ExceptionThrown {

        private final Throwable exception;

        ExceptionThrown(JavaBox box, LineAndColumn offset, Snippet snippet, Throwable exception) {
            super(box, offset, snippet);
            this.exception = exception;
        }

        @Override
        public Throwable exception() {
            return this.exception;
        }

        @Override
        public String toString() {
            return String.format("%s: %s", super.toString(), this.exception);
        }
    }

    abstract static sealed class AbstractSuccessful extends AbstractSnippetOutcome implements SnippetOutcome.Successful {

        AbstractSuccessful(JavaBox box, LineAndColumn offset, Snippet snippet) {
            super(box, offset, snippet);
        }
    }

    static final class SuccessfulNoValue extends AbstractSuccessful implements SnippetOutcome.SuccessfulNoValue {

        SuccessfulNoValue(JavaBox box, LineAndColumn offset, Snippet snippet) {
            super(box, offset, snippet);
        }
    }

    static final class SuccessfulWithValue extends AbstractSuccessful implements SnippetOutcome.SuccessfulWithValue {

        private final Object returnValue;

        SuccessfulWithValue(JavaBox box, LineAndColumn offset, Snippet snippet, Object returnValue) {
            super(box, offset, snippet);
            this.returnValue = returnValue;
        }

        @Override
        public Object returnValue() {
            return this.returnValue;
        }

        @Override
        public String toString() {
            return String.format("%s: %s", super.toString(), this.returnValue);
        }
    }
}
