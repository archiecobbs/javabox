
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox.control;

import com.google.common.base.Preconditions;

import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantDynamicEntry;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.InvokeDynamicEntry;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.classfile.constantpool.MethodTypeEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A simple {@link ConstantPoolControl} that restricts which classes can be referenced by {@link ClassEntry}'s.
 */
public class ClassReferenceControl extends ConstantPoolControl {

    protected final Predicate<? super ClassDesc> test;

    /**
     * Constructor.
     *
     * <p>
     * References to Java classes found in bytecode are validity checked using the given predicate.
     * References to array types are allowed when the base type is allowed; references to primitive
     * types are always allowed. Therefore, the {@code test} may assume that {@link ClassDesc#isClassOrInterface}
     * always returns true for the types it sees.
     *
     * @param test returns true if the provided class reference is allowed
     * @throws IllegalArgumentException if {@code test} is null
     */
    public ClassReferenceControl(Predicate<? super ClassDesc> test) {
        Preconditions.checkArgument(test != null, "null test");
        this.test = test;
    }

    /**
     * Delegates to {@link #checkClassDesc checkClassDesc()} after invoking {@link ClassEntry#asSymbol}.
     *
     * @param entry class constant pool entry
     * @param poolEntry originating constant pool entry
     */
    protected void checkClassEntry(ClassEntry entry, PoolEntry poolEntry) {
        Preconditions.checkArgument(entry != null, "null entry");
        this.checkClassDesc(entry.asSymbol(), poolEntry);
    }

    /**
     * Invoke the predicate provided to the constructor on the given class descriptor.
     *
     * @param poolEntry originating constant pool entry
     * @param desc class reference
     * @throws IllegalPoolEntryException if disallowed
     * @throws IllegalArgumentException if either parameter is null
     */
    protected void checkClassDesc(ClassDesc desc, PoolEntry poolEntry) {
        Preconditions.checkArgument(desc != null, "null desc");
        Preconditions.checkArgument(poolEntry != null, "null poolEntry");
        if (desc.isPrimitive())
            return;
        if (desc.isArray()) {
            this.checkClassDesc(desc.componentType(), poolEntry);
            return;
        }
        if (!this.test.test(desc))
            throw new IllegalPoolEntryException(poolEntry);
    }

    /**
     * Invoke the predicate provided to the constructor on the classes referenced in the given method type descriptor.
     *
     * @param desc method type descriptor
     * @param poolEntry originating constant pool entry
     * @throws IllegalPoolEntryException if disallowed
     * @throws NullPointerException if either parameter is null
     */
    protected void checkMethodTypeDesc(MethodTypeDesc desc, PoolEntry poolEntry) {
        Stream.concat(Stream.of(desc.returnType()), desc.parameterList().stream())
          .forEach(classDesc -> this.checkClassDesc(classDesc, poolEntry));
    }

// ConstantPoolControl

    @Override
    protected void checkClassEntry(ClassEntry entry) {
        this.checkClassEntry(entry, entry);
    }

    @Override
    protected void checkConstantDynamicEntry(ConstantDynamicEntry entry) {
        this.checkClassDesc(entry.typeSymbol(), entry);
    }

    @Override
    protected void checkFieldRefEntry(FieldRefEntry entry) {
        this.checkClassDesc(ClassDesc.ofDescriptor(entry.nameAndType().type().stringValue()), entry);
    }

    @Override
    protected void checkInterfaceMethodRefEntry(InterfaceMethodRefEntry entry) {
        this.checkMethodTypeDesc(MethodTypeDesc.ofDescriptor(entry.nameAndType().type().stringValue()), entry);
    }

    @Override
    protected void checkInvokeDynamicEntry(InvokeDynamicEntry entry) {
        this.checkMethodTypeDesc(entry.typeSymbol(), entry);
    }

    @Override
    protected void checkMethodRefEntry(MethodRefEntry entry) {
        this.checkMethodTypeDesc(MethodTypeDesc.ofDescriptor(entry.nameAndType().type().stringValue()), entry);
    }

    @Override
    protected void checkMethodTypeEntry(MethodTypeEntry methodTypeEntry) {
        this.checkMethodTypeDesc(methodTypeEntry.asSymbol(), methodTypeEntry);
    }
}
