
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox.execution;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import jdk.jshell.execution.DirectExecutionControl;
import jdk.jshell.execution.LoaderDelegate;
import jdk.jshell.execution.LocalExecutionControl;

/**
 * A {@link LocalExecutionControl} that provides the ability to add thread-local context to the execution of
 * each JShell snippet.
 *
 * <p>
 * It is sometimes useful to have the execution of JShell snippets occur within some thread-local context. For example,
 * a database application might want to open a transaction around each snippet execution. This is somewhat tricky because
 * JShell's {@link LocalExecutionControl} executes snippets in a different thread from the main command loop thread so,
 * for example, using {@link LocalExecutionControl#clientCodeEnter clientCodeEnter()} and
 * {@link LocalExecutionControl#clientCodeLeave clientCodeLeave()} won't work.
 *
 * <p>
 * In addition, the context may also be necessary during the conversion of the snippet result into a {@link String} for display
 * in the JShell console. This conversion is performed by {@link DirectExecutionControl#valueString} and is normally done
 * later, in a separate thread. This class performs this conversion earlier, in the same thread that executes the snippet,
 * while the context is still available.
 *
 * <p>
 * The methods a subclass can use to bracket snippet execution and {@link String} result decoding are {@link #enterContext}
 * and {@link #leaveContext leaveContext()}; the latter method also provides access the actual snippet return value, rather
 * than just its {@link String} representation.
 */
public class LocalContextExecutionControl extends LocalExecutionControl {

    // Used for the invokeWrapper() hack
    private static final InheritableThreadLocal<Invocation> CURRENT_INVOCATION = new InheritableThreadLocal<>();
    private static final Method INVOKE_WRAPPER_METHOD;
    static {
        try {
            INVOKE_WRAPPER_METHOD = LocalContextExecutionControl.class.getDeclaredMethod("invokeWrapper");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("internal error");
        }
    }

    // Reentrancy detector (it should never happen but just in case)
    private final AtomicBoolean executing = new AtomicBoolean();

// Constructor

    /**
     * Constructor.
     *
     * @param delegate loader delegate
     */
    public LocalContextExecutionControl(LoaderDelegate delegate) {
        super(delegate);
    }

// LocalExecutionControl

    // This is a total hack. Our goal is to access this LocalContextExecutionControl from within the thread
    // that is actually invoking the target method (which is different from the current thread here). The problem
    // is the target method must be a static method, so we have to stash "this" somewhere. To accomplish that
    // we invoke invokeWrapper() instead of "method" after storing "this" and "method" in an InheritableThreadLocal.
    // This works because the thread that will execute invokeWrapper() is a child of the current thread.
    // See also: https://bugs.openjdk.org/browse/JDK-8353487
    @Override
    protected String invoke(Method method) throws Exception {
        if (CURRENT_INVOCATION.get() != null)
            throw new IllegalStateException("reentrant execution");
        CURRENT_INVOCATION.set(new Invocation(this, method));
        try {
            return super.invoke(INVOKE_WRAPPER_METHOD);
        } finally {
            CURRENT_INVOCATION.remove();
        }
    }

// Subclass Hooks

    /**
     * Enter a new thread-local context for snippet execution.
     *
     * <p>
     * The implementation in {@link LocalContextExecutionControl} does nothing.
     */
    protected void enterContext() {
    }

    /**
     * Exit the thread-local context previously entered by {@link #enterContext} in the current thread.
     *
     * <p>
     * The implementation in {@link LocalContextExecutionControl} does nothing.
     *
     * @param returnValue the return value from the successful execution of an expression snippet, otherwise null
     * @param error the exception thrown by snippet execution if there was an error, otherwise null
      */
    protected void leaveContext(Object returnValue, Throwable error) {
    }

// Internal Methods

    /**
     * Execute the given snippet in context.
     *
     * <p>
     * The implementation in {@link LocalContextExecutionControl} uses {@link #enterContext} and
     * {@link #leaveContext leaveContext()} to bracket the execution of the snippet and
     * the decoding of its result (via {@link #valueStringWithContext valueStringWithContext()}).
     * The decoded result is wrapped in a {@link StringWrapper} to prevent duplicate decoding by JShell.
     *
     * @param method static snippet method
     * @return result from snippet execution
     */
    protected Object invokeWithContext(Method method) throws Throwable {
        if (!this.executing.compareAndSet(false, true))
            throw new IllegalStateException("reentrant execution");
        try {
            Object result = null;
            Throwable error = null;
            this.enterContext();
            try {
                result = method.invoke(null);
                return Optional.ofNullable(result)
                  .map(this::valueStringWithContext)
                  .map(StringWrapper::new)
                  .orElse(null);
            } catch (InvocationTargetException e) {
                error = e.getCause();
                throw error;
            } catch (Throwable t) {
                error = t;
                throw error;
            } finally {
                this.leaveContext(result, error);
            }
        } finally {
            this.executing.set(false);
        }
    }

    /**
     * Decode the snippet result into a {@link String} for display in the JShell console.
     *
     * <p>
     * The implementation in {@link LocalContextExecutionControl} just invokes {@link DirectExecutionControl#valueString}.
     */
    protected String valueStringWithContext(Object result) {
        return DirectExecutionControl.valueString(result);
    }

// Invoke Wrapper

    /**
     * Invocation wrapper method.
     *
     * <p>
     * This method recovers the current instance and the actual method to invoke
     * and then delegates to {@link #invokeWithContext invokeWithContext()}.
     *
     * <p>
     * This method is only used internally but is required to be public due to Java access controls.
     *
     * @return result from snippet execution
     * @throws IllegalStateException if not invoked by JShell
     */
    public static Object invokeWrapper() throws Throwable {
        final Invocation info = CURRENT_INVOCATION.get();
        if (info == null)
            throw new IllegalStateException("internal error");
        return info.control().invokeWithContext(info.method());
    }

// Invocation

    private record Invocation(LocalContextExecutionControl control, Method method) { }
}
