
/*
 * Copyright (C) 2025 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.javabox.control;

import com.google.common.base.Preconditions;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Label;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.time.Duration;
import java.util.HashSet;

import org.dellroad.javabox.Control;
import org.dellroad.javabox.JavaBox;

/**
 * A JavaBox {@link Control} that limits the amount of time that a script snippet may execute.
 *
 * <p>
 * This class works by adding time checks to backward branches. Therefore, it only works when
 * the executing thread is actually executing (i.e., not blocked).
 *
 * @param timeLimit the maximum execution time allowed
 */
public record TimeLimitControl(Duration timeLimit) implements Control {

    private static final ClassDesc CHECK_METHOD_CLASS_DESC = ClassDesc.of(TimeLimitControl.class.getName());
    private static final String CHECK_METHOD_NAME = "check";

    /**
     * Constructor.
     *
     * @throws IllegalArgumentException if {@code timeLimit} is more than 2<super>63</super> nanoseconds
     * @throws IllegalArgumentException if {@code timeLimit} is null or negative
     */
    public TimeLimitControl {
        Preconditions.checkArgument(timeLimit != null, "null timeLimit");
        Preconditions.checkArgument(!timeLimit.isNegative(), "negative timeLimit");
        try {
            timeLimit.toNanos();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("timeLimit is too large");
        }
    }

    /**
     * Adds time limit checks to the bytecode.
     */
    @Override
    public byte[] modifyBytecode(ClassDesc name, byte[] bytes) {
        final ClassFile classFile = ClassFile.of();
        return classFile.transformClass(classFile.parse(bytes),
          ClassTransform.transformingMethodBodies(CodeTransform.ofStateful(TimeLimitWeaver::new)));
    }

    @Override
    public Long startExecution(ContainerContext context) {
        return System.nanoTime() + this.timeLimit().toNanos();
    }

    public static void check() {
        ExecutionContext executionContext = JavaBox.executionContextFor(TimeLimitControl.class);
        final long now = System.nanoTime();
        final long deadline = (Long)executionContext.context();
        final long overage = now - deadline;
        if (overage > 0) {
            final Duration timeLimit = ((TimeLimitControl)executionContext.containerContext().control()).timeLimit();
            throw new TimeLimitExceededException(timeLimit, timeLimit.plusNanos(overage));
        }
    }

// TimeLimitWeaver

    private static final class TimeLimitWeaver implements CodeTransform {

        private final HashSet<Label> priorLabels = new HashSet<>();

        @Override
        public void atStart(CodeBuilder builder) {
            checkTimeLimit(builder);
        }

        @Override
        public void accept(CodeBuilder builder, CodeElement element) {
            switch (element) {
                case LabelTarget target -> priorLabels.add(target.label());
                case BranchInstruction branch when priorLabels.contains(branch.target()) -> checkTimeLimit(builder);
                default -> { }
            }
            builder.with(element);
        };

        private void checkTimeLimit(CodeBuilder builder) {
            builder.invokestatic(CHECK_METHOD_CLASS_DESC, CHECK_METHOD_NAME, ConstantDescs.MTD_void);
        }
    }
}
