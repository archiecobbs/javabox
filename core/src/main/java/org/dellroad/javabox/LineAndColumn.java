
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import com.google.common.base.Preconditions;

import java.util.Iterator;

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
 * Instances are immutable.
 */
public final class LineAndColumn {

    private static final LineAndColumn INITIAL = new LineAndColumn(0, 0, 0);

    private final int offset;
    private final int lineOffset;
    private final int columnOffset;

    private LineAndColumn(int offset, int lineOffset, int columnOffset) {
        this.offset = offset;
        this.lineOffset = lineOffset;
        this.columnOffset = columnOffset;
    }

    /**
     * Obtain an initial instance with all offsets set to zero.
     */
    public static LineAndColumn initial() {
        return LineAndColumn.INITIAL;
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
     * @return updated instance
     * @throws IllegalArgumentException if {@code text} is null
     * @throws IllegalArgumentException if the offsets would overflow
     */
    public LineAndColumn advance(String text) {
        Preconditions.checkArgument(text != null, "null text");
        final int offset = this.offset + text.length();
        if (offset < 0)
            throw new IllegalArgumentException("overflow");
        int lineOffset = this.lineOffset;
        int columnOffset = this.columnOffset;
        for (Iterator<Integer> i = text.codePoints().iterator(); i.hasNext(); ) {
            final int ch = i.next();
            if (ch == '\n') {
                lineOffset++;
                columnOffset = 0;
            } else
                columnOffset++;
        }
        return new LineAndColumn(offset, lineOffset, columnOffset);
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
