
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import com.google.common.base.Preconditions;

/**
 * Represents a character offset into a {@link String}, along with associated line and column offsets.
 *
 * <p>
 * This class does not store the string itself, just the offset information within it.
 *
 * <p>
 * The character, line, and column offsets are zero-based. Columns are measured in codepoints rather than characters.
 *
 * <p>
 * Instances are mutable and not thread-safe.
 */
public class LineAndColumn implements Cloneable {

    private int offset;
    private int lineOffset;
    private int columnOffset;

    /**
     * Constructor.
     */
    public LineAndColumn() {
    }

    /**
     * Get the overall character offset.
     *
     * @return character offset (zero based)
     */
    public int offset() {
        return this.offset;
    }

    /**
     * Get the line offset.
     *
     * @return line offset (zero based)
     */
    public int lineOffset() {
        return this.lineOffset;
    }

    /**
     * Get the column offset.
     *
     * @return column offset (zero based)
     */
    public int columnOffset() {
        return this.columnOffset;
    }

    /**
     * Update the offsets by advancing through the given string.
     *
     * @param text string content
     * @throws IllegalArgumentException if {@code text} is null
     * @throws IllegalArgumentException if the offsets overflow
     */
    public void advance(String text) {
        Preconditions.checkArgument(text != null, "null text");
        final int newOffset = this.offset + text.length();
        if (newOffset < 0)
            throw new IllegalArgumentException("overflow");
        this.offset = newOffset;
        text.codePoints().forEach(ch -> {
            if (ch == '\n') {
                this.lineOffset++;
                this.columnOffset = 0;
            } else
                this.columnOffset++;
        });
    }

    /**
     * Reset this instance to all zero offsets.
     */
    public void reset() {
        this.offset = 0;
        this.lineOffset = 0;
        this.columnOffset = 0;
    }

// Cloneable

    /**
     * Clone this instance.
     */
    @Override
    public LineAndColumn clone() {
        try {
            return (LineAndColumn)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

// Object

    @Override
    public int hashCode() {
        return this.getClass().hashCode()
          ^ this.offset
          ^ (this.lineOffset << 10)
          ^ (this.columnOffset << 20);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj == null || obj.getClass() != this.getClass())
            return false;
        final LineAndColumn that = (LineAndColumn)obj;
        return this.offset == that.offset
          && this.lineOffset == that.lineOffset
          && this.columnOffset == that.columnOffset;
    }

    @Override
    public String toString() {
        return String.format("%s[offset=%d,line=%d,col=%d]",
          this.getClass().getSimpleName(), this.offset, this.lineOffset, this.columnOffset);
    }
}
