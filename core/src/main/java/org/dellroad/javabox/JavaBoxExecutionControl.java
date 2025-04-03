
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import jdk.jshell.execution.LoaderDelegate;
import jdk.jshell.execution.LocalExecutionControl;

import org.dellroad.javabox.execution.LocalContextExecutionControl;

/**
 * The {@link LocalExecutionControl} used by {@link JavaBox} itself.
 */
public class JavaBoxExecutionControl extends LocalContextExecutionControl {

    protected final JavaBox box;

// Constructor

    /**
     * Constructor.
     *
     * @param delegate loader delegate
     */
    public JavaBoxExecutionControl(LoaderDelegate delegate) {
        super(delegate);
        this.box = JavaBox.getCurrent();
    }

// LocalContextExecutionControl

    @Override
    public void load(ClassBytecodes[] cbcs) throws ClassInstallException, NotImplementedException, EngineTerminationException {

        // Clone array just to be safe
        cbcs = cbcs.clone();

        // Apply filters
        for (int i = 0; i < cbcs.length; i++)
            cbcs[i] = this.box.applyControls(cbcs[i]);

        // Load bytecode
        super.load(cbcs);
    }

    @Override
    protected void enterContext() {
        super.enterContext();
        this.box.startExecution();
    }

    @Override
    protected void leaveContext(Object result, Throwable error) {
        try {
            this.box.finishExecution(result, error);
        } finally {
            super.leaveContext(result, error);
        }
    }

    // We don't ever actually use the string form of the result, so don't bother
    @Override
    protected String valueStringWithContext(Object result) {
        return "[ignored]";
    }
}
