
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import com.google.common.base.Preconditions;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import jdk.jshell.Diag;
import jdk.jshell.JShell;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;
import jdk.jshell.SourceCodeAnalysis;
import jdk.jshell.SourceCodeAnalysis.CompletionInfo;
import jdk.jshell.spi.ExecutionControl.ClassBytecodes;

import org.dellroad.javabox.Control.ContainerContext;
import org.dellroad.javabox.Control.ExecutionContext;
import org.dellroad.stuff.java.ThreadLocalHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A scripting container for scripts written in the Java language.
 */
public class JavaBox implements Closeable {

    /**
     * The package name reserved for {@link JavaBox} classes use within the container.
     */
    public static final String JAVABOX_RESERVED_PACKAGE = "JAVABOX";

    /**
     * The package name reserved for use by JShell within the container.
     */
    public static final String JSHELL_RESERVED_PACKAGE = "REPL";

    private static final String THREAD_NAME_PREFIX = "JavaBox";
    private static final AtomicLong THREAD_NAME_INDEX = new AtomicLong();
    private static final ThreadLocalHolder<JavaBox> CURRENT = new ThreadLocalHolder<>();
    private static final ThreadLocal<ExecutionInfo> EXECUTION_INFO = new ThreadLocal<>();
    private static final int EXECUTE_WAIT_TIME_MILLIS = 100;

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final Config config;
    private final ArrayList<ContainerContext> containerContexts = new ArrayList<>();
    private final AtomicReference<ExecResult> currentExecResult = new AtomicReference<>();

    private JShell jshell;
    private ExecutorService executor;

    private boolean initialized;
    private boolean closed;

    // This is the thread in execute() that is waiting on the Future that reports the outcome of doExecute()
    private volatile Thread executeThread;

    // This flag is how "executeThread" notifies the thread in doExecute() that it was interrupted
    private volatile boolean executionInterrupted;

// Constructor

    /**
     * Constructor.
     *
     * <p>
     * Instances must be {@link initialize()}d before use.
     *
     * @param config configuration
     * @throws IllegalArgumentException if {@code config} is null
     */
    public JavaBox(Config config) {
        Preconditions.checkArgument(config != null, "null config");
        this.config = config;
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

    /**
     * Determine if this instance is initialized.
     *
     * @return true if this instance is initialized, otherwise false
     */
    public synchronized boolean isInitialized() {
        return this.initialized;
    }

    /**
     * Determine if this instance is closed.
     *
     * @return true if this instance is closed, otherwise false
     */
    public synchronized boolean isClosed() {
        return this.closed;
    }

    /**
     * Get the {@link JShell} instanced associated with this container.
     *
     * @return this container's {@link JShell}
     * @throws IllegalStateException if this instance is not yet initialized
     */
    public synchronized JShell getJShell() {
        Preconditions.checkState(this.initialized, "not initialized");
        return this.jshell;
    }

// Lifecycle

    /**
     * Initialize this instance.
     *
     * @throws IllegalStateException if this instance is already initialized or closed
     */
    public synchronized void initialize() {

        // Sanity check
        Preconditions.checkState(!this.initialized, "already initialized");
        Preconditions.checkState(!this.closed, "closed");
        try {

            // Create our JShell instance
            assert this.jshell == null;
            this.jshell = CURRENT.invoke(this, () -> this.config.jshellBuilder().build());

            // Create our executor
            this.executor = Executors.newSingleThreadExecutor();

            // Initialize control contexts
            for (Control control : this.config.controls())
                this.containerContexts.add(new ContainerContext(this, control, control.initialize(this)));

        } catch (RuntimeException | Error e) {
            this.shutdown();
            throw e;
        }

        // Done
        this.initialized = true;
    }

    /**
     * Close this instance.
     *
     * <p>
     * If this instance is already closed, or was never initialized, this method does nothing.
     */
    @Override
    public synchronized void close() {
        if (this.closed)
            return;
        this.closed = true;
        this.shutdown();
    }

    private synchronized void shutdown() {
        boolean wasInterrupted = false;
        this.interrupt();
        while (this.executeThread != null) {
            try {
                this.wait(EXECUTE_WAIT_TIME_MILLIS);
            } catch (InterruptedException e) {
                wasInterrupted = true;
            }
        }
        if (this.executor != null) {
            this.executor.shutdown();
            this.executor = null;
        }
        if (this.jshell != null) {
            this.jshell.close();
            this.jshell = null;
        }
        while (!this.containerContexts.isEmpty()) {
            final ContainerContext context = this.containerContexts.remove(this.containerContexts.size() - 1);
            try {
                context.control().shutdown(context);
            } catch (Exception e) {
                this.log.warn("error closing {} context for {} (ignoring)", "container", context.control(), e);
            }
        }
        if (wasInterrupted)
            Thread.currentThread().interrupt();
    }

// Script Execution

    /**
     * Execute the given script in this container.
     *
     * <p>
     * The script is broken into individual snippets, which are executed one-at-a-time. Processing stops
     * if any snippet fails, otherwise after the last snippet has finished. The results of the execution
     * of the snippet that were processed are returned in the {@link ScriptResult}.
     *
     * <p>
     * This method is single threaded: if invoked simultaneously by two different threads, the second thread will
     * block until the first finishes.
     *
     * <p>
     * If the current thread is interrupted, then script execution will be interrupted and {@link InterruptedException}
     * thrown. The script may have not yet started, may have only partially completed, or may have fully completed.
     *
     * @param source the script to execute
     * @return result from successful script execution
     * @throws InterruptedException if the current thread is interrupted
     * @throws IllegalStateException if this instance is not initialized or closed
     * @throws IllegalArgumentException if {@code source} is null
     */
    public ScriptResult execute(String source) throws InterruptedException {

        // Sanity check
        Preconditions.checkArgument(source != null, "null source");

        // Snapshot these to avoid inconsistent synchronization warnings
        final ExecutorService executor0;
        final JShell jshell0;

        // Allow only one execute operation at a time
        synchronized (this) {
            Preconditions.checkState(this.initialized, "not initialized");
            Preconditions.checkState(!this.closed, "closed");
            while (this.executeThread != null)
                this.wait();
            this.executeThread = Thread.currentThread();
            this.executionInterrupted = false;
            executor0 = this.executor;
            jshell0 = this.jshell;
        }
        try {

            // Enqueue an execute task and wait for it to complete
            final Future<ScriptResult> future = executor0.submit(() -> this.doExecute(source));
            while (true) {

                // Get result from doExecute()
                final ScriptResult scriptResult;
                try {
                    scriptResult = future.get(EXECUTE_WAIT_TIME_MILLIS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    if (this.executionInterrupted)
                        jshell0.stop();             // keep trying in case we missed it
                    continue;
                } catch (CancellationException e) {
                    Preconditions.checkState(this.executionInterrupted, "internal error: not canceled");
                    throw new InterruptedException();
                } catch (ExecutionException e) {
                    final Throwable error = e.getCause();
                    throw new JavaBoxException("internal error: " + error, error);
                } catch (InterruptedException e) {
                    if (!this.executionInterrupted) {
                        jshell0.stop();
                        future.cancel(false);
                        this.executionInterrupted = true;
                    }
                    continue;
                }

                // If we were canceled, disregard script return value
                if (this.executionInterrupted)
                    throw new InterruptedException();

                // Done
                Preconditions.checkState(scriptResult != null, "internal error: null script");
                return scriptResult;
            }

        } finally {
            synchronized (this) {
                this.executeThread = null;
                this.executionInterrupted = false;
                this.notifyAll();
            }
        }
    }

    /**
     * Interrupt the execution of the script that is currently executing, if any.
     *
     * <p>
     * This method interrupts the thread currently running in {@link #execute execute()}, if any.
     *
     * @return true if script execution was interrupted, false if no execution was occurring
     */
    public synchronized boolean interrupt() {
        if (this.executeThread != null) {
            this.executeThread.interrupt();
            return true;
        }
        return false;
    }

    private synchronized ScriptResult doExecute(String source) throws Exception {

        // Sanity check
        Preconditions.checkState(this.initialized && !this.closed && this.executeThread != null, "internal error");

        // Break source into individual source snippets
        final SourceCodeAnalysis sourceCodeAnalysis = this.jshell.sourceCodeAnalysis();
        final LineAndColumn lineCol = new LineAndColumn();
        final List<SnippetOutcome> snippetOutcomes = new ArrayList<>();
    snippetLoop:
        for (String remain = source; !remain.isEmpty(); ) {

            // Check for interrupt of execute() thread
            if (this.executionInterrupted)
                return null;

            // Scrape off the next snippet from the script source
            final CompletionInfo info = sourceCodeAnalysis.analyzeCompletion(remain);
            String snippetSource = info.source();
            final LineAndColumn snippetLineCol = lineCol.dup();
//            if (this.log.isDebugEnabled())
//                this.log.debug("execute: {}: completeness={} source=\"{}\"", lineCol, info.completeness(), source);
            boolean semicolonAdded = false;
            switch (info.completeness()) {
            case CONSIDERED_INCOMPLETE:
            case DEFINITELY_INCOMPLETE:
                snippetOutcomes.add(SnippetOutcome.compilerErrors(this, snippetSource,
                  Collections.singletonList(lineCol.toError("incomplete trailing statement"))));
                break snippetLoop;
            case UNKNOWN:
                final List<SnippetEvent> eval = this.jshell.eval(snippetSource);
                final SnippetEvent event;
                if (eval.size() != 1 || !Snippet.Status.REJECTED.equals((event = eval.get(0)).status()))
                    throw new JavaBoxException("internal error: " + eval);
                snippetOutcomes.add(SnippetOutcome.compilerErrors(this, snippetSource,
                  this.toErrors(snippetSource, snippetLineCol, this.jshell.diagnostics(event.snippet()))));
                break snippetLoop;
            case COMPLETE_WITH_SEMI:
                semicolonAdded = true;
                break;
            case COMPLETE:
            case EMPTY:
                break;
            default:
                throw new JavaBoxException("internal error");
            }

            // Execute the snippet
            final List<SnippetEvent> events;
            final ExecResult execResult;
            try {
                events = this.jshell.eval(snippetSource);
            } finally {
                execResult = this.currentExecResult.get();
                this.currentExecResult.set(null);
            }

            // Find the snippet event that corresponds to this new snippet; there should be exactly one such event.
            final SnippetEvent event = events.stream()
              .filter(e -> e.causeSnippet() == null)
              .reduce((e1, e2) -> {
                throw new JavaBoxException(String.format("internal error: multiple events: %s, %s", e1, e2));
              })
              .orElseThrow(() -> new JavaBoxException(String.format("internal error: no event in %s", events)));
            final Snippet snippet = event.snippet();

            // Check snippet status
            Object returnValue = null;
            switch (event.status()) {
            case RECOVERABLE_DEFINED:
            case RECOVERABLE_NOT_DEFINED:
                break;
            case VALID:
                Optional<Throwable> error = Optional.ofNullable(execResult)
                  .map(ExecResult::error)
                  .or(() -> Optional.of(event).map(SnippetEvent::exception));
                if (error.isPresent()) {
                    snippetOutcomes.add(SnippetOutcome.exceptionThrown(this, snippet, error.get()));
                    break snippetLoop;
                }
                switch (snippet.kind()) {
                case EXPRESSION:
                case VAR:
                    returnValue = execResult.result();
                    break;
                default:
                    break;
                }
                break;
            case REJECTED:
                snippetOutcomes.add(SnippetOutcome.compilerErrors(this, snippet,
                  this.toErrors(snippetSource, snippetLineCol, this.jshell.diagnostics(snippet))));
                break snippetLoop;
            default:
                throw new JavaBoxException("internal error: " + event);
            }

            // It was successful
            snippetOutcomes.add(SnippetOutcome.success(this, snippet, returnValue));

            // Advance line & column
            if (semicolonAdded) {
                assert !snippetSource.isEmpty() && snippetSource.charAt(snippetSource.length() - 1) == ';';
                snippetSource = snippetSource.substring(0, snippetSource.length() - 1);
            }
            lineCol.advance(snippetSource);
            remain = info.remaining();
        }

        // Done
        return new ScriptResult(this, source, Collections.unmodifiableList(snippetOutcomes));
    }

    private List<CompilerError> toErrors(String source, LineAndColumn lineCol, Stream<Diag> diagnostics) {
        final CompilerError[] array = diagnostics
          .sorted(Comparator.comparingLong(Diag::getPosition))
          .map(diag -> {
            final LineAndColumn diagLineCol = lineCol.dup();
            diagLineCol.advance(source.substring(0, (int)diag.getPosition()));
            return diagLineCol.toError(diag.getMessage(Locale.ROOT));
          })
          .toArray(CompilerError[]::new);
        return List.of(array);
    }

// Control Support

    /**
     * Obtain the execution context associated with the specified {@link Control} class
     * and the script execution occurring in the current thread.
     *
     * <p>
     * This method can be used by {@link Control}s that need access to the per-execution context
     * from within the execution thread, for example, from bytecode woven into script classes.
     *
     * <p>
     * The {@link Control} class name is used instead of the {@link Control} instance itself to
     * allow custom obtaining the context instance from woven bytecode. The {@code controlType}
     * must exactly equal the {@link Control} instance class (not just be assignable from it).
     * If multiple instances of the same {@link Control} class are configured on a container,
     * then the context associated with the first instance is returned.
     *
     * <p>
     * Note that the return value from a script execution can be an invokable object, and any
     * subsequent invocation directly into that object will not have a per-execution context
     * since it is executing outside of the container. If invoked in that scenario, this method
     * will instead throw {@link IllegalStateException}.
     *
     * @param controlType the control's Java class
     * @return the execution context for the control of type {@code controlType}
     * @throws JavaBoxException if no control having type {@code controlType} is configured
     * @throws IllegalStateException if the current thread is not a {@link JavaBox} script execution thread
     */
    public static ExecutionContext executionContextFor(Class<? extends Control> controlType) {
        final ExecutionInfo info = EXECUTION_INFO.get();
        Preconditions.checkState(info != null, "no script is currently executing in this thread");
        return info.executionContexts().stream()
          .filter(context -> context.containerContext().control().getClass().equals(controlType))
          .findFirst()
          .orElseThrow(() -> new JavaBoxException(String.format(
            "there is no configured control of type %s; the configured controls are: %s",
            controlType.getName(), info.box().config.controls())));
    }

// Package Access - JavaBoxExecutionControl

    synchronized ClassBytecodes applyControls(ClassBytecodes cbc) {
        if (this.config.controls().isEmpty())
            return cbc;
        byte[] newBytecode = cbc.bytecodes().clone();           // clone() it just in case it matters
        for (Control control : this.config.controls()) {
            byte[] modifiedBytecode = control.modifyBytecode(cbc.name(), newBytecode);
            if (modifiedBytecode != null)
                newBytecode = modifiedBytecode;
        }
        return new ClassBytecodes(cbc.name(), newBytecode);
    }

    // Callback from JavaBoxExecutionControl constructor
    static JavaBox getCurrent() {
        return CURRENT.require();
    }

    void startExecution() {

//        if (this.log.isDebugEnabled())
//            this.log.debug("enterContext()", new Throwable("HERE"));

        // Sanity check
        Preconditions.checkState(this.executeThread != null, "internal error");
        Preconditions.checkState(EXECUTION_INFO.get() == null, "internal error");

        // Set thread name (if not already set)
        final Thread thread = Thread.currentThread();
        if (!thread.getName().startsWith(THREAD_NAME_PREFIX))
            thread.setName(String.format("%s-%d", THREAD_NAME_PREFIX, THREAD_NAME_INDEX.incrementAndGet()));

        // Reset result
        this.currentExecResult.set(null);

        // Initialize execution contexts
        final ExecutionInfo info = new ExecutionInfo(this);
        EXECUTION_INFO.set(info);
        this.containerContexts.stream()
          .map(context -> new ExecutionContext(context, context.control().startExecution(context)))
          .forEach(info.executionContexts()::add);

//        if (this.log.isDebugEnabled())
//            this.log.debug("enterContext(): this.currentExecResult={}", this.currentExecResult.get());

        // Notify subclass
        this.startingExecution();
    }

    void finishExecution(Object result, Throwable error) {

        // Sanity check
        Preconditions.checkState(this.executeThread != null, "internal error");
        Preconditions.checkState(EXECUTION_INFO.get() != null, "internal error");

//        if (this.log.isDebugEnabled())
//            this.log.debug("leaveContext(): result={} error={}", result, error, new Throwable("HERE"));

        // Snapshot ExecResult
        this.currentExecResult.set(new ExecResult(result, error));

        // Shutdown execution contexts
        final ExecutionInfo info = EXECUTION_INFO.get();
        EXECUTION_INFO.set(null);
        info.executionContexts().forEach(executionContext -> {
            final Control control = executionContext.containerContext().control();
            try {
                control.finishExecution(executionContext, result, error);
            } catch (Exception e) {
                this.log.warn("error closing {} context for {} (ignoring)", "execution", control, e);
            }
        });

//        if (this.log.isDebugEnabled())
//            this.log.debug("leaveContext(): this.currentExecResult={}", this.currentExecResult.get());

        // Notify subclass
        this.finishingExecution(result, error);
    }

    /**
     * Subclass hook invoked when starting script execution.
     *
     * <p>
     * This method must not lock this instance or deadlock will result.
     *
     * <p>
     * The implementation in {@link JavaBox} does nothing.
     */
    protected void startingExecution() {
    }

    /**
     * Subclass hook invoked when finishing script execution.
     *
     * <p>
     * This method must not lock this instance or deadlock will result.
     *
     * <p>
     * The implementation in {@link JavaBox} does nothing.
     */
    protected void finishingExecution(Object result, Throwable error) {
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

        CompilerError toError(String message) {
            return new CompilerError(lineNumber().get(), columNumber().get(), message);
        }

        LineAndColumn dup() {
            return new LineAndColumn(lineNumber(), columNumber());
        }
    }

// ExecutionInfo

    private record ExecutionInfo(JavaBox box, List<ExecutionContext> executionContexts) {

        ExecutionInfo(JavaBox box) {
            this(box, new ArrayList<>(box.containerContexts.size()));
        }
    }

// ExecResult

    private record ExecResult(Object result, Throwable error) {

        static ExecResult empty() {
            return new ExecResult(null, null);
        }
    }
}
