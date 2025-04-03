
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox.control;

import java.lang.classfile.constantpool.PoolEntry;

import org.dellroad.javabox.ControlViolationException;

/**
 * Exception thrown when an illegal entry is found in the constant pool.
 */
@SuppressWarnings("serial")
public class IllegalPoolEntryException extends ControlViolationException {

    private final PoolEntry poolEntry;

    public IllegalPoolEntryException(PoolEntry poolEntry) {
        this(poolEntry, "illegal constant pool entry: " + poolEntry);
    }

    public IllegalPoolEntryException(PoolEntry poolEntry, String message) {
        super(message);
        this.poolEntry = poolEntry;
    }

    public PoolEntry getPoolEntry() {
        return this.poolEntry;
    }
}
