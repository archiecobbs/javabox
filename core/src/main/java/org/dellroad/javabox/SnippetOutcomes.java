
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import java.util.List;

import jdk.jshell.Snippet;

final class SnippetOutcomes {

    private SnippetOutcomes() {
    }

    abstract static sealed class AbstractSnippetOutcome implements SnippetOutcome {

        private final JavaBox box;
        private final String source;

        AbstractSnippetOutcome(JavaBox box, String source) {
            this.box = box;
            this.source = source;
        }

        @Override
        public JavaBox box() {
            return this.box;
        }

        @Override
        public String source() {
            return this.source;
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName();
        }
    }

    abstract static sealed class AbstractHasSnippet extends AbstractSnippetOutcome implements SnippetOutcome.HasSnippet {

        private final Snippet snippet;

        AbstractHasSnippet(JavaBox box, Snippet snippet) {
            super(box, snippet.source());
            this.snippet = snippet;
        }

        @Override
        public Snippet snippet() {
            return this.snippet;
        }
    }

    abstract static sealed class AbstractCompilerErrors extends AbstractSnippetOutcome implements SnippetOutcome.CompilerErrors {

        private final List<CompilerError> compilerErrors;

        AbstractCompilerErrors(JavaBox box, String source, List<CompilerError> compilerErrors) {
            super(box, source);
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

    static final class CompilerSyntaxErrors extends AbstractCompilerErrors implements SnippetOutcome.CompilerSyntaxErrors {

        CompilerSyntaxErrors(JavaBox box, String source, List<CompilerError> compilerErrors) {
            super(box, source, compilerErrors);
        }
    }

    static final class CompilerSemanticErrors extends AbstractCompilerErrors implements SnippetOutcome.CompilerSemanticErrors {

        private final Snippet snippet;

        CompilerSemanticErrors(JavaBox box, Snippet snippet, List<CompilerError> compilerErrors) {
            super(box, snippet.source(), compilerErrors);
            this.snippet = snippet;
        }

        @Override
        public Snippet snippet() {
            return this.snippet;
        }
    }

    static final class ControlViolation extends AbstractSnippetOutcome implements SnippetOutcome.ControlViolation {

        private final ControlViolationException exception;

        ControlViolation(JavaBox box, String source, ControlViolationException exception) {
            super(box, source);
            this.exception = exception;
        }

        @Override
        public ControlViolationException exception() {
            return this.exception;
        }

        @Override
        public String toString() {
            return String.format("%s: %s", super.toString(), this.exception.getMessage());
        }
    }

    static final class UnresolvedReferences extends AbstractHasSnippet implements SnippetOutcome.UnresolvedReferences {

        UnresolvedReferences(JavaBox box, Snippet snippet) {
            super(box, snippet);
        }
    }

    static final class Overwritten extends AbstractHasSnippet implements SnippetOutcome.Overwritten {

        Overwritten(JavaBox box, Snippet snippet) {
            super(box, snippet);
        }
    }

    static final class ExceptionThrown extends AbstractHasSnippet implements SnippetOutcome.ExceptionThrown {

        private final Throwable exception;

        ExceptionThrown(JavaBox box, Snippet snippet, Throwable exception) {
            super(box, snippet);
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

    abstract static sealed class AbstractSuccessful extends AbstractHasSnippet implements SnippetOutcome.Successful {

        AbstractSuccessful(JavaBox box, Snippet snippet) {
            super(box, snippet);
        }
    }

    static final class SuccessfulNoValue extends AbstractSuccessful implements SnippetOutcome.SuccessfulNoValue {

        SuccessfulNoValue(JavaBox box, Snippet snippet) {
            super(box, snippet);
        }
    }

    static final class SuccessfulWithValue extends AbstractSuccessful implements SnippetOutcome.SuccessfulWithValue {

        private final Object returnValue;

        SuccessfulWithValue(JavaBox box, Snippet snippet, Object returnValue) {
            super(box, snippet);
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
