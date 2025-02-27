
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import com.google.common.base.Preconditions;

import java.io.Closeable;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.jshell.DeclarationSnippet;
import jdk.jshell.Diag;
import jdk.jshell.EvalException;
import jdk.jshell.JShell;
import jdk.jshell.JShellException;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;
import jdk.jshell.SourceCodeAnalysis;
import jdk.jshell.SourceCodeAnalysis.CompletionInfo;
import jdk.jshell.spi.ExecutionControl.ClassBytecodes;

import org.dellroad.stuff.java.ThreadLocalHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A scripting container for scripts written in the Java language.
 */
public class JavaBox implements Closeable {

    private static final ThreadLocalHolder<JavaBox> CURRENT = new ThreadLocalHolder<>();
    private static final int APPLY_WAIT_TIME_MILLIS = 100;

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final Config config;
    private final JShell jshell;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicReference<Outcome> currentOutcome = new AtomicReference<>();

    private boolean applying;
    private boolean closed;

    private volatile boolean applyCanceled;

    /**
     * Constructor.
     *
     * @param config configuration
     * @throws IllegalArgumentException if {@code config} is null
     */
    public JavaBox(Config config) {
        Preconditions.checkArgument(config != null, "null config");
        this.config = config;
        this.jshell = CURRENT.invoke(this, () -> this.config.jshellBuilder().build());
    }

    /**
     * Apply the given script to this instance.
     *
     * <p>
     * If the current thread is interrupted during execution, then the script is stopped and {@link InterruptedException}
     * is thrown; if that happens, the script may have not started yet, partially completed, or completed.
     *
     * @param source the script to apply
     * @return the value returned by the Java expression at the end of the script, if any, otherwise null
     * @throws AccessViolation if the script attempts to do something disallowed by the currently configured {@link ScriptFilter}
     * @throws JavaBoxException if script parsing or execution fails
     * @throws InterruptedException if the current thread is interrupted
     * @throws IllegalStateException if this instance is closed
     * @throws IllegalArgumentException if {@code source} is null
     */
    public Script apply(String source) throws InterruptedException {

        // Sanity check
        Preconditions.checkArgument(source != null, "null source");

        // Allow only one apply operation at a time
        synchronized (this) {
            Preconditions.checkState(!this.closed, "instance is closed");
            while (this.applying) {
                this.wait();
            }
            this.applying = true;
            this.applyCanceled = false;
        }
        try {

            // Enqueue an apply task and wait for it to complete
            final Future<Script> future = this.executor.submit(() -> this.doApply(source));
            while (true) {

                // Get result from doApply()
                final Script script;
                try {
                    script = future.get(APPLY_WAIT_TIME_MILLIS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    if (this.applyCanceled)
                        this.jshell.stop();         // keep trying in case we missed it
                    continue;
                } catch (CancellationException e) {
                    Preconditions.checkState(this.applyCanceled, "internal error: not canceled");
                    throw new InterruptedException();
                } catch (ExecutionException e) {
                    final Throwable error = e.getCause();
                    throw new JavaBoxException(error.getMessage(), error);
                } catch (InterruptedException e) {
                    if (!this.applyCanceled) {
                        this.jshell.stop();
                        future.cancel(false);
                        this.applyCanceled = true;
                    }
                    continue;
                }

                // If we were canceled, disregard script return value
                if (this.applyCanceled)
                    throw new InterruptedException();

                // Done
                Preconditions.checkState(script != null, "internal error: null script");
                return script;
            }

        } finally {
            synchronized (this) {
                this.applying = false;
                this.applyCanceled = false;
                this.notifyAll();
            }
        }
    }

    @SuppressWarnings("fallthrough")
    private synchronized Script doApply(String wholeSource) {

        // Sanity check
        Preconditions.checkState(!this.closed && this.applying, "internal error");

        // Break source into individual source snippets
        final LinkedHashMap<String, LineAndColumn> sources = new LinkedHashMap<>();
        final SourceCodeAnalysis sourceCodeAnalysis = this.jshell.sourceCodeAnalysis();
        final LineAndColumn lineCol = new LineAndColumn();
        for (String remain = wholeSource; !remain.isEmpty(); ) {

            // Check for interrupt
            if (this.applyCanceled)
                return null;

            // Scrape off the next source snippet
            final CompletionInfo info = sourceCodeAnalysis.analyzeCompletion(remain);
            String source = info.source();
            if (this.log.isDebugEnabled())
                this.log.debug("apply: {}: completeness={} source=\"{}\"", lineCol, info.completeness(), source);
            boolean semicolonAdded = false;
            switch (info.completeness()) {
            case COMPLETE_WITH_SEMI:
                semicolonAdded = true;
                // FALLTHROUGH
            case COMPLETE:
            case EMPTY:
                sources.put(info.source(), lineCol);
                break;
            case CONSIDERED_INCOMPLETE:
            case DEFINITELY_INCOMPLETE:
                throw new JavaBoxException(this.toErrorMessage(lineCol, "incomplete trailing statement"));
            case UNKNOWN:
                final List<SnippetEvent> eval = this.jshell.eval(source);
                final SnippetEvent event;
                if (eval.size() != 1 || !Snippet.Status.REJECTED.equals((event = eval.get(0)).status()))
                    throw new JavaBoxException("internal error: " + eval);
                throw new ScriptCompilationException(
                  this.toErrorMessage(source, lineCol, this.jshell.diagnostics(event.snippet())));
            default:
                throw new JavaBoxException("internal error");
            }

            // Advance line & column
            if (semicolonAdded) {
                assert !source.isEmpty() && source.charAt(source.length() - 1) == ';';
                source = source.substring(0, source.length() - 1);
            }
            lineCol.advance(source);
            remain = info.remaining();
        }

        // So far so good; now actually evaluate each source snippet
        lineCol.reset();
        final HashSet<Snippet> snippets = new HashSet<>();
        Outcome outcome = Outcome.empty();
        for (Map.Entry<String, LineAndColumn> entry : sources.entrySet()) {
            final String source = entry.getKey();

            // Check for interrupt
            if (this.applyCanceled)
                return null;

            // Execute snippet
            final List<SnippetEvent> events;
            try {
                events = this.jshell.eval(source);
            } finally {
                if (this.log.isDebugEnabled())
                    this.log.debug("3: this.currentOutcome={}", this.currentOutcome.get());
                outcome = this.currentOutcome.get();
                this.currentOutcome.set(null);
                if (this.log.isDebugEnabled())
                    this.log.debug("4: this.currentOutcome={}", this.currentOutcome.get());
            }

            // Find the snippet event that corresponds to this new snippet; there should be exactly one
            SnippetEvent event = null;
            for (SnippetEvent someEvent : events) {
                if (someEvent.causeSnippet() == null) {
                    if (event != null)
                        throw new JavaBoxException("internal error: multiple events: " + someEvent + ", " + event);
                    event = someEvent;
                }
            }
            if (event == null)
                throw new JavaBoxException("internal error: no event: " + events);

            // Associate the snippet with the script
            final Snippet snippet = event.snippet();
            snippets.add(snippet);

            // Check snippet status
            switch (event.status()) {
            case RECOVERABLE_DEFINED:
            case RECOVERABLE_NOT_DEFINED:
                break;
            case VALID:
                final JShellException eventException = event.exception();
                Optional.ofNullable(outcome)
                  .map(Outcome::error)
                  .or(() -> Optional.ofNullable(eventException))
                  .map(EvalException.class::cast)
                  .ifPresent(e -> {
                    throw new ScriptExecutionException(String.format(
                      "%s: %s", e.getExceptionClassName(), e.getMessage()), e).initCause(e);
                  });
                break;
            case REJECTED:
                throw new ScriptCompilationException(
                  this.toErrorMessage(source, entry.getValue(), this.jshell.diagnostics(snippet)));
            default:
                throw new JavaBoxException("internal error: " + event);
            }
        }

        // Return completed script
        final Object returnValue = Optional.ofNullable(outcome).map(Outcome::result).orElse(null);
        return new Script(this, wholeSource, snippets, returnValue);
    }

    private String toErrorMessage(String source, LineAndColumn lineCol, Stream<Diag> diagnostics) {
        return diagnostics
          .sorted(Comparator.comparingLong(Diag::getPosition))
          .map(diag -> {
            final LineAndColumn diagLineCol = lineCol.dup();
            diagLineCol.advance(source.substring(0, (int)diag.getPosition()));
            return toErrorMessage(diagLineCol, diag.getMessage(Locale.ROOT));
          })
          .collect(Collectors.joining("\n"));
    }

    private String toErrorMessage(LineAndColumn lineCol, String error) {
        return String.format("%d:%d: %s", lineCol.lineNumber(), lineCol.columNumber(), error);
    }

// Package Access Methods

    synchronized Set<String> unresolvedDependencies(Set<Snippet> snippets) {
        Preconditions.checkState(!this.closed, "instance is closed");
        Preconditions.checkArgument(snippets != null, "null snippets");
        return snippets.stream()
          .filter(DeclarationSnippet.class::isInstance)
          .map(DeclarationSnippet.class::cast)
          .flatMap(this.jshell::unresolvedDependencies)
          .collect(Collectors.toSet());
    }

    synchronized List<ClassBytecodes> filter(ClassBytecodes classfile) {
        Preconditions.checkState(!this.closed, "instance is closed");
        Preconditions.checkArgument(classfile != null, "null classfile");
        return this.config.scriptFilter().filter(classfile);
    }

// Context

    // Callback from JavaBoxExecutionControl constructor
    static JavaBox getCurrent() {
        return CURRENT.require();
    }

    protected void enterContext() {
        if (this.log.isDebugEnabled())
            this.log.debug("enterContext()", new Throwable("HERE"));
        this.currentOutcome.set(null);
        if (this.log.isDebugEnabled())
            this.log.debug("1: this.currentOutcome={}", this.currentOutcome.get());
    }

    protected void leaveContext(Object result, Throwable error) {
        if (this.log.isDebugEnabled())
            this.log.debug("leaveContext(): result={} error={}", result, error, new Throwable("HERE"));
        this.currentOutcome.set(new Outcome(result, error));
        if (this.log.isDebugEnabled())
            this.log.debug("2: this.currentOutcome={}", this.currentOutcome.get());
    }

// Properties

    /**
     * Get the {@link Config} associated with this instance.
     *
     * @return instance config
     */
    public Config getConfig() {
        return this.config;
    }

// Lifecycle

    /**
     * Determine if this instance is closed.
     *
     * @return true if this instance is closed, otherwise false
     */
    public synchronized boolean isClosed() {
        return this.closed;
    }

    /**
     * Close this instance.
     */
    @Override
    public synchronized void close() {
        if (this.closed)
            return;
        boolean interrupted = false;
        while (this.applying) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        this.closed = true;
        this.executor.shutdown();
        this.jshell.close();
        if (interrupted)
            Thread.currentThread().interrupt();
    }

// LineAndColumn

    private record LineAndColumn(AtomicInteger lineNumber, AtomicInteger columNumber) {

        LineAndColumn() {
            this(new AtomicInteger(1), new AtomicInteger(1));
        }

        void advance(String text) {
            text.codePoints().forEach(ch -> {
                if (ch == '\n') {
                    lineNumber().incrementAndGet();
                    columNumber().set(1);
                } else
                    columNumber().incrementAndGet();
            });
        }

        void reset() {
            lineNumber().set(1);
            columNumber().set(1);
        }

        public LineAndColumn dup() {
            return new LineAndColumn(lineNumber(), columNumber());
        }
    }

// Outcome

    private record Outcome(Object result, Throwable error) {

        static Outcome empty() {
            return new Outcome(null, null);
        }
    }
}
