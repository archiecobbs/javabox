
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox.control;

import java.time.Duration;

import org.dellroad.javabox.ControlViolationException;

/**
 * Exception thrown when a script exceeds the time limit imposed by a {@link TimeLimitControl}.
 */
@SuppressWarnings("serial")
public class TimeLimitExceededException extends ControlViolationException {

    private final Duration timeLimit;
    private final Duration timeSpent;

    public TimeLimitExceededException(Duration timeLimit, Duration timeSpent) {
        this(timeLimit, timeSpent, String.format("time limit of %s exceeded", timeLimit));
    }

    public TimeLimitExceededException(Duration timeLimit, Duration timeSpent, String message) {
        super(message);
        this.timeLimit = timeLimit;
        this.timeSpent = timeSpent;
    }

    public Duration getTimeLimit() {
        return this.timeLimit;
    }

    public Duration getTimeSpent() {
        return this.timeSpent;
    }
}
