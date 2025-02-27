
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import java.util.List;

import jdk.jshell.spi.ExecutionControl;
import jdk.jshell.spi.ExecutionControl.ClassBytecodes;

/**
 * Callback interface for controlling and modifying the scripts applied to a {@link JavaBox}.
 */
public interface ScriptFilter {

    /**
     * Apply controls and/or modifications to the given class file which is to be applied to a {@link JavaBox}.
     *
     * <p>
     * The returned list of classfiles will be the ones actually {@linkplain ExecutionControl#load loaded}
     * into the {@link JavaBox}. The list may contain classes that have already been loaded; these will be
     * {@linkplain ExecutionControl#redefine redefined}, but note, the default {@link ExecutionControl}
     * does not support class redefinition so that can result in an exception being thrown.
     *
     * @param classfile the classfile to be added
     * @return replacement classfile(s)
     * @throws AccessViolation if the script is attempting to do something disallowed by this filter
     * @throws JavaBoxException if some other error occurs
     */
    List<ClassBytecodes> filter(ClassBytecodes classfile);

    /**
     * Get the identity filter, i.e., the filter which makes no changes and allows anything.
     *
     * @return do-nothing filter
     */
    static ScriptFilter identity() {
        return List::of;
    }
}
