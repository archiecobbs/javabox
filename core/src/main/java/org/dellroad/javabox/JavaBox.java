
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import com.google.common.base.Preconditions;

import java.io.Closeable;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
import static org.dellroad.javabox.SnippetOutcome.CompilerErrors;
import static org.dellroad.javabox.SnippetOutcome.ExceptionThrown;
import static org.dellroad.javabox.SnippetOutcome.HaltsScript;
import static org.dellroad.javabox.SnippetOutcome.Interrupted;
import static org.dellroad.javabox.SnippetOutcome.Overwritten;
import static org.dellroad.javabox.SnippetOutcome.Skipped;
import static org.dellroad.javabox.SnippetOutcome.Successful;
import static org.dellroad.javabox.SnippetOutcome.SuccessfulWithValue;
import static org.dellroad.javabox.SnippetOutcome.Suspended;
import static org.dellroad.javabox.SnippetOutcome.UnresolvedReferences;
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
 * It is also possible to {@link #validate validate()} a script without executing it. This performs basic syntax
 * checking and allows the caller to filter which types of snippets are allowed.
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
 * Instead, suspeneded scripts must be resumed via {@link #resume resume()} and allowed to terminate.
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

    private static final String UNASSOCIATED_ID = "*UNASSOCIATED*";     // defined in jdk.jshell.Snippet.java

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final Config config;
    private final ArrayList<ContainerContext> containerContexts = new ArrayList<>();

    private State state = State.INITIAL;
    private ExecutorService executor;
    private JShell jshell;

    // The value of some variable being set by setVariable()
    private AtomicReference<Object> variableValue;

    // Execution state
    private ProcessInfo processInfo;
    private boolean interrupted;

    private final AtomicReference<Suspended> suspendOutcome = new AtomicReference<>();
    private final AtomicReference<Object> resumeReturnValue = new AtomicReference<>();
    private final AtomicReference<CurrentSnippet> currentSnippet = new AtomicReference<>();
    private final AtomicReference<SnippetResult> snippetResult = new AtomicReference<>();

/*

    State management is complicated because there are 3+ different threads that can be running
    through a single JavaBox instance at the same time:

        (a) One or more USER THREADS invoking execute(), resume(), interrupt(), close(), etc.
        (b) The EXECUTION THREAD which runs on "executor" and invokes JShell.eval() from doExecute()
        (c) The SNIPPET THREAD created by JShell that actually executes snippet code, which invokes
            startExecution() and finishExecution().

*/

    private enum State {

        // The initial state, prior to initialize()
        INITIAL {

            @Override
            void checkInvariants(JavaBox box) {
                Preconditions.checkState(box.executor == null);
                Preconditions.checkState(box.jshell == null);
                Preconditions.checkState(box.processInfo == null);
                Preconditions.checkState(!box.interrupted);
                Preconditions.checkState(box.suspendOutcome.get() == null);
                Preconditions.checkState(box.resumeReturnValue.get() == null);
                Preconditions.checkState(box.currentSnippet.get() == null);
                Preconditions.checkState(box.snippetResult.get() == null);
            }
        },

        // Initialized but otherwise not doing anything
        IDLE {

            @Override
            void checkInvariants(JavaBox box) {
                Preconditions.checkState(box.executor != null);
                Preconditions.checkState(box.jshell != null);
                Preconditions.checkState(box.processInfo == null);
                Preconditions.checkState(!box.interrupted);
                Preconditions.checkState(box.suspendOutcome.get() == null);
                Preconditions.checkState(box.resumeReturnValue.get() == null);
                Preconditions.checkState(box.currentSnippet.get() == null);
                Preconditions.checkState(box.snippetResult.get() == null);
            }
        },

        // Executing a script: the user thread is waiting for a result from execute() or resume(), the execution thread
        // is either compiling a snippet or waiting for JShell.eval() to return, and (in the latter case) the snippet thread
        // is executing script code. Also, the "interrupted" flag may be set; if so, JShell.stop() will have been invoked
        // at least once. We can also get to this state from SUSPENDED if some user thread invokes interrupt().
        EXECUTING {

            @Override
            void checkInvariants(JavaBox box) {
                Preconditions.checkState(box.executor != null);
                Preconditions.checkState(box.jshell != null);
                Preconditions.checkState(box.processInfo != null);
                Preconditions.checkState(box.suspendOutcome.get() == null);
                Preconditions.checkState(box.resumeReturnValue.get() == null);
            }
        },

        // Follows EXECUTING after JShell.eval() has returned in the execution thread and that thread has added the appropriate
        // SnippetOutcome and exited. We are waiting for the user thread that invoked execute() or resume() to wake up and
        // return a corresponding ScriptResult. After that the state reverts back to IDLE.
        RETURNING {

            @Override
            void checkInvariants(JavaBox box) {
                Preconditions.checkState(box.executor != null);
                Preconditions.checkState(box.jshell != null);
                Preconditions.checkState(box.processInfo != null);
                Preconditions.checkState(!box.interrupted);
                Preconditions.checkState(box.suspendOutcome.get() == null);
                Preconditions.checkState(box.resumeReturnValue.get() == null);
                Preconditions.checkState(box.currentSnippet.get() == null);
                Preconditions.checkState(box.snippetResult.get() == null);
            }
        },

        // Follows EXECUTING after an invocation of suspend() by the snippet thread. The script thread will be blocked in
        // suspend() and the "suspendOutcome" holds the temporary outcome. The execution thread will stay blocked waiting for
        // JShell.eval() to return. The user thread will wake up, copy the current snippetOutcomes list, append "suspendOutcome",
        // and return a corresponding ScriptResult. The state will change to SUSPENDED.
        // Note: if "interrupted" is set when suspend() is invoked, an immediate ThreadDeath exception is thrown, so it's
        // not possible for "interrupted" to be set in this state except transiently during doSuspend().
        SUSPENDING {

            @Override
            void checkInvariants(JavaBox box) {
                Preconditions.checkState(box.executor != null);
                Preconditions.checkState(box.jshell != null);
                Preconditions.checkState(box.processInfo != null);
                Preconditions.checkState(box.suspendOutcome.get() != null);
                Preconditions.checkState(box.resumeReturnValue.get() == null);
                Preconditions.checkState(box.currentSnippet.get() != null);
                Preconditions.checkState(box.snippetResult.get() == null);
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
                Preconditions.checkState(box.suspendOutcome.get() == null);
                Preconditions.checkState(box.resumeReturnValue.get() == null);
                Preconditions.checkState(box.currentSnippet.get() != null);
                Preconditions.checkState(box.snippetResult.get() == null);
            }
        },

        // Same as SUSPENDED but resume() has been invoked and we are waiting for the snippet thread to wake up and return
        // "resumeReturnValue" from suspend(). If "interrupted" is set then suspend() with throw ThreadDeath instead.
        // Either way, after that we will go back to EXECUTING.
        RESUMING {

            @Override
            void checkInvariants(JavaBox box) {
                Preconditions.checkState(box.executor != null);
                Preconditions.checkState(box.jshell != null);
                Preconditions.checkState(box.processInfo != null);
                Preconditions.checkState(box.suspendOutcome.get() == null);
                Preconditions.checkState(box.currentSnippet.get() != null);
                Preconditions.checkState(box.snippetResult.get() == null);
            }
        },

        // Closed and no longer usable
        CLOSED {

            @Override
            void checkInvariants(JavaBox box) {
                Preconditions.checkState(box.executor == null);
                Preconditions.checkState(box.jshell == null);
                Preconditions.checkState(box.processInfo == null);
                Preconditions.checkState(!box.interrupted);
                Preconditions.checkState(box.suspendOutcome.get() == null);
                Preconditions.checkState(box.resumeReturnValue.get() == null);
                Preconditions.checkState(box.snippetResult.get() == null);
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

            // Initialize control contexts (including our own, which goes first)
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
        this.checkVariableName(varName);

        // Get the variable
        SnippetOutcome outcome = this.execute(varName).snippetOutcomes().get(0);
        if (outcome instanceof SuccessfulWithValue success)
            return success.returnValue();
        if (outcome instanceof CompilerErrors)
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
        this.checkVariableName(varName);

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
            if (!(outcome instanceof Successful))
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
          .orElseThrow(() -> new IllegalStateException("the current thread is not a setVariable() script thread"))
          .get();
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
     * Execute the given script in this container.
     *
     * <p>
     * This is a convenience method, equivalent to: {@link #process process}{@code (source, true, null)}.
     *
     * @param source the script to execute
     * @return result from script execution
     * @see #process process()
     */
    public synchronized ScriptResult execute(String source) {
        return this.process(source, true, null);
    }

    /**
     * Perform basic validation of the given script but do not execute it.
     *
     * <p>
     * This is a convenience method, equivalent to: {@link #process process}{@code (source, false, validator)}.
     * If successful, all outcomes will be {@link Skipped}.
     *
     * @param source the script to execute
     * @param validator if not null, invoked to perform initial validation of each snippet in {@code source}
     * @return result from script validation
     * @see #process process()
     */
    public synchronized ScriptResult validate(String source, SnippetValidator validator) {
        return this.process(source, false, validator);
    }

    /**
     * Process the given script in this container.
     *
     * <p>
     * The script is broken into individual snippets, which are all validated and then, if {@code execute} is true, executed.
     *
     * <p><b>Initial Validation</b>
     *
     * <p>
     * Regardless of the value of {@code execute}, first a basic validation of all snippets is performed. For example, all
     * snippets must be parseable. In addition, if {@code validator} is non-null, then it is included in this basic validation
     * step for each snpipet. This is useful, for example, if you want to filter snippets by {@link Snippet.Kind}, for example,
     * to require that they include only declarations.
     *
     * <p>
     * The snippets that {@code validator} sees will be <i>unassociated</i> (as described by
     * {@link SourceCodeAnalysis#sourceToSnippets SourceCodeAnalysis.sourceToSnippets()}). If {@code validator} throws
     * a {@link SnippetValidationException}, the snippet's outcome is set to {@link ValidationFailure} and processing halts;
     * all preceding snippets (already validated) will have outcome {@link Skipped}, and those outcomes are returned
     * in a {@link ScriptResult}.
     *
     * <p><b>Execution</b>
     *
     * <p>
     * If all snippets pass initial validation, and {@code execute} is true, then they are executed one at a time. Executing
     * snippets may define variables, methods, and classes, and execute code. Execution stops when any snippet's execution
     * has an outcome implementing {@link HaltsScript} or there are no more snippets; the outcomes of all snippets that were
     * executed are then returned in a {@link ScriptResult}.
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
     * If this method is invoked when this instance already has a suspended script, an {@link IllegalStateException} is thrown.
     *
     * <p><b>Interruption</b>
     *
     * <p>
     * This method blocks until the script completes, suspends itself, or {@link #interrupt} is invoked (typically from
     * another thraed). Interrupting the current thread has the same effect as invoking {@link #interrupt}.
     *
     * @param source the script to process
     * @param execute true to execute the script, false to just perform initial validation
     * @param validator if not null, invoked to perform initial validation of each snippet in {@code source}
     * @return result from script processing
     * @throws InterruptedException if the current thread is interrupted or {@link #interrupt} is invoked
     * @throws IllegalStateException if this instance has a suspended script
     * @throws IllegalStateException if this instance is not initialized or closed
     * @throws IllegalArgumentException if {@code source} is null
     */
    public synchronized ScriptResult process(String source, boolean execute, SnippetValidator validator) {

        // Sanity check
        Preconditions.checkArgument(source != null, "null source");

        // Wait for previous operation to complete
        while (true) {
            this.checkAlive();
            this.checkInvariants();
            switch (this.state) {
            case IDLE:
                this.processInfo = new ProcessInfo(this.jshell, source, execute, validator);
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
                this.doExecute();
            } catch (Throwable t) {
                this.log.warn("error in execution task", t);
            }
        });

        // Wait for result
        return this.waitForResult();
    }

    /**
     * Suspend the script executing in the current thread.
     *
     * <p>
     * If a script invokes this method, the script will pause and the associated {@link #execute execute()}
     * or {@link #resume resume()} invocation that (re)started this script's execution will return to the caller,
     * with the corresponding snippet outcome being a {@link Suspended} containing the given {@code parameter}.
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
            final CurrentSnippet snippet = this.currentSnippet.get();
            this.suspendOutcome.set(new SnippetOutcomes.Suspended(this, snippet.offset, snippet.snippet, parameter));
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
                final Object returnValue = this.resumeReturnValue.get();
                this.resumeReturnValue.set(null);
                this.newState(State.EXECUTING);
                if (this.interrupted || wasInterrupted)
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
     * by any outcomes from the script's subsequent snippets.
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
                this.resumeReturnValue.set(returnValue);
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
                final List<SnippetOutcome> outcomes = List.copyOf(this.processInfo.snippetOutcomes());
                final ScriptResult result = new ScriptResult(this, this.processInfo.source(), outcomes);
                this.processInfo = null;
                this.newState(State.IDLE);
                return result;
            }
            case SUSPENDING: {
                final ArrayList<SnippetOutcome> outcomes = new ArrayList<>(this.processInfo.snippetOutcomes());
                outcomes.add(this.suspendOutcome.get());
                this.suspendOutcome.set(null);
                this.newState(State.SUSPENDED);
                return new ScriptResult(this, this.processInfo.source(), outcomes);
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
            this.interrupted = true;
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

    record SnippetInfo(String source, LineAndColumn offset, CompletionInfo completionInfo, AtomicReference<Snippet> snippet) {

        SnippetInfo(SourceCodeAnalysis sourceCodeAnalysis, LineAndColumn offset, String source) {
            this(sourceCodeAnalysis, offset, sourceCodeAnalysis.analyzeCompletion(source));
        }

        SnippetInfo(SourceCodeAnalysis sourceCodeAnalysis, LineAndColumn offset, CompletionInfo completionInfo) {
            this(fixupSource(completionInfo), offset, completionInfo,
              new AtomicReference<>(sourceCodeAnalysis.sourceToSnippets(completionInfo.source()).get(0)));
        }

        private static String fixupSource(CompletionInfo info) {
            String source = info.source();
            if (info.completeness() == Completeness.COMPLETE_WITH_SEMI)
                source = source.substring(0, source.length() - 1);
            return source;
        }
    }

    private void doExecute() throws Exception {

        // Avoid SpotBugs warnings
        final ProcessInfo pinfo;
        synchronized (this) {
            pinfo = this.processInfo;
        }

        // Break script into individual source snippets
        final ArrayList<SnippetInfo> snippetInfos = new ArrayList<>();
        {
            final SourceCodeAnalysis sourceCodeAnalysis = pinfo.jshell.sourceCodeAnalysis();
            LineAndColumn offset = LineAndColumn.initial();
            String remain = pinfo.source;
            while (!remain.trim().isEmpty()) {
                final SnippetInfo info = new SnippetInfo(sourceCodeAnalysis, offset, remain);
                snippetInfos.add(info);
                offset = offset.advance(info.source);
                remain = info.completionInfo.remaining();
            }
        }

        // Peform initial validation of each snippet
        int numValidated = 0;
        SnippetOutcome validationError = null;
        while (validationError == null && numValidated < snippetInfos.size()) {
            final SnippetInfo info = snippetInfos.get(numValidated);

            // Validate the next snippet
            switch (info.completionInfo.completeness()) {
            case CONSIDERED_INCOMPLETE:
            case DEFINITELY_INCOMPLETE:
                validationError = new SnippetOutcomes.CompilerSyntaxErrors(this, info.offset, info.snippet.get(),
                  Collections.singletonList(new CompilerError(info.offset, "incomplete trailing statement")));
                break;
            case UNKNOWN:
                final List<SnippetEvent> eval = pinfo.jshell.eval(info.source);
                final SnippetEvent event;
                if (eval.size() != 1 || !Snippet.Status.REJECTED.equals((event = eval.get(0)).status()))
                    throw new JavaBoxException("internal error: " + eval);
                info.snippet.set(event.snippet());
                validationError = new SnippetOutcomes.CompilerSyntaxErrors(this,
                  info.offset, info.snippet.get(), JavaBox.toErrors(pinfo.jshell, info));
                break;
            case COMPLETE_WITH_SEMI:
            case COMPLETE:
            case EMPTY:
                if (pinfo.validator != null) {
                    try {
                        pinfo.validator.validate(info.snippet.get());
                    } catch (SnippetValidationException e) {
                        validationError = new SnippetOutcomes.ValidationFailure(this, info.offset, info.snippet.get(), e);
                        break;
                    }
                }
                numValidated++;
                break;
            default:
                throw new JavaBoxException("internal error");
            }
        }

        // If a snippet failed to validate, mark all preceding snippets as skipped, add the error outcome, and bail out
        if (validationError != null) {
            snippetInfos.subList(0, numValidated).stream()
              .map(info -> new SnippetOutcomes.Skipped(this, info.offset, info.snippet.get()))
              .forEach(pinfo.snippetOutcomes::add);
            pinfo.snippetOutcomes.add(validationError);

        // If only doing initial validation, mark all snippets as skipped
        } else if (!pinfo.execute) {
            snippetInfos.stream()
              .map(info -> new SnippetOutcomes.Skipped(this, info.offset, info.snippet.get()))
              .forEach(pinfo.snippetOutcomes::add);

        // Otherwise, proceed to execute each snippet
        } else {

            // Execute each snippet
            for (SnippetInfo info : snippetInfos) {
                final SnippetOutcome outcome = this.executeSnippet(pinfo.jshell, info);
                pinfo.snippetOutcomes.add(outcome);
                if (outcome instanceof HaltsScript)
                    break;
            }

            // Update the status of any snippets that changed due to subsequent snippets
            for (int i = 0; i < pinfo.snippetOutcomes.size(); i++) {
                final SnippetOutcome oldOutcome = pinfo.snippetOutcomes.get(i);
                final LineAndColumn offset = oldOutcome.offset();
                final Snippet snippet = oldOutcome.snippet();
                if (snippet.id().equals(UNASSOCIATED_ID))
                    continue;
                SnippetOutcome newOutcome = oldOutcome;
                switch (pinfo.jshell.status(snippet)) {
                case RECOVERABLE_DEFINED:
                case RECOVERABLE_NOT_DEFINED:
                    newOutcome = new SnippetOutcomes.UnresolvedReferences(this, offset, snippet);
                    break;
                case OVERWRITTEN:
                    if (oldOutcome instanceof Successful || oldOutcome instanceof UnresolvedReferences) {
                        newOutcome = new SnippetOutcomes.Overwritten(this, offset, snippet);
                        break;
                    }
                    if (oldOutcome instanceof Overwritten)
                        break;
                    throw new JavaBoxException("internal error: " + oldOutcome + " -> " + snippet);
                case VALID:
                    if (oldOutcome instanceof Successful || oldOutcome instanceof ExceptionThrown)
                        break;
                    if (oldOutcome instanceof UnresolvedReferences unresolved) {
                        newOutcome = new SnippetOutcomes.SuccessfulNoValue(this, offset, snippet);
                        break;
                    }
                    throw new JavaBoxException("internal error: " + oldOutcome + " -> " + snippet);
                default:
                    throw new JavaBoxException("internal error: " + oldOutcome + " -> " + snippet);
                }
                pinfo.snippetOutcomes.set(i, newOutcome);
            }
        }

        // Finish up
        synchronized (this) {

            // Update state
            this.checkAlive();
            this.checkInvariants();
            switch (this.state) {
            case EXECUTING:
                this.newState(State.RETURNING);
                break;
            default:
                throw new JavaBoxException("internal error: " + this.state);
            }

            // If we were interrupted and the last snippet threw ThreadDeath, then change its outcome to Interrupted
            if (this.interrupted) {
                final int lastIndex = pinfo.snippetOutcomes.size() - 1;
                if (lastIndex >= 0
                  && pinfo.snippetOutcomes.get(lastIndex) instanceof SnippetOutcomes.ExceptionThrown exceptionThrown
                  && exceptionThrown.exception() instanceof ThreadDeath) {
                    pinfo.snippetOutcomes.set(lastIndex,
                      new SnippetOutcomes.Interrupted(this, exceptionThrown.offset(), exceptionThrown.snippet()));
                }
                this.interrupted = false;
            }
        }
    }

    private SnippetOutcome executeSnippet(JShell jsh, SnippetInfo info) {

        // Invoke JShell with the snippet
        final List<SnippetEvent> events;
        final SnippetResult snippetResult;
        try {
            this.currentSnippet.set(new CurrentSnippet(info.offset, info.snippet.get()));
            events = jsh.eval(info.source);
            snippetResult = this.snippetResult.get();
        } catch (ControlViolationException e) {
            return new SnippetOutcomes.ControlViolation(this, info.offset, info.snippet.get(), e);
        } finally {
            this.snippetResult.set(null);
            this.currentSnippet.set(null);
        }

        // Find the snippet event that corresponds to the new snippet; there should be exactly one such event
        final SnippetEvent event = events.stream()
          .filter(e -> e.causeSnippet() == null)
          .reduce((e1, e2) -> {
            throw new JavaBoxException(String.format("internal error: multiple events: %s, %s", e1, e2));
          })
          .orElseThrow(() -> new JavaBoxException(String.format("internal error: no event in %s", events)));
        info.snippet.set(event.snippet());

        // Debug
//        if (this.log.isDebugEnabled())
//            this.log.debug("execute:\n  event={}\n  result={}", event, snippetResult);

        // Check snippet status
        Object returnValue = null;
        switch (event.status()) {
        case RECOVERABLE_DEFINED:
        case RECOVERABLE_NOT_DEFINED:
            return new SnippetOutcomes.UnresolvedReferences(this, info.offset, info.snippet.get());
        case VALID:
            Optional<Throwable> error = Optional.ofNullable(snippetResult)
              .map(SnippetResult::error)
              .or(() -> Optional.of(event).map(SnippetEvent::exception));
            if (error.isPresent())
                return new SnippetOutcomes.ExceptionThrown(this, info.offset, info.snippet.get(), error.get());
            switch (info.snippet.get().kind()) {
            case EXPRESSION:
            case VAR:
                returnValue = snippetResult.result();
                break;
            default:
                break;
            }
            return new SnippetOutcomes.SuccessfulWithValue(this, info.offset, info.snippet.get(), returnValue);
        case REJECTED:
            return new SnippetOutcomes.CompilerSemanticErrors(this, info.offset, info.snippet.get(), JavaBox.toErrors(jsh, info));
        default:
            throw new JavaBoxException("internal error: " + event);
        }
    }

    private static List<CompilerError> toErrors(JShell jsh, SnippetInfo info) {
        final CompilerError[] array = jsh.diagnostics(info.snippet.get())
          .sorted(Comparator.comparingLong(Diag::getPosition))
          .map(diag -> {
            final LineAndColumn diagOffset = info.offset.advance(info.source.substring(0, (int)diag.getPosition()));
            return new CompilerError(diagOffset, diag.getMessage(Locale.ROOT));
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
        Preconditions.checkState(this.snippetResult.get() == null, "internal error");

        // Set thread name (if not already set)
        final Thread thread = Thread.currentThread();
        if (!thread.getName().startsWith(THREAD_NAME_PREFIX))
            thread.setName(String.format("%s-Script-%d", THREAD_NAME_PREFIX, THREAD_NAME_INDEX.incrementAndGet()));

        // Debug
//        if (this.log.isDebugEnabled())
//            this.log.debug("startExecution(): result={}", this.snippetResult.get(), new Throwable("HERE"));

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
        Preconditions.checkState(this.snippetResult.get() == null, "internal error");
        try {

            // Snapshot SnippetResult
            this.snippetResult.set(new SnippetResult(result, error));

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
//                this.log.debug("finishExecution(): result={}", this.snippetResult.get());

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

// ProcessInfo

    private record ProcessInfo(JShell jshell, String source, boolean execute,
            SnippetValidator validator, List<SnippetOutcome> snippetOutcomes) {
        ProcessInfo(JShell jshell, String source, boolean execute, SnippetValidator validator) {
            this(jshell, source, execute, validator, new ArrayList<>());
        }
    }

// CurrentSnippet

    private record CurrentSnippet(LineAndColumn offset, Snippet snippet) { }

// SnippetThreadInfo

    private record SnippetThreadInfo(JavaBox box, List<ExecutionContext> executionContexts) {

        SnippetThreadInfo(JavaBox box) {
            this(box, new ArrayList<>(box.containerContexts.size()));
        }
    }

// SnippetResult

    private record SnippetResult(Object result, Throwable error) {

        static SnippetResult empty() {
            return new SnippetResult(null, null);
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
