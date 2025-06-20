
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import com.google.common.base.Preconditions;

import java.io.Closeable;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import jdk.jshell.Diag;
import jdk.jshell.JShell;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;
import jdk.jshell.SourceCodeAnalysis;
import jdk.jshell.SourceCodeAnalysis.Completeness;
import jdk.jshell.SourceCodeAnalysis.CompletionInfo;
import jdk.jshell.execution.LocalExecutionControl;
import jdk.jshell.spi.ExecutionControl.ClassBytecodes;

import org.dellroad.javabox.Control.ContainerContext;
import org.dellroad.javabox.Control.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.dellroad.javabox.SnippetOutcome.CompilerError;
import static org.dellroad.javabox.SnippetOutcome.ExceptionThrown;
import static org.dellroad.javabox.SnippetOutcome.HaltsScript;
import static org.dellroad.javabox.SnippetOutcome.Interrupted;
import static org.dellroad.javabox.SnippetOutcome.Skipped;
import static org.dellroad.javabox.SnippetOutcome.SuccessfulNoValue;
import static org.dellroad.javabox.SnippetOutcome.SuccessfulWithValue;
import static org.dellroad.javabox.SnippetOutcome.Suspended;
import static org.dellroad.javabox.SnippetOutcome.ValidationFailure;

/**
 * A container for scripts written in the Java language.
 *
 * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/prism.min.js"></script>
 * <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/components/prism-java.min.js"></script>
 * <link href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.27.0/themes/prism.min.css" rel="stylesheet"/>
 *
 * <p><b>Overview</b>
 *
 * <p>
 * Each {@link JavaBox} instance relies on an underlying {@link JShell} instance configured for to parse and execute
 * scripts, and to hold its state, i.e., the variables, methods, and classes declared by those scripts.
 * The {@link JavaBox} instance is configured for {@linkplain LocalExecutionControl local execution}, so scripts
 * run within the same virtual machine.
 *
 * <p>
 * Bevcause {@link JavaBox} instances always run in local execution mode, they support the direct transfer of Java objects
 * between the container and the outside world:
 * <ul>
 *  <li>Return values from script execution are returned to the caller
 *  <li>{@link JShell} variables can be read and written directly (via {@link #getVariable getVariable()}
 *      and {@link #setVariable setVariable()}).
 * </ul>
 *
 * <p><b>Lifecycle</b>
 *
 * <p>
 * Usage follows this pattern:
 * <ul>
 *  <li>Instances are created by providing a {@link Config}, an immutable configuration object
 *      created via {@link Config.Builder}.
 *  <li>Instances must be {@link #initialize}'d before use. This builds the underlying {@link JShell}
 *      instance and creates an internal service thread.
 *  <li>Instances must be {@link #close}'d to release resources when no longer needed.
 * </ul>
 *
 * <p><b>Script Execution</b>
 *
 * <p>
 * Scripts are executed via {@link #execute execute()}. A single script may contain multiple individual expressions,
 * statements, or declarations; these are called "snippets". The snippets are analyzed and executed one at a time.
 * The return value from {@link #execute execute()} contains a distinct {@link SnippetOutcome} for each of the snippets.
 * Often the last snippet's return value (if any) is considered to be the overall script's "return value".
 *
 * <p><b>Script Validation</b>
 *
 * <p>
 * It is also possible to validate and compile a script without executing it. This allows the caller to gather some basic
 * information about the script and control which types of snippets are allowed.
 *
 * <p><b>Suspend and Resume</b>
 *
 * <p>
 * If a script invokes {@link #suspend suspend()}, then {@link #execute execute()} returns to the caller with the
 * last snippet outcome being an instance of {@link Suspended}. The script can be restarted later by invoking
 * {@link #resume resume()}, which behaves just like {@link #execute execute()}, except that it continues the previous
 * script instead of starting a new one. On the next return, the previously suspended snippet's earlier {@link Suspended}
 * outcome will be overwritten with its new, updated outcome.
 *
 * <p>
 * If there is a suspended script associated with an instance, any new invocation of {@link #execute execute()} will fail.
 * Instead, suspeneded scripts must be resumed via {@link #resume resume()} and allowed to terminate (even if interrupted;
 * see below).
 *
 * <p><b>Interruption</b>
 *
 * <p>
 * Both {@link #execute execute()} and {@link #resume resume()} block the calling thread until the script terminates
 * or suspends itself. Another thread can interrupt that execution by interrupting the calling thread, or equivalently
 * by invoking {@link #interrupt}. If a snippet's execution is interrrupted, its outcome is {@link SnippetOutcome.Interrupted}.
 *
 * <p>
 * A suspended script can also be interrupted, but the script does not awaken immediately. Instead, upon the next call to
 * {@link #resume resume()}, it will terminate immediately with outcome {@link SnippetOutcome.Interrupted}.
 *
 * <p><b>Controls</b>
 *
 * <p>
 * Scripts may be restricted or otherwise transformed using {@link Control}s which are specified as part of the initial
 * {@link Config}. Controls can do the following things:
 * <ul>
 *  <li>Inspect and modify all of the bytecode generated from scripts
 *  <li>Keep state associated with each {@link JavaBox} instance
 *  <li>Keep state associated with each {@link JavaBox} snippet execution
 * </ul>
 *
 * <p>
 * Every control is given a per-container {@link ContainerContext} and a per-execution {@link ExecutionContext}.
 * Controls can modify script bytecode to insert method calls into the control itself, where it can then utilize its
 * state to decide what to do, etc. To access its state from within an executing snippet, a control invokes
 * {@link #executionContextFor executionContextFor()}.
 *
 * <p><b>Examples</b>
 *
 * <p>
 * Here is a simple "Hello, World" example:
 * <pre><code class="language-java">
 *  Config config = Config.builder().build();
 *  try (JavaBox box = new JavaBox(config)) {
 *      box.initialize();
 *      box.setVariable("target", "World");
 *      ScriptResult result = box.execute("""
 *          String.format("Hello, %s!", target);
 *          """);
 *      System.out.println(result.returnValue());      // prints "Hello, World!"
 *  }
 * </code></pre>
 *
 * <p>
 * Here is an example that shows how to avoid infinite loops:
 * <pre><code class="language-java">
 *  // Set up control
 *  Config config = Config.builder()
 *    .withControl(new TimeLimitControl(Duration.ofSeconds(5)))
 *    .build();
 *
 *  // Execute script
 *  ScriptResult result;
 *  try (JavaBox box = new JavaBox(config)) {
 *      box.initialize();
 *      result = box.execute("""
 *          while (true) {
 *              Thread.yield();
 *          }
 *      """);
 *  }
 *
 *  // Check result
 *  switch (result.snippetOutcomes().get(0)) {
 *  case SnippetOutcome.ExceptionThrown e when e.exception() instanceof TimeLimitExceededException
 *    -&gt; System.out.println("infinite loop detected");
 *  }
 * </code></pre>
 *
 * <p><b>Thread Safety</b>
 *
 * <p>
 * Instances are thread safe but single threaded: when simultaneous operations are attempted from multiple threads,
 * only one operation executes at a time.
 */
public class JavaBox implements Closeable {

    private static final String THREAD_NAME_PREFIX = "JavaBox";
    private static final AtomicLong THREAD_NAME_INDEX = new AtomicLong();
    private static final ThreadLocal<JavaBox> CURRENT_JAVABOX = new ThreadLocal<>();
    private static final ThreadLocal<SnippetThreadInfo> SNIPPET_THREAD_INFO = new ThreadLocal<>();
    private static final int EXECUTE_WAIT_TIME_MILLIS = 100;

    private static final String UNASSOCIATED_SNIPPET_ID = "*UNASSOCIATED*";     // defined in jdk.jshell.Snippet.java

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final Config config;
    private final ArrayList<ContainerContext> containerContexts = new ArrayList<>();

    private State state = State.INITIAL;
    private ExecutorService executor;
    private JShell jshell;

    // The value of some variable being set by setVariable()
    private AtomicReference<Object> variableValue;

    // Information used during process() in states EXECUTING, RETURNING, SUSPENDING, SUSPENDED, RESUMING
    private ProcessInfo processInfo;

    // Actual snippet return value or exception thrown
    private final AtomicReference<SnippetReturn> snippetReturn = new AtomicReference<>();

// ProcessInfo

    /**
     * Information that is passed around while a script is executing, i.e., while process() is being invoked
     * by the user thread.
     */
    private record ProcessInfo(
            JShell jshell,                          // a copy of JavaBox.jshell
            String source,                          // the script we are executing
            ProcessMode mode,                       // process() processing mode
            SnippetValidator validator,             // process() initial validator
            AtomicInteger snippetIndex,             // the index (in "infos" list) of the currently executing snippet, or -1
            AtomicBoolean interrupted,              // whether there is a pending interrupt
            AtomicReference<Object> resumeReturn,   // return value from suspend() (state RESUMING only)
            List<SnippetInfo> infos) {              // infomation about each snippets

        ProcessInfo(JShell jshell, String source, ProcessMode mode, SnippetValidator validator) {
            this(jshell, source, mode, validator, new AtomicInteger(-1),
            new AtomicBoolean(), new AtomicReference<>(), new ArrayList<>());
        }

        List<SnippetOutcome> copyOutcomes() {
            return this.infos.stream()
              .map(SnippetInfo::outcome)
              .map(AtomicReference::get)
              .collect(Collectors.toList());
        }
    }

    /**
     * Instance state.
     *
     * <p>
     * State management is complicated because there are 3+ different threads that can be running
     * through a single JavaBox instance at the same time:
     * <ul>
     *  <li>One or more USER THREADS invoking execute(), resume(), interrupt(), close(), etc.
     *  <li>The EXECUTION THREAD which runs on "executor" and invokes JShell.eval() from executeScript().
     *  <li>The SNIPPET THREAD created by JShell that actually executes snippet code. This thread
     *      invokes methods startExecution() and finishExecution() in this class.
     * </ul>
     */
    private enum State {

        // The initial state, prior to initialize()
        INITIAL {

            @Override
            void checkInvariants(JavaBox box) {
                Preconditions.checkState(box.executor == null);
                Preconditions.checkState(box.jshell == null);
                Preconditions.checkState(box.variableValue == null);
                Preconditions.checkState(box.processInfo == null);
                Preconditions.checkState(box.snippetReturn.get() == null);
            }
        },

        // Initialized but otherwise not doing anything
        IDLE {

            @Override
            void checkInvariants(JavaBox box) {
                Preconditions.checkState(box.executor != null);
                Preconditions.checkState(box.jshell != null);
                Preconditions.checkState(box.processInfo == null);
                Preconditions.checkState(box.snippetReturn.get() == null);
            }
        },

        // Executing a script: the user thread is waiting for a result from execute() or resume(), the execution thread is
        // executing executeScript() and either compiling a snippet or waiting for JShell.eval() to return, and (in the latter
        // case) the snippet thread is executing script code. The ProccessInfo.interrupted flag may be set; if so, JShell.stop()
        // will has been invoked. We can also get to this state from SUSPENDED if some user thread invokes interrupt().
        EXECUTING {

            @Override
            void checkInvariants(JavaBox box) {
                Preconditions.checkState(box.executor != null);
                Preconditions.checkState(box.jshell != null);
                Preconditions.checkState(box.processInfo != null);
                Preconditions.checkState(box.processInfo.resumeReturn.get() == null);
            }
        },

        // Follows EXECUTING after JShell.eval() has returned in the execution thread and executeScript() has returned.
        // We are waiting for the user thread that invoked execute() or resume() to wake up and
        // return a corresponding ScriptResult. After that the state reverts back to IDLE.
        RETURNING {

            @Override
            void checkInvariants(JavaBox box) {
                Preconditions.checkState(box.executor != null);
                Preconditions.checkState(box.jshell != null);
                Preconditions.checkState(box.processInfo != null);
                Preconditions.checkState(box.processInfo.snippetIndex.get() == -1);
                Preconditions.checkState(!box.processInfo.interrupted.get());
                Preconditions.checkState(box.processInfo.resumeReturn.get() == null);
                Preconditions.checkState(box.snippetReturn.get() == null);
            }
        },

        // Follows EXECUTING after an invocation of suspend() by the snippet thread. The script thread will be blocked in
        // suspend(). The execution thread will stay blocked waiting for JShell.eval() to return. The user thread will wake
        // up and return a ScriptResult and the state will change to SUSPENDED. Note: if ProccessInfo.interrupted is set when
        // suspend() is invoked, an immediate ThreadDeath exception is thrown, so it's not possible for that flag to be set
        // in this state except transiently during doSuspend().
        SUSPENDING {

            @Override
            void checkInvariants(JavaBox box) {
                Preconditions.checkState(box.executor != null);
                Preconditions.checkState(box.jshell != null);
                Preconditions.checkState(box.processInfo != null);
                Preconditions.checkState(box.processInfo.snippetIndex.get() >= 0);
                Preconditions.checkState(box.processInfo.resumeReturn.get() == null);
                Preconditions.checkState(box.snippetReturn.get() == null);
            }
        },

        // There is no user thread, the execution thread is still blocked in JShell.eval(), and the snippet thread is blocked
        // in suspend(). When resume() is invoked we will go to RESUMING.
        SUSPENDED {

            @Override
            void checkInvariants(JavaBox box) {
                Preconditions.checkState(box.executor != null);
                Preconditions.checkState(box.jshell != null);
                Preconditions.checkState(box.processInfo != null);
                Preconditions.checkState(box.processInfo.snippetIndex.get() >= 0);
                Preconditions.checkState(box.processInfo.resumeReturn.get() == null);
                Preconditions.checkState(box.snippetReturn.get() == null);
            }
        },

        // Same as SUSPENDED but resume() has been invoked and we are waiting for the snippet thread to wake up and return
        // ProcessInfo.resumeReturn from suspend(). If ProccessInfo.interrupted is set then suspend() with throw ThreadDeath
        // instead. Either way, after that we will go back to EXECUTING.
        RESUMING {

            @Override
            void checkInvariants(JavaBox box) {
                Preconditions.checkState(box.executor != null);
                Preconditions.checkState(box.jshell != null);
                Preconditions.checkState(box.processInfo != null);
                Preconditions.checkState(box.processInfo.snippetIndex.get() >= 0);
                Preconditions.checkState(box.snippetReturn.get() == null);
            }
        },

        // Closed and no longer usable
        CLOSED {

            @Override
            void checkInvariants(JavaBox box) {
                Preconditions.checkState(box.executor == null);
                Preconditions.checkState(box.jshell == null);
                Preconditions.checkState(box.variableValue == null);
                Preconditions.checkState(box.processInfo == null);
                Preconditions.checkState(box.snippetReturn.get() == null);
            }
        };

        abstract void checkInvariants(JavaBox box);
    }

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
     * Determine if this instance has been initialized.
     *
     * @return true if this instance is initialized, otherwise false
     */
    public synchronized boolean isInitialized() {
        return this.state != State.INITIAL;
    }

    /**
     * Determine if this instance is closed.
     *
     * @return true if this instance is closed, otherwise false
     */
    public synchronized boolean isClosed() {
        return this.state == State.CLOSED;
    }

    /**
     * Determine if this instance is currently executing a script.
     *
     * <p>
     * A suspended script counts as "currently executing"; use {@link #isSuspended} to detect that situation.
     *
     * @return true if this instance has a currently executing script, otherwise false
     */
    public synchronized boolean isExecuting() {
        this.checkInvariants();
        switch (this.state) {
        case EXECUTING:
        case SUSPENDING:
        case SUSPENDED:
        case RESUMING:
            return true;
        default:
            return false;
        }
    }

    /**
     * Determine if this instance has a suspended script.
     *
     * @return true if this instance has a currently suspended script, otherwise false
     */
    public synchronized boolean isSuspended() {
        switch (this.state) {
        case SUSPENDING:
        case SUSPENDED:
            return true;
        default:
            return false;
        }
    }

    /**
     * Get the {@link JShell} instanced associated with this container.
     *
     * @return this container's {@link JShell}
     * @throws IllegalStateException if this instance is not yet initialized
     */
    public synchronized JShell getJShell() {
        Preconditions.checkState(this.state != State.INITIAL, "not initialized");
        return this.jshell;
    }

    /**
     * Get the {@link JavaBox} instance associated with the current thread.
     *
     * <p>
     * This method works during (a) {@link JShell} initialization and (b) snippet execution.
     *
     * @throws IllegalStateException if there is no such instance
     */
    public static JavaBox getCurrent() {
        final JavaBox box = CURRENT_JAVABOX.get();
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
        Preconditions.checkState(this.state == State.INITIAL, "already initialized");

        // Initialize executor, JShell, and controls
        Preconditions.checkState(CURRENT_JAVABOX.get() == null, "reentrant invocation");
        CURRENT_JAVABOX.set(this);
        try {

            // Create our executor
            this.executor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task);
                thread.setName(String.format("%s-Execute-%d", THREAD_NAME_PREFIX, THREAD_NAME_INDEX.incrementAndGet()));
                return thread;
            });

            // Create our JShell instance
            this.jshell = this.config.jshellBuilder().build();

            // Initialize control contexts
            this.config.controls().stream()
              .forEach(control -> this.containerContexts.add(new ContainerContext(this, control, control.initialize(this))));
        } catch (RuntimeException | Error e) {
            this.close();
            throw e;
        } finally {
            CURRENT_JAVABOX.set(null);
        }

        // Ready
        this.newState(State.IDLE);
    }

    /**
     * Close this instance.
     *
     * <p>
     * If this instance is already closed this method does nothing.
     *
     * <p>
     * If there is a currently executing or suspended script, it will be interrupted and allowed to terminate.
     */
    @Override
    public synchronized void close() {

        // Wait for all activity to cease
        boolean wasInterrupted = false;
        while (true) {
            if (this.state != State.INITIAL)        // during intialize() our state is still being set up
                this.checkInvariants();
            switch (this.state) {
            case INITIAL:
            case IDLE:
            case CLOSED:
                break;
            case EXECUTING:
            case RETURNING:
            case SUSPENDING:
            case RESUMING:
                this.interrupt();
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    wasInterrupted = true;
                }
                continue;
            case SUSPENDED:
                this.interrupt();
                this.resume(null);
                continue;
            default:
                throw new JavaBoxException("internal error: " + this.state);
            }
            break;
        }

        // Shut everything down
        if (this.jshell != null) {
            this.jshell.close();
            this.jshell = null;
        }
        if (this.executor != null) {
            this.executor.shutdown();
            this.executor = null;
        }
        while (!this.containerContexts.isEmpty()) {
            final ContainerContext context = this.containerContexts.remove(this.containerContexts.size() - 1);
            try {
                context.control().shutdown(context);
            } catch (Throwable e) {
                this.log.warn("error closing {} context for {} (ignoring)", "container", context.control(), e);
            }
        }

        // Done
        if (wasInterrupted)
            Thread.currentThread().interrupt();
        this.newState(State.CLOSED);
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
        this.checkIdent(varName, "variable name");

        // Get the variable
        SnippetOutcome outcome = this.execute(varName).snippetOutcomes().get(0);
        if (outcome instanceof SuccessfulWithValue success)
            return success.returnValue();
        if (outcome instanceof CompilerError)
            throw new IllegalArgumentException("no such variable \"" + varName + "\"");
        throw new JavaBoxException("error getting variable \"" + varName + "\": " + outcome);
    }

    /**
     * Declare and assign a variable in this container.
     *
     * <p>
     * Equivalent to: {@link #setVariable(String, String, Object) setVariable}{@code (varName, null, varValue)}.
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
     * This is basically equivalent to executing the script {@code "<varType> <varName> = <varValue>;"}.
     *
     * <p>
     * If {@code vartype} is null:
     * <ul>
     *  <li>The actual type of {@code varValue} (expressed as a string) will be used;
     *      this type name must be accessible in the generated script
     *  <li>If {@code varValue} is a non-null primitive wrapper type, the corresponding primitive type is used
     *  <li>If {@code varValue} is null, {@code var} will be used
     * </ul>
     *
     * <p>
     * Using the narrowest possible type for {@code varType} is advantageous because it eliminates the need
     * for casting when referring to {@code varName} in subsequent scripts. However, it's possible that
     * {@code varType} is not accessible in the script environment, e.g., not on the classpath, or a private class.
     * In that case, this method will throw a {@link JavaBoxException}. To avoid that, set {@code varType} to any
     * accessible supertype (e.g., {@code "Object"}), or use {@code "var"} to infer it.
     *
     * @param varName variable name
     * @param varType variable's declared type, or null to infer from actual type; must be accessible in the generated script
     * @param varValue variable value
     * @throws InterruptedException if the current thread is interrupted
     * @throws IllegalStateException if this instance is not initialized or closed
     * @throws IllegalArgumentException if {@code varName} is not a valid Java identifier
     * @throws IllegalArgumentException if {@code varName} is null
     * @throws JavaBoxException if variable assignment fails
     * @see JShell#variables
     */
    public void setVariable(String varName, String varType, Object varValue) throws InterruptedException {

        // Sanity check
        this.checkIdent(varName, "variable name");

        // Auto-generate type if needed
        if (varType == null) {
            varType = varValue == null      ? Object.class.getName() :
              varValue instanceof Boolean   ? "boolean" :
              varValue instanceof Byte      ? "byte" :
              varValue instanceof Character ? "char" :
              varValue instanceof Short     ? "short" :
              varValue instanceof Integer   ? "int" :
              varValue instanceof Float     ? "float" :
              varValue instanceof Long      ? "long" :
              varValue instanceof Double    ? "double" : varValue.getClass().getName();
        }

        // Sanity check
        this.checkVariableType(varType);

        // Create a script that sets the variable
        final String script = String.format("%s %s = (%s)%s.variableValue();", varType, varName, varType, JavaBox.class.getName());

        // Ensure there is only one use of variableValue() at a time
        synchronized (this) {
            while (this.variableValue != null)
                this.wait();
            this.variableValue = new AtomicReference<>(varValue);
        }

        // Execute the script
        try {
            final SnippetOutcome outcome = this.execute(script).snippetOutcomes().get(0);
            if (outcome instanceof Interrupted)
                throw new InterruptedException("script was interrupted while setting variable");
            if (!(outcome instanceof SuccessfulWithValue))
                throw new JavaBoxException("error setting variable \"" + varName + "\": " + outcome);
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
     * @throws IllegalStateException if the current thread is not a {@link #setVariable setVariable()} script thread
     */
    public static Object variableValue() {
        return Optional.ofNullable(JavaBox.getCurrent())
          .map(box -> box.variableValue)
          .orElseThrow(() -> new IllegalStateException("the current thread is not a setVariable() snippet thread"))
          .get();
    }

    private void checkVariableType(String type) {
        for (String ident : type.split("\\.", -1)) {
            try {
                this.checkIdent(ident, "type name component");
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("invalid type name \"" + type + "\": " + e.getMessage());
            }
        }
    }

    private void checkIdent(String ident, String what) {
        Preconditions.checkArgument(ident != null, "null " + what);
        Preconditions.checkArgument(!ident.isEmpty(), "empty " + what);
        boolean first = true;
        for (Iterator<Integer> i = ident.codePoints().iterator(); i.hasNext(); ) {
            final int codePoint = i.next();
            if (!(first ? Character.isJavaIdentifierStart(codePoint) : Character.isJavaIdentifierPart(codePoint)))
                throw new IllegalArgumentException("invalid " + what + " \"" + ident + "\"");
            first = false;
        }
    }

// Script Execution

    /**
     * Compile the given script but do not execute it.
     *
     * <p>
     * This is a convenience method, equivalent to:
     *  {@link #process process}{@code (source, }{@link ProcessMode#COMPILE}{@code , null)}.
     * If compilation is successful, all outcomes will be {@link SuccessfulNoValue}.
     *
     * @param source the script to execute
     * @return result from script validation
     * @see #process process()
     */
    public synchronized ScriptResult compile(String source) {
        return this.process(source, ProcessMode.COMPILE, null);
    }

    /**
     * Execute the given script in this container.
     *
     * <p>
     * This is a convenience method, equivalent to:
     *  {@link #process process}{@code (source, }{@link ProcessMode#EXECUTE}{@code , null)}.
     *
     * @param source the script to execute
     * @return result from script execution
     * @see #process process()
     */
    public synchronized ScriptResult execute(String source) {
        return this.process(source, ProcessMode.EXECUTE, null);
    }

    /**
     * Process the given script in this container.
     *
     * <p>
     * The script is broken into individual snippets, each of which is validated, compiled, and/or executed according to
     * the specified {@link ProcessMode}, and the resulting {@link SnippetOutcome}s are returned as a {@link ScriptResult}.
     *
     * <p><b>Initial Validation</b>
     *
     * <p>
     * In all {@link ProcessMode}s, an initial basic structural validation of all snippets is first performed: each
     * snippet must be parseable. In addition, if {@code validator} is non-null, then it is included in this basic validation
     * step for each snpipet. This validation step does not require loading any generated classes or executing any code.
     * It is useful, for example, if you want to filter snippets by {@link Snippet.Kind} or otherwise inspect individual snippets.
     *
     * <p>
     * Snippets that fail to parse will have a {@link CompilerError} outcome. Otherwise, if {@code validator} is non-null,
     * each snippet is supplied to it; if it throws a {@link SnippetValidationException}, that snippet's outcome is set to
     * {@link ValidationFailure}. The snippets that {@code validator} sees will be <i>unassociated</i> (as described by
     * {@link SourceCodeAnalysis#sourceToSnippets SourceCodeAnalysis.sourceToSnippets()}). Snippets that successfully parse
     * and validate will have a {@link SuccessfulNoValue} outcome.
     *
     * <p>
     * If {@code mode} is {@link ProcessMode#VALIDATE} then processing stops after all snippets are validated and their
     * outcomes are returned. This mode can be used to gather basic information about a script, including how many snippets
     * it contains, their source code offsets, their {@link Snippet.Kind}, etc.
     *
     * <p><b>Compilation</b>
     *
     * <p>
     * If {@code mode} is {@link ProcessMode#COMPILE}, then after initial validation each snippet is fully compiled into
     * bytecode and loaded (so that any configured {@link Control}s are given a chance to veto it), but no execution of
     * any snippet's generated bytecode occurs.
     *
     * <p><b>Execution</b>
     *
     * <p>
     * If {@code mode} is {@link ProcessMode#EXECUTE}, then after initial validation each snippet is compiled, loaded,
     * and executed, one at a time. Processing stops if compilation or execution of any snippet results in an error,
     * i.e., it has an outcome implementing {@link HaltsScript}. If this happens, subsequent snippets will have outcome
     * {@link Skipped}.
     *
     * <p><b>Suspend/Resume</b>
     *
     * <p>
     * If an executing script suspends itself by invoking {@link #suspend suspend()}, this method immediately returns,
     * the corresponding snippet outcome is {@link Suspended}, and the script then becomes this instance's <i>suspended script</i>.
     * The script must be {@link #resume resume()}ed before a new script can be processed. A suspended script can also be
     * {@link #interrupt}ed, in which case it will throw {@link ThreadDeath} as soon as it is resumed.
     *
     * <p>
     * When this method returns early due to suspension, subsequent snippets will have outcome {@link Skipped}. If the script
     * is later {@link #resume resume()}ed, these outcomes are updated accordingly in the return value from that method.
     *
     * <p>
     * If this method is invoked when this instance already has a suspended script, an {@link IllegalStateException} is thrown.
     *
     * <p><b>Interruption</b>
     *
     * <p>
     * This method blocks until the script completes, suspends itself, or {@link #interrupt} is invoked (typically from
     * another thraed). Interrupting the current thread has the same effect as invoking {@link #interrupt}.
     *
     * @param source the script to process
     * @param mode processing mode
     * @param validator if not null, invoked to perform initial validation of each snippet in {@code source}
     * @return result from script processing
     * @throws InterruptedException if the current thread is interrupted or {@link #interrupt} is invoked
     * @throws IllegalStateException if this instance has a suspended script
     * @throws IllegalStateException if this instance is not initialized or closed
     * @throws IllegalArgumentException if {@code source} or {@code mode} is null
     */
    public synchronized ScriptResult process(String source, ProcessMode mode, SnippetValidator validator) {

        // Sanity check
        Preconditions.checkArgument(source != null, "null source");
        Preconditions.checkArgument(mode != null, "null mode");

        // Wait for previous operation to complete
        while (true) {
            this.checkAlive();
            this.checkInvariants();
            switch (this.state) {
            case IDLE:
                this.processInfo = new ProcessInfo(this.jshell, source, mode, validator);
                this.newState(State.EXECUTING);
                break;
            case EXECUTING:
            case RETURNING:
            case SUSPENDING:
            case RESUMING:
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    this.interrupt();
                }
                continue;
            case SUSPENDED:
                throw new IllegalStateException("this instance has a suspended script; it must be resumed first");
            default:
                throw new JavaBoxException("internal error: " + this.state);
            }
            break;
        }

        // Start a new execution task
        this.executor.submit(() -> {
            try {
                this.executeScript();
            } catch (Throwable t) {
                this.log.warn("error in execution task", t);
            }
        });

        // Wait for result
        return this.waitForResult();
    }

    /**
     * Specifies the snippet processing mode to {@link JavaBox#process}.
     */
    public enum ProcessMode {

        /**
         * Only perform initial validation of each snippet.
         *
         * <p>
         * No generated classes are loaded and no script coded is executed.
         * This is useful for detecting basic errors such as parser errors from the compiler.
         */
        VALIDATE,

        /**
         * Perform initial validation and compile each snippet and load any generated classes.
         *
         * <p>
         * The script is compiled and classes are generated and loaded, but no script code is actually executed.
         * This is useful for detecting compile-time errors in the script, but without actually executing any code.
         */
        COMPILE,

        /**
         * Perform initial validation of each snippet, load generated classes, and execute code as needed.
         *
         * <p>
         * This is peforms the normal JShell full evaluation of each snippet.
         */
        EXECUTE;
    }

    /**
     * Suspend the script that is currently executing in the current thread.
     *
     * <p>
     * If a script invokes this method, the script will pause and the associated {@link #execute execute()}
     * or {@link #resume resume()} invocation that (re)started this script's execution will return to the caller,
     * and the corresponding snippet outcome will be a {@link Suspended} containing the given {@code parameter}.
     *
     * <p>
     * This instance will then have a <i>suspended script</i>; it must be {@link #resume resume()}ed before a
     * new script can be {@link #execute execute()}ed. A suspended script can be {@link #interrupt}ed, in which
     * case it will throw {@link ThreadDeath} as soon as it resumes.
     *
     * @param parameter value to be made available via {@link Suspended#parameter}
     * @return the return value provided to {@link #resume resume()}
     * @throws ThreadDeath if {@link #interrupt} or {@link #close} was invoked while suspended
     * @throws IllegalStateException if the current thread is not a script execution thread
     */
    public static Object suspend(Object parameter) {

        // Sanity check we are the snippet thread
        final SnippetThreadInfo info = SNIPPET_THREAD_INFO.get();
        Preconditions.checkState(info != null, "the current thread is not a script execution thread");

        // Suspend this snippet
        return info.box.doSuspend(parameter);
    }

    private synchronized Object doSuspend(Object parameter) {

        // Update state
        this.checkInvariants();
        switch (this.state) {
        case EXECUTING:
            final int snippetIndex = this.processInfo.snippetIndex.get();
            final SnippetInfo info = this.processInfo.infos.get(snippetIndex);
            info.outcome.set(new SnippetOutcomes.Suspended(this, info, parameter));
            this.newState(State.SUSPENDING);
            break;
        default:
            throw new JavaBoxException("internal error: " + this.state);
        }

        // Wait for resume
        boolean wasInterrupted = false;
        while (true) {
            this.checkInvariants();
            switch (this.state) {
            case SUSPENDED:
            case SUSPENDING:
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    wasInterrupted = true;
                }
                continue;
            case RESUMING:
                final Object returnValue = this.processInfo.resumeReturn.get();
                this.processInfo.resumeReturn.set(null);
                this.newState(State.EXECUTING);
                if (this.processInfo.interrupted.get() || wasInterrupted)
                    throw new ThreadDeath();
                return returnValue;
            default:
                throw new JavaBoxException("internal error: " + this.state);
            }
        }
    }

    /**
     * Resume this instance's suspended script.
     *
     * <p>
     * The script's earlier invocation of {@link #suspend suspend()} will return the given {@code returnValue},
     * or throw {@link ThreadDeath} if {@link #interrupt} has been invoked since it was suspended. This method will
     * then block just like {@link #execute execute()}, i.e., until the script terminates or suspends itself again.
     *
     * <p>
     * The returned {@link ScriptResult} will include the outcomes from all snippets in the original script,
     * including those that executed before the snippet that invoked {@link #suspend suspend()}, followed by
     * the updated outcome of the suspended snippet (replacing the previous {@link Suspended} outcome), followed
     * by the outcomes of the script's subsequent snippets.
     *
     * @param returnValue the value to be returned to the script from {@link #suspend suspend()}
     * @return the result from the script's execution
     * @throws ThreadDeath if {@link #interrupt} or {@link #close} was invoked on the associated container
     * @throws IllegalStateException if this instance has no currently suspended script
     * @throws IllegalStateException if this instance is not initialized or closed
     */
    public synchronized ScriptResult resume(Object returnValue) {

        // Resume suspended snippet
        boolean wasInterrupted = false;
        while (true) {
            this.checkAlive();
            this.checkInvariants();
            switch (this.state) {
            case SUSPENDING:
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    this.interrupt();
                }
                continue;
            case SUSPENDED:
                this.processInfo.resumeReturn.set(returnValue);
                this.newState(State.RESUMING);
                break;
            case IDLE:
            case EXECUTING:
            case RETURNING:
            case RESUMING:
                throw new IllegalStateException("this instance does not currently have a suspended script to resume");
            default:
                throw new JavaBoxException("internal error: " + this.state);
            }
            break;
        }

        // Do more execution
        return this.waitForResult();
    }

    private ScriptResult waitForResult() {
        assert Thread.holdsLock(this);
        while (true) {
            this.checkInvariants();
            switch (this.state) {
            case EXECUTING:
            case RESUMING:
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    this.interrupt();
                }
                continue;
            case RETURNING: {
                final ScriptResult result = new ScriptResult(this, this.processInfo.source(), this.processInfo.copyOutcomes());
                this.processInfo = null;
                this.newState(State.IDLE);
                return result;
            }
            case SUSPENDING: {
                this.newState(State.SUSPENDED);
                return new ScriptResult(this, this.processInfo.source(), this.processInfo.copyOutcomes());
            }
            default:
                throw new JavaBoxException("internal error: " + this.state);
            }
        }
    }

    /**
     * Interrupt the current script execution, if any.
     *
     * <p>
     * If there is no current execution, then nothing happens and false is returned. Otherwise, an attempt is made to stop
     * the execution via {@link JShell#stop}. If successful, the final snippet outcome will be {@link Interrupted}.
     * Even in this case, because this operation is asynchronous, the snippet may have actually never started, it may have only
     * partially completed, or it may have fully completed.
     *
     * <p>
     * If this instance has a currently suspeneded script, that script will awaken and throw an immediate {@link ThreadDeath}
     * exception. Note that {@link Interrupted} is the outcome assigned to any snippet that terminates by
     * throwing {@link ThreadDeath}.
     *
     * <p>
     * If this instance is closed or not initialized, false is returned.
     *
     * @return true if execution was interrupted, false if no execution was occurring
     */
    public synchronized boolean interrupt() {
        this.checkInvariants();
        switch (this.state) {
        case INITIAL:
        case CLOSED:
        case IDLE:
        case RETURNING:
            return false;
        case EXECUTING:
        case SUSPENDING:
        case SUSPENDED:
        case RESUMING:
            if (this.state == State.EXECUTING)
                this.jshell.stop();
            this.processInfo.interrupted.set(true);
            this.notifyAll();
            return true;
        default:
            throw new JavaBoxException("internal error: " + this.state);
        }
    }

    private void newState(State state) {
        Preconditions.checkState(Thread.holdsLock(this));
        this.state = state;
        this.notifyAll();
    }

    private void checkInvariants() {
        this.state.checkInvariants(this);
    }

    private void checkAlive() {
        switch (this.state) {
        case INITIAL:
            throw new IllegalStateException("instance is not initialized");
        case CLOSED:
            throw new IllegalStateException("instance is closed");
        default:
            break;
        }
    }

// SnippetInfo

    record SnippetInfo(String source, int offset, CompletionInfo completionInfo,
      AtomicReference<Snippet> snippet, ArrayList<Diag> diagnostics, AtomicReference<SnippetOutcome> outcome) {

        SnippetInfo(JShell jshell, int offset, String source) {
            this(jshell, offset, source, jshell.sourceCodeAnalysis().analyzeCompletion(source));
        }

        SnippetInfo(JShell jshell, int offset, String source, CompletionInfo completionInfo) {
            this(SnippetInfo.fixupSource(completionInfo), offset, completionInfo,
              new AtomicReference<>(), new ArrayList<>(), new AtomicReference<>());
            final List<Snippet> snippets = jshell.sourceCodeAnalysis().sourceToSnippets(completionInfo.source());
            Preconditions.checkState(snippets.size() == 1, "internal error");
            this.updateSnippet(jshell, snippets.get(0));
        }

        private static String fixupSource(CompletionInfo info) {
            String source = info.source();
            if (info.completeness() == Completeness.COMPLETE_WITH_SEMI)
                source = source.substring(0, source.length() - 1);
            return source;
        }

        public void updateSnippet(JShell jshell, Snippet snippet) {
            this.snippet.set(snippet);
            diagnostics.clear();
            if (!snippet.id().equals(UNASSOCIATED_SNIPPET_ID))
                jshell.diagnostics(snippet).forEach(diagnostics::add);
        }
    }

    @SuppressWarnings("fallthrough")
    private void executeScript() throws Exception {

        // Avoid SpotBugs warnings
        final ProcessInfo pinfo;
        synchronized (this) {
            pinfo = this.processInfo;
        }

        // Sanity checks
        Preconditions.checkArgument(pinfo.infos.isEmpty(), "internal error");
        Preconditions.checkArgument(pinfo.snippetIndex.get() == -1, "internal error");

        // Break script into individual source snippets and create corresponding SnippetInfo's
        int offset = 0;
        String remain = pinfo.source;
        while (!remain.isEmpty()) {
            final SnippetInfo info = new SnippetInfo(pinfo.jshell, offset, remain);
            if (info.completionInfo.completeness() != Completeness.EMPTY)   // elide comments
                pinfo.infos.add(info);
            offset += info.source.length();
            remain = info.completionInfo.remaining();
        }

        // Peform initial validation of each snippet
        for (int i = 0; i < pinfo.infos.size(); i++) {
            final SnippetInfo info = pinfo.infos.get(i);
            switch (info.completionInfo.completeness()) {
            case UNKNOWN:
            case CONSIDERED_INCOMPLETE:
            case DEFINITELY_INCOMPLETE:
            case COMPLETE_WITH_SEMI:

                // Parse failed, so full compilation will fail also; so try to evaluate it to generate some diagnostics
                final List<SnippetEvent> eval = pinfo.jshell.eval(info.source);
                final SnippetEvent event;
                if (eval.size() != 1 || !Snippet.Status.REJECTED.equals((event = eval.get(0)).status()))
                    throw new JavaBoxException("internal error: " + eval);
                info.updateSnippet(pinfo.jshell, event.snippet());
                info.outcome.set(new SnippetOutcomes.CompilerError(this, info));
                break;
            case COMPLETE:

                // Parse was successful; next apply validator, if any
                if (pinfo.validator != null) {
                    try {
                        pinfo.validator.validate(info.snippet.get());
                    } catch (SnippetValidationException e) {
                        info.outcome.set(new SnippetOutcomes.ValidationFailure(this, info, e));
                        break;
                    } catch (Throwable t) {
                        info.outcome.set(new SnippetOutcomes.ValidationFailure(this, info,
                          new SnippetValidationException("validator threw unexpected exception", t)));
                        break;
                    }
                }

                // OK
                info.outcome.set(new SnippetOutcomes.SuccessfulNoValue(this, info));
                break;
            default:
                throw new JavaBoxException("internal error");
            }
        }

        // Proceed only if all snippets passed initial validation and mode != VALIDATE
        final boolean allValid = pinfo.infos.stream()
          .map(SnippetInfo::outcome)
          .map(AtomicReference::get)
          .allMatch(SuccessfulNoValue.class::isInstance);
        if (allValid && pinfo.mode != ProcessMode.VALIDATE) {

            // Reset all outcomes to Skipped in case we suspend/resume or encounter a HaltsScript outcome
            pinfo.infos.forEach(info -> info.outcome.set(new SnippetOutcomes.Skipped(this, info)));

            // Compile/execute each snippet
            for (int i = 0; i < pinfo.infos.size(); i++) {
                if (!this.executeSnippet(pinfo, i))
                    break;
            }
        }

        // Finish up
        synchronized (this) {
            this.checkAlive();
            this.checkInvariants();
            switch (this.state) {
            case EXECUTING:
                this.newState(State.RETURNING);
                break;
            default:
                throw new JavaBoxException("internal error: " + this.state);
            }
        }
    }

    /**
     * Compile/execute the specified snippet.
     *
     * @return true to proceed, false to halt script
     */
    private boolean executeSnippet(ProcessInfo pinfo, final int executeIndex) {

        // Sanity check
        Preconditions.checkArgument(pinfo.snippetIndex.get() == -1);

        // Build mapping from snippet ID to that snippet's index in the list
        final HashMap<String, Integer> idMap = new HashMap<>(pinfo.infos.size());
        for (int i = 0; i < pinfo.infos.size(); i++) {
            final String id = pinfo.infos.get(i).snippet.get().id();
            if (!id.equals(UNASSOCIATED_SNIPPET_ID))
                idMap.put(id, i);
        }

        // Invoke JShell with the snippet
        final List<SnippetEvent> events;
        final SnippetReturn snippetReturn;
        pinfo.snippetIndex.set(executeIndex);
        final SnippetInfo executeInfo = pinfo.infos.get(executeIndex);
        try {
            events = pinfo.jshell.eval(executeInfo.source);
            snippetReturn = this.snippetReturn.get();
        } catch (ControlViolationException e) {
            executeInfo.outcome.set(new SnippetOutcomes.ControlViolation(this, executeInfo, e));
            return pinfo.mode != ProcessMode.EXECUTE;
        } finally {
            this.snippetReturn.set(null);
            pinfo.snippetIndex.set(-1);
        }

        // Scan snippet events: find the one corresponding to the new snippet, and update existing snippets from any others
        for (SnippetEvent event : events) {
            final Snippet snippet = event.snippet();
            final boolean isUpdate = event.causeSnippet() != null;

            // Figure out which snippet this event applies to, but note we can get events for snippets that
            // were part of a previous script, and so they will not be in our map. If so, just ignore them.
            final int snippetIndex = isUpdate ? idMap.computeIfAbsent(snippet.id(), id -> -1) : executeIndex;
            if (snippetIndex == -1)
                continue;

            // Update snippet and its diagnostics
            final SnippetInfo info = pinfo.infos.get(snippetIndex);
            info.updateSnippet(pinfo.jshell, snippet);

            // Set/update snippet outcome
            final SnippetOutcome outcome;
            switch (pinfo.jshell.status(snippet)) {
            case REJECTED:
                outcome = new SnippetOutcomes.CompilerError(this, info);
                break;
            case RECOVERABLE_DEFINED:
            case RECOVERABLE_NOT_DEFINED:
                outcome = new SnippetOutcomes.UnresolvedReferences(this, info);
                break;
            case OVERWRITTEN:
                outcome = new SnippetOutcomes.Overwritten(this, info);
                break;
            case VALID:
                if (isUpdate) {
                    final SnippetOutcome previous = info.outcome.get();
                    if (previous instanceof ExceptionThrown || previous instanceof SuccessfulWithValue)
                        throw new JavaBoxException("internal error");               // these should never update, right?
                    outcome = new SnippetOutcomes.SuccessfulNoValue(this, info);
                } else if (pinfo.mode == ProcessMode.COMPILE) {
                    outcome = new SnippetOutcomes.SuccessfulNoValue(this, info);    // we don't actually execute in this case
                } else {
                    final Optional<Throwable> error = Optional.ofNullable(snippetReturn)
                      .map(SnippetReturn::error)
                      .or(() -> Optional.of(event).map(SnippetEvent::exception));
                    if (error.isPresent()) {
                        final Throwable exception = error.get();

                        // If we were interrupted and the snippet threw ThreadDeath, then change its outcome to Interrupted
                        outcome = exception instanceof ThreadDeath && pinfo.interrupted.compareAndSet(true, false) ?
                          new SnippetOutcomes.Interrupted(this, info) :
                          new SnippetOutcomes.ExceptionThrown(this, info, exception);
                        break;
                    }
                    switch (snippet.kind()) {
                    case EXPRESSION:
                    case VAR:
                        outcome = new SnippetOutcomes.SuccessfulWithValue(this, info, snippetReturn.result());
                        break;
                    default:
                        outcome = new SnippetOutcomes.SuccessfulNoValue(this, info);
                        break;
                    }
                }
                break;
            default:
                throw new JavaBoxException("internal error");
            }
            info.outcome.set(outcome);
        }

        // Done
        return pinfo.mode != ProcessMode.EXECUTE || !(pinfo.infos.get(executeIndex).outcome instanceof HaltsScript);
    }

    synchronized boolean isCompileOnly() {
        return processInfo.mode != ProcessMode.EXECUTE;
    }

// Control Support

    /**
     * Obtain the execution context associated with the specified {@link Control} class
     * and the script execution occurring in the current thread.
     *
     * <p>
     * This method can be used by {@link Control}s that need access to the per-execution or per-container
     * context from within the execution thread, for example, from bytecode woven into script classes.
     *
     * <p>
     * The {@link Control} class is used instead of the {@link Control} instance itself to allow invoking
     * this method from woven bytecode. The {@code controlType} must exactly equal the {@link Control}
     * instance class (not just be assignable from it). If multiple instances of the same {@link Control}
     * class are configured on a container, then the context associated with the first instance is returned.
     *
     * <p>
     * This method only works while executing within the container. For example, if a scripts defines and returns
     * an instance of some class {@code C}, and then the caller invokes a method {@code C.foo()} which in turn
     * invokes this method, this method will throw an {@link IllegalStateException} because it will be executing
     * outside of the container. In other words, controls that use this method must make other arrangements if
     * they want to obtain context when executing script code outside of the container.
     *
     * @param controlType the control's Java class
     * @return the execution context for the control of type {@code controlType}
     * @throws JavaBoxException if no control having type {@code controlType} is configured
     * @throws IllegalStateException if the current thread is not a {@link JavaBox} script execution thread
     */
    public static ExecutionContext executionContextFor(Class<? extends Control> controlType) {
        final SnippetThreadInfo info = SNIPPET_THREAD_INFO.get();
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

        // Done
        return new ClassBytecodes(cbc.name(), bytes);
    }

    // Callback from JavaBoxExecutionControl.enterContext()
    void startExecution() {

        // Sanity check
        Preconditions.checkState(SNIPPET_THREAD_INFO.get() == null, "internal error");
        Preconditions.checkState(CURRENT_JAVABOX.get() == null, "internal error");
        Preconditions.checkState(this.snippetReturn.get() == null, "internal error");

        // Set thread name (if not already set)
        final Thread thread = Thread.currentThread();
        if (!thread.getName().startsWith(THREAD_NAME_PREFIX))
            thread.setName(String.format("%s-Script-%d", THREAD_NAME_PREFIX, THREAD_NAME_INDEX.incrementAndGet()));

        // Debug
//        if (this.log.isDebugEnabled())
//            this.log.debug("startExecution(): result={}", this.snippetReturn.get(), new Throwable("HERE"));

        // Set current instance
        CURRENT_JAVABOX.set(this);

        // Initialize execution contexts
        final SnippetThreadInfo info = new SnippetThreadInfo(this);
        SNIPPET_THREAD_INFO.set(info);
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
                CURRENT_JAVABOX.set(null);
                SNIPPET_THREAD_INFO.set(null);
//                if (this.log.isDebugEnabled())
//                    this.log.debug("startExecution(): canceled due to exception");
            }
        }
    }

    // Callback from JavaBoxExecutionControl.leaveContext()
    void finishExecution(Object result, Throwable error) {

        // Sanity check
        final SnippetThreadInfo info = SNIPPET_THREAD_INFO.get();
        Preconditions.checkState(SNIPPET_THREAD_INFO.get() != null, "internal error");
        Preconditions.checkState(info != null, "internal error");
        Preconditions.checkState(this.snippetReturn.get() == null, "internal error");
        try {

            // Snapshot SnippetReturn
            this.snippetReturn.set(new SnippetReturn(result, error));

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
//                this.log.debug("finishExecution(): result={}", this.snippetReturn.get());

            // Notify subclass
            this.finishingExecution(result, error);
        } finally {

            // Reset thread locals
            CURRENT_JAVABOX.set(null);
            SNIPPET_THREAD_INFO.remove();
        }
    }

    /**
     * Subclass hook invoked when starting script execution from within the snippet thread.
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
     * Subclass hook invoked when finishing script execution from within the snippet thread.
     *
     * <p>
     * This method must not lock this instance or deadlock will result.
     *
     * <p>
     * The implementation in {@link JavaBox} does nothing.
     */
    protected void finishingExecution(Object result, Throwable error) {
    }

// SnippetThreadInfo

    private record SnippetThreadInfo(JavaBox box, List<ExecutionContext> executionContexts) {

        SnippetThreadInfo(JavaBox box) {
            this(box, new ArrayList<>(box.containerContexts.size()));
        }
    }

// SnippetReturn

    private record SnippetReturn(Object result, Throwable error) {

        static SnippetReturn empty() {
            return new SnippetReturn(null, null);
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
