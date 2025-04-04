
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import com.google.common.base.Preconditions;

import java.io.Closeable;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
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
import jdk.jshell.SourceCodeAnalysis.Completeness;
import jdk.jshell.SourceCodeAnalysis.CompletionInfo;
import jdk.jshell.spi.ExecutionControl.ClassBytecodes;

import org.dellroad.javabox.Control.ContainerContext;
import org.dellroad.javabox.Control.ExecutionContext;
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
    private static final ThreadLocal<JavaBox> CURRENT = new ThreadLocal<>();
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

    // The value of some variable being set by setVariable()
    private AtomicReference<Object> variableValue;

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

    /**
     * Get the {@link JavaBox} instance associated with the current thread.
     *
     * <p>
     * This method works during {@link JShell} initialization and script execution.
     *
     * @throws IllegalStateException if there is no such instance
     */
    public static JavaBox getCurrent() {
        final JavaBox box = CURRENT.get();
        Preconditions.checkState(box != null, "there is no JavaBox associated with the current thread");
        return box;
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
        Preconditions.checkState(CURRENT.get() == null, "reentrant invocation");
        CURRENT.set(this);
        try {

            // Create our executor
            this.executor = Executors.newSingleThreadExecutor();

            // Create our JShell instance
            this.jshell = this.config.jshellBuilder().build();

            // Initialize control contexts (including our own, which goes first)
            this.config.controls().stream()
              .forEach(control -> this.containerContexts.add(new ContainerContext(this, control, control.initialize(this))));
        } catch (RuntimeException | Error e) {
            this.shutdown();
            throw e;
        } finally {
            CURRENT.set(null);
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

// Variables

    /**
     * Get the value of a variable in this container.
     *
     * @param varName variable name
     * @return variable value
     * @throws InterruptedException if the current thread is interrupted
     * @throws IllegalStateException if this instance is not initialized or closed
     * @throws IllegalArgumentException if {@code varName} is not found
     * @throws IllegalArgumentException if {@code varName} is not a valid Java identifier
     * @throws IllegalArgumentException if {@code varName} is null
     * @see JShell#variables
     */
    public Object getVariable(String varName) throws InterruptedException {

        // Sanity check
        this.checkVariableName(varName);
        Preconditions.checkState(this.initialized, "not initialized");
        Preconditions.checkState(!this.closed, "closed");

        // Get the variable
        final SnippetOutcome outcome = this.execute(varName).snippetOutcomes().get(0);
        switch (outcome.type()) {
        case SUCCESSFUL_WITH_VALUE:
            return outcome.returnValue();
        case COMPILER_ERRORS:
            throw new IllegalArgumentException("no such variable \"" + varName + "\"");
        default:
            throw new JavaBoxException("error getting variable: " + outcome);
        }
    }

    /**
     * Declare and assign a variable in this container.
     *
     * @param varName variable name
     * @param varValue variable value
     * @throws InterruptedException if the current thread is interrupted
     * @throws IllegalStateException if this instance is not initialized or closed
     * @throws IllegalArgumentException if {@code varName} is not a valid Java identifier
     * @throws IllegalArgumentException if {@code varName} is null
     * @see JShell#variables
     */
    public void setVariable(String varName, Object varValue) throws InterruptedException {
        this.setVariable(varName, null, varValue);
    }

    /**
     * Declare and assign a variable in this container.
     *
     * <p>
     * If {@code vartype} is null:
     * <ul>
     *  <li>The actual type of {@code varValue} (expressed as a string) will be used;
     *      this type must be accessible in the generated script
     *  <li>If {@code varValue} is a non-null primitive wrapper type, the corresponding primitive type is used
     *  <li>If {@code varValue} is null, {@code var} will be used
     * </ul>
     *
     * @param varName variable name
     * @param varType variable's declared type, or null to infer from actual type
     * @param varValue variable value
     * @throws InterruptedException if the current thread is interrupted
     * @throws IllegalStateException if this instance is not initialized or closed
     * @throws IllegalArgumentException if {@code varName} is not a valid Java identifier
     * @throws IllegalArgumentException if {@code varName} is null
     * @see JShell#variables
     */
    public void setVariable(String varName, String varType, Object varValue) throws InterruptedException {

        // Sanity check
        this.checkVariableName(varName);
        Preconditions.checkState(this.initialized, "not initialized");
        Preconditions.checkState(!this.closed, "closed");

        // Auto-generate type if needed
        if (varType == null) {
            varType = switch (varValue) {
                case null -> Object.class.getName();
                case Boolean x -> "boolean";
                case Byte x -> "byte";
                case Character x -> "char";
                case Short x -> "short";
                case Integer x -> "int";
                case Float x -> "float";
                case Long x -> "long";
                case Double x -> "double";
                default -> varValue.getClass().getName();
            };
        }

        // Create a script that sets the variable
        String script = String.format("%s %s = (%s)%s.variableValue();", varType, varName, varType, JavaBox.class.getName());

        // Wait for our turn
        synchronized (this) {
            while (this.variableValue != null)
                this.wait();
            this.variableValue = new AtomicReference<>(varValue);
        }

        // Execute the script
        try {
            final SnippetOutcome outcome = this.execute(script).snippetOutcomes().get(0);
            if (!outcome.type().isSuccessful())
                throw new JavaBoxException("error setting variable: " + outcome);
        } finally {
            synchronized (this) {
                this.variableValue = null;
                this.notifyAll();
            }
        }
    }

    /**
     * Obtain the value of a variable being set by {@link #setVariable setVariable()}.
     *
     * <p>
     * This method is only used internally; it's {@code public} so that it can be accessed
     * from JShell scripts.
     *
     * @return value of variable being set if any, otherwise null
     */
    public static Object variableValue() {
        return JavaBox.getCurrent().variableValue.get();
    }

    private void checkVariableName(String name) {
        Preconditions.checkArgument(name != null, "null variable name");
        boolean first = true;
        for (Iterator<Integer> i = name.codePoints().iterator(); i.hasNext(); ) {
            final int codePoint = i.next();
            final boolean valid = first ?
              Character.isJavaIdentifierStart(codePoint) :
              Character.isJavaIdentifierPart(codePoint);
            if (!valid)
                throw new IllegalArgumentException("invalid variable name \"" + name + "\"");
            first = false;
        }
        Preconditions.checkArgument(!first, "empty variable name");
    }

// Script Execution

    /**
     * Execute the given script in this container with the specified preset variables.
     *
     * <p>
     * The script is broken into individual snippets, which are executed one-at-a-time. Processing stops
     * if any snippet fails, otherwise after the last snippet has finished. The results from the execution
     * of the snippets that were processed are in the returned {@link ScriptResult}.
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
        for (String remain = source; !remain.isEmpty(); ) {

            // Check for interrupt of execute() thread
            if (this.executionInterrupted)
                return null;

            // Scrape off the snippet and analzye
            final CompletionInfo info = sourceCodeAnalysis.analyzeCompletion(remain);
            String snippetSource = info.source();

            // Debug
//            if (this.log.isDebugEnabled()) {
//                String display = snippetSource.replaceAll("\\s", " ").trim();
//                if (display.length() > 200)
//                    display = display.substring(0, 200) + "...";
//                this.log.debug("execute:\n  snippet=[{}]\n  completeness={}", display, info.completeness());
//            }

            // Analyze and execute snippet
            /*final*/ SnippetOutcome outcome;
            switch (info.completeness()) {
            case CONSIDERED_INCOMPLETE:
            case DEFINITELY_INCOMPLETE:
                outcome = SnippetOutcome.compilerErrors(this, snippetSource,
                  Collections.singletonList(lineCol.toError("incomplete trailing statement")));
                break;
            case UNKNOWN:
                final List<SnippetEvent> eval = this.jshell.eval(snippetSource);
                final SnippetEvent event;
                if (eval.size() != 1 || !Snippet.Status.REJECTED.equals((event = eval.get(0)).status()))
                    throw new JavaBoxException("internal error: " + eval);
                outcome = SnippetOutcome.compilerErrors(this, snippetSource,
                  this.toErrors(snippetSource, lineCol, this.jshell.diagnostics(event.snippet())));
                break;
            case COMPLETE_WITH_SEMI:
            case COMPLETE:
            case EMPTY:
                outcome = this.executeSnippet(snippetSource, lineCol);
                break;
            default:
                throw new JavaBoxException("internal error");
            }

            // Add outcome to the list, and bail out if error is severe enough
            snippetOutcomes.add(outcome);
            if (outcome.type().isHaltsScript())
                break;

            // Advance line & column to the next snippet
            if (info.completeness() == Completeness.COMPLETE_WITH_SEMI) {
                assert !snippetSource.isEmpty() && snippetSource.charAt(snippetSource.length() - 1) == ';';
                snippetSource = snippetSource.substring(0, snippetSource.length() - 1);
            }
            lineCol.advance(snippetSource);
            remain = info.remaining();
        }

        // Update the status of any snippets that changed due to subsequent snippets
        for (int i = 0; i < snippetOutcomes.size(); i++) {
            final SnippetOutcome outcome = snippetOutcomes.get(i);
            final Snippet snippet = outcome.snippet().orElse(null);
            if (snippet == null)
                continue;
            SnippetOutcome updatedOutcome = null;
            Snippet.Status status = jshell.status(snippet);
            if (status == Snippet.Status.OVERWRITTEN) {
                switch (outcome.type()) {
                case SUCCESSFUL_NO_VALUE:
                case SUCCESSFUL_WITH_VALUE:
                case UNRESOLVED_REFERENCES:
                    break;
                default:
                    throw new JavaBoxException("internal error: " + outcome.type());
                }
                updatedOutcome = outcome.toOverwritten();
            } else if (outcome.type() == SnippetOutcome.Type.UNRESOLVED_REFERENCES && status == Snippet.Status.VALID)
                updatedOutcome = outcome.toValid();
            if (updatedOutcome != null)
                snippetOutcomes.set(i, updatedOutcome);
        }

        // Done
        return new ScriptResult(this, source, Collections.unmodifiableList(snippetOutcomes));
    }

    private SnippetOutcome executeSnippet(String source, LineAndColumn lineCol) {

        // Invoke JShell with the snippet
        final List<SnippetEvent> events;
        final ExecResult execResult;
        try {
            events = this.jshell.eval(source);
            execResult = this.currentExecResult.get();
        } catch (ControlViolationException e) {
            return SnippetOutcome.controlViolation(this, source, e);
        } finally {
            this.currentExecResult.set(null);
        }

        // Find the snippet event that corresponds to the new snippet; there should be exactly one such event
        final SnippetEvent event = events.stream()
          .filter(e -> e.causeSnippet() == null)
          .reduce((e1, e2) -> {
            throw new JavaBoxException(String.format("internal error: multiple events: %s, %s", e1, e2));
          })
          .orElseThrow(() -> new JavaBoxException(String.format("internal error: no event in %s", events)));
        final Snippet snippet = event.snippet();

        // Debug
//        if (this.log.isDebugEnabled())
//            this.log.debug("execute:\n  event={}\n  result={}", event, execResult);

        // Check snippet status
        Object returnValue = null;
        switch (event.status()) {
        case RECOVERABLE_DEFINED:
        case RECOVERABLE_NOT_DEFINED:
            return SnippetOutcome.unresolvedReferences(this, snippet);
        case VALID:
            Optional<Throwable> error = Optional.ofNullable(execResult)
              .map(ExecResult::error)
              .or(() -> Optional.of(event).map(SnippetEvent::exception));
            if (error.isPresent())
                return SnippetOutcome.exceptionThrown(this, snippet, error.get());
            switch (snippet.kind()) {
            case EXPRESSION:
            case VAR:
                returnValue = execResult.result();
                break;
            default:
                break;
            }
            return SnippetOutcome.success(this, snippet, returnValue);
        case REJECTED:
            return SnippetOutcome.compilerErrors(this, snippet,
              this.toErrors(source, lineCol.dup(), this.jshell.diagnostics(snippet)));
        default:
            throw new JavaBoxException("internal error: " + event);
        }
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

// Package Access

    // Callback from JavaBoxExecutionControl.load()
    synchronized ClassBytecodes applyControls(ClassBytecodes cbc) {

        // Apply controls
        final ClassDesc name = ClassDesc.of(cbc.name());
        final byte[] origBytes = cbc.bytecodes();
        byte[] bytes = origBytes;
        for (Control control : this.config.controls()) {
            bytes = control.modifyBytecode(name, bytes);
            if (bytes == null)
                throw new ControlViolationException("null bytecode returned by " + control);
        }

        // Any changes?
        if (bytes == origBytes)
            return cbc;

        // Sanity check class name didn't change
        final ClassDesc newName = ClassFile.of().parse(bytes).thisClass().asSymbol();
        if (!newName.equals(name)) {
            throw new ControlViolationException(String.format(
              "control(s) changed class name \"%s\" → \"%s\"", name.descriptorString(), newName.descriptorString()));
        }

        // Done
        return new ClassBytecodes(cbc.name(), bytes);
    }

    // Callback from JavaBoxExecutionControl.enterContext()
    void startExecution() {

        // Sanity check
        Preconditions.checkState(this.executeThread != null, "internal error");
        Preconditions.checkState(EXECUTION_INFO.get() == null, "internal error");
        Preconditions.checkState(CURRENT.get() == null, "internal error");
        Preconditions.checkState(this.currentExecResult.get() == null, "internal error");

        // Set thread name (if not already set)
        final Thread thread = Thread.currentThread();
        if (!thread.getName().startsWith(THREAD_NAME_PREFIX))
            thread.setName(String.format("%s-%d", THREAD_NAME_PREFIX, THREAD_NAME_INDEX.incrementAndGet()));

        // Debug
//        if (this.log.isDebugEnabled())
//            this.log.debug("startExecution(): result={}", this.currentExecResult.get(), new Throwable("HERE"));

        // Set current instance
        CURRENT.set(this);

        // Initialize execution contexts
        final ExecutionInfo info = new ExecutionInfo(this);
        EXECUTION_INFO.set(info);
        boolean success = false;
        try {

            // Initialize control execution contexts
            this.containerContexts.stream()
              .map(context -> new ExecutionContext(context, context.control().startExecution(context)))
              .forEach(info.executionContexts()::add);

            // Notify subclass
            this.startingExecution();

            // Done
            success = true;
        } finally {

            // Reset thread locals if exception thrown
            if (!success) {
                CURRENT.set(null);
                EXECUTION_INFO.set(null);
//                if (this.log.isDebugEnabled())
//                    this.log.debug("startExecution(): canceled due to exception");
            }
        }
    }

    // Callback from JavaBoxExecutionControl.leaveContext()
    void finishExecution(Object result, Throwable error) {

        // Sanity check
        final ExecutionInfo info = EXECUTION_INFO.get();
        Preconditions.checkState(this.executeThread != null, "internal error");
        Preconditions.checkState(EXECUTION_INFO.get() != null, "internal error");
        Preconditions.checkState(info != null, "internal error");
        Preconditions.checkState(this.currentExecResult.get() == null, "internal error");
        try {

            // Snapshot ExecResult
            this.currentExecResult.set(new ExecResult(result, error));

            // Shutdown execution contexts
            info.executionContexts().forEach(executionContext -> {
                final Control control = executionContext.containerContext().control();
                try {
                    control.finishExecution(executionContext, result, error);
                } catch (Throwable e) {
                    this.log.warn("error closing {} context for {} (ignoring)", "execution", control, e);
                }
            });

            // Debug
//            if (this.log.isDebugEnabled())
//                this.log.debug("finishExecution(): result={}", this.currentExecResult.get());

            // Notify subclass
            this.finishingExecution(result, error);
        } finally {

            // Reset thread locals
            CURRENT.set(null);
            EXECUTION_INFO.remove();
        }
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

        @Override
        public String toString() {
            final StringBuilder buf = new StringBuilder(32);
            buf.append(this.getClass().getSimpleName()).append('[');
            if (result == null && error == null)
                buf.append("empty");
            else {
                buf.append("result=").append(result);
                if (result != null)
                    buf.append(" (" + result.getClass().getName() + ")");
                if (error != null)
                    buf.append(", error=").append(error);
            }
            return buf.append(']').toString();
        }
    }
}
