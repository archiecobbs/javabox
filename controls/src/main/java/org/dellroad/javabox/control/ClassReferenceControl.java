
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox.control;

import com.google.common.base.Preconditions;

import java.lang.classfile.constantpool.ClassEntry;
import java.util.function.Predicate;

/**
 * A simple {@link ConstantPoolControl} that restricts which classes can be referenced by {@link ClassEntry}'s.
 */
public class ClassReferenceControl extends ConstantPoolControl {

    protected final Predicate<? super ClassEntry> test;

    /**
     * Constructor.
     *
     * @param test returns true if the provided class reference class is allowed
     * @throws IllegalArgumentException if {@code test} is null
     */
    public ClassReferenceControl(Predicate<? super ClassEntry> test) {
        Preconditions.checkArgument(test != null, "null test");
        this.test = test;
    }

// ConstantPoolControl

    @Override
    protected void checkClassEntry(ClassEntry entry) {
        if (!this.test.test(entry))
            throw new IllegalPoolEntryException(entry);
    }
}
