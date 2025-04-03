
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox.control;

import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantDynamicEntry;
import java.lang.classfile.constantpool.DoubleEntry;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.FloatEntry;
import java.lang.classfile.constantpool.IntegerEntry;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.InvokeDynamicEntry;
import java.lang.classfile.constantpool.LongEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.classfile.constantpool.MethodTypeEntry;
import java.lang.classfile.constantpool.ModuleEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.classfile.constantpool.PackageEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ClassDesc;

import org.dellroad.javabox.Control;

/**
 * A JavaBox {@link Control} that restricts the what's allowed to appear in the constant pool.
 *
 * <p>
 * This class inspects all constant pool entries, passing them to {@link #checkPoolEntry checkPoolEntry()}
 * for validation. That method delegates to the appropriate type-specific method; the type-specific methods
 * in this class do not throw any exceptions, so {@link ConstantPoolControl} itself does not disallow anything.
 * Subclasses should overrided as needed.
 */
public class ConstantPoolControl implements Control {

    @Override
    public byte[] modifyBytecode(ClassDesc name, byte[] bytes) {
        ClassFile.of().parse(bytes).constantPool().forEach(this::checkPoolEntry);
        return bytes;
    }

    /**
     * Validate a constant pool entry.
     *
     * <p>
     * The implementation in {@link ConstantPoolControl} just delegates to the corresponding
     * type-specific validation method.
     *
     * @throws IllegalPoolEntryException if a disallowed symbolic reference is detected
     */
    protected void checkPoolEntry(PoolEntry entry) {
        switch (entry) {
        case ClassEntry e -> checkClassEntry(e);
        case ConstantDynamicEntry e -> checkConstantDynamicEntry(e);
        case DoubleEntry e -> checkDoubleEntry(e);
        case FieldRefEntry e -> checkFieldRefEntry(e);
        case FloatEntry e -> checkFloatEntry(e);
        case IntegerEntry e -> checkIntegerEntry(e);
        case InterfaceMethodRefEntry e -> checkInterfaceMethodRefEntry(e);
        case InvokeDynamicEntry e -> checkInvokeDynamicEntry(e);
        case LongEntry e -> checkLongEntry(e);
        case MethodHandleEntry e -> checkMethodHandleEntry(e);
        case MethodRefEntry e -> checkMethodRefEntry(e);
        case MethodTypeEntry e -> checkMethodTypeEntry(e);
        case ModuleEntry e -> checkModuleEntry(e);
        case NameAndTypeEntry e -> checkNameAndTypeEntry(e);
        case PackageEntry e -> checkPackageEntry(e);
        case StringEntry e -> checkStringEntry(e);
        case Utf8Entry e -> checkUtf8Entry(e);
        }
    }

    protected void checkClassEntry(ClassEntry entry) {
    }

    protected void checkConstantDynamicEntry(ConstantDynamicEntry entry) {
    }

    protected void checkDoubleEntry(DoubleEntry entry) {
    }

    protected void checkFieldRefEntry(FieldRefEntry entry) {
    }

    protected void checkFloatEntry(FloatEntry entry) {
    }

    protected void checkIntegerEntry(IntegerEntry entry) {
    }

    protected void checkInterfaceMethodRefEntry(InterfaceMethodRefEntry entry) {
    }

    protected void checkInvokeDynamicEntry(InvokeDynamicEntry entry) {
    }

    protected void checkLongEntry(LongEntry entry) {
    }

    protected void checkMethodHandleEntry(MethodHandleEntry entry) {
    }

    protected void checkMethodRefEntry(MethodRefEntry entry) {
    }

    protected void checkMethodTypeEntry(MethodTypeEntry entry) {
    }

    protected void checkModuleEntry(ModuleEntry entry) {
    }

    protected void checkNameAndTypeEntry(NameAndTypeEntry entry) {
    }

    protected void checkPackageEntry(PackageEntry entry) {
    }

    protected void checkStringEntry(StringEntry entry) {
    }

    protected void checkUtf8Entry(Utf8Entry entry) {
    }
}
