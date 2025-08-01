
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.jshell.Diag;
import jdk.jshell.Snippet;

final class SnippetOutcomes {

    private SnippetOutcomes() {
    }

// Abstract classes

    abstract static sealed class AbstractSnippetOutcome implements SnippetOutcome {

        private final JavaBox box;
        private final String scriptSource;
        private final int index;
        private final int offset;
        private final Snippet snippet;
        private final List<Diag> diagnostics;

        AbstractSnippetOutcome(JavaBox box, JavaBox.SnippetInfo info) {
            this.box = box;
            this.scriptSource = info.scriptSource();
            this.index = info.index();
            this.offset = info.offset();
            this.snippet = info.snippet().get();
            this.diagnostics = info.diagnostics();
        }

        @Override
        public JavaBox box() {
            return this.box;
        }

        @Override
        public String scriptSource() {
            return this.scriptSource;
        }

        @Override
        public int index() {
            return this.index;
        }

        @Override
        public int offset() {
            return this.offset;
        }

        @Override
        public Snippet snippet() {
            return this.snippet;
        }

        @Override
        public List<Diag> diagnostics() {
            return this.diagnostics;
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName();
        }
    }

    abstract static sealed class AbstractHasException<T extends Throwable> extends AbstractSnippetOutcome
      implements SnippetOutcome.HasException<T> {

        private final T exception;

        AbstractHasException(JavaBox box, JavaBox.SnippetInfo info, T exception) {
            super(box, info);
            this.exception = exception;
        }

        @Override
        public T exception() {
            return this.exception;
        }

        @Override
        public String toString() {
            return String.format("%s: %s", super.toString(), this.exception);
        }
    }

// Concrete classes

    static final class CompilerError extends AbstractSnippetOutcome implements SnippetOutcome.CompilerError {

        CompilerError(JavaBox box, JavaBox.SnippetInfo info) {
            super(box, info);
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append(super.toString())
              .append(':')
              .append(this.diagnostics().stream()
                .map(d -> d.getMessage(null))
                .collect(Collectors.joining("\n")));
            return buf.toString();
        }
    }

    static final class ControlViolation extends AbstractHasException<ControlViolationException>
      implements SnippetOutcome.ControlViolation {

        ControlViolation(JavaBox box, JavaBox.SnippetInfo info, ControlViolationException exception) {
            super(box, info, exception);
        }
    }

    static final class UnresolvedReferences extends AbstractSnippetOutcome implements SnippetOutcome.UnresolvedReferences {

        private final List<String> references;

        UnresolvedReferences(JavaBox box, JavaBox.SnippetInfo info, Stream<String> references) {
            super(box, info);
            this.references = references.collect(Collectors.toList());
        }

        @Override
        public List<String> references() {
            return this.references;
        }

        @Override
        public String toString() {
            return super.toString() + ": " + this.references.stream().collect(Collectors.joining(", "));
        }
    }

    static final class Overwritten extends AbstractSnippetOutcome implements SnippetOutcome.Overwritten {

        Overwritten(JavaBox box, JavaBox.SnippetInfo info) {
            super(box, info);
        }
    }

    static final class Suspended extends AbstractHasException<Throwable> implements SnippetOutcome.Suspended {

        private final Object parameter;

        Suspended(JavaBox box, JavaBox.SnippetInfo info, Throwable exception, Object parameter) {
            super(box, info, exception);
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

    static final class Interrupted extends AbstractHasException<ThreadDeath> implements SnippetOutcome.Interrupted {

        Interrupted(JavaBox box, JavaBox.SnippetInfo info, ThreadDeath exception) {
            super(box, info, exception);
        }
    }

    static final class Skipped extends AbstractSnippetOutcome implements SnippetOutcome.Skipped {

        Skipped(JavaBox box, JavaBox.SnippetInfo info) {
            super(box, info);
        }
    }

    static final class ExceptionThrown extends AbstractHasException<Throwable> implements SnippetOutcome.ExceptionThrown {

        ExceptionThrown(JavaBox box, JavaBox.SnippetInfo info, Throwable exception) {
            super(box, info, exception);
        }
    }

    abstract static sealed class AbstractSuccessful extends AbstractSnippetOutcome implements SnippetOutcome.Successful {

        AbstractSuccessful(JavaBox box, JavaBox.SnippetInfo info) {
            super(box, info);
        }
    }

    static final class SuccessfulNoValue extends AbstractSuccessful implements SnippetOutcome.SuccessfulNoValue {

        SuccessfulNoValue(JavaBox box, JavaBox.SnippetInfo info) {
            super(box, info);
        }
    }

    static final class SuccessfulWithValue extends AbstractSuccessful implements SnippetOutcome.SuccessfulWithValue {

        private final Object returnValue;

        SuccessfulWithValue(JavaBox box, JavaBox.SnippetInfo info, Object returnValue) {
            super(box, info);
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
