
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import jdk.jshell.execution.LoaderDelegate;
import jdk.jshell.execution.LocalExecutionControl;

import org.dellroad.javabox.execution.LocalContextExecutionControl;

/**
 * The {@link LocalExecutionControl} used by {@link JavaBox}.
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

    @Override
    public void load(ClassBytecodes[] cbcs) throws ClassInstallException, NotImplementedException, EngineTerminationException {
        for (ClassBytecodes cbc : cbcs) {
            cbcs = this.box.filter(cbc).toArray(ClassBytecodes[]::new);
            super.load(cbcs);
        }
    }

    @Override
    protected void enterContext() {
        this.box.enterContext();
    }

    @Override
    protected void leaveContext(Object result, Throwable error) {
        this.box.leaveContext(result, error);
    }

    // We don't ever actually use the string form of the result, so don't bother
    @Override
    protected String valueStringWithContext(Object result) {
        return "[ignored]";
    }
}
