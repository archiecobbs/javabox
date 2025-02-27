
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox;

import com.google.common.base.Preconditions;

import java.util.Set;

import jdk.jshell.Snippet;

/**
 * A script that has been applied to a {@link JavaBox}.
 *
 * <p>
 * Instances are thread safe.
 */
public class Script {

    private final JavaBox box;
    private final String source;
    private final Set<Snippet> snippets;
    private final Object returnValue;

    /**
     * Constructor.
     *
     * @param source the Java source for the script
     * @throws IllegalArgumentException if {@code source} is null
     */
    Script(JavaBox box, String source, Set<Snippet> snippets, Object returnValue) {
        Preconditions.checkArgument(box != null, "null box");
        Preconditions.checkArgument(source != null, "null source");
        Preconditions.checkArgument(snippets != null, "null snippets");
        this.box = box;
        this.source = source;
        this.snippets = snippets;
        this.returnValue = returnValue;
    }

// Properties

    /**
     * Get the associated {@link JavaBox}.
     *
     * @return associated container
     */
    public JavaBox javaBox() {
        return this.box;
    }

    /**
     * Get the original script source.
     *
     * @return script source
     */
    public String source() {
        return this.source;
    }

    /**
     * Get the names of any unresolved dependencies in this script.
     *
     * <p>
     * This returns an up-to-date view, which can change as new scripts are added to the associated container.
     */
    public Set<String> unresolvedDependencies() {
        return this.box.unresolvedDependencies(this.snippets);
    }

    /**
     * Get the return value from this script.
     *
     * @return script return value
     */
    public Object returnValue() {
        return this.returnValue;
    }
}
