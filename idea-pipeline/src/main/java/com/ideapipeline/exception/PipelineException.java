package com.ideapipeline.exception;

import java.util.ArrayList;
import java.util.List;

// Note: Using standard Java class structure for exceptions for compilability.
// Lombok annotations are removed here as it's a simple class.
public class PipelineException extends RuntimeException {
    private final String phase;
    private final int loopCount;

    public PipelineException(String message, String phase, int loopCount) {
        super(message);
        this.phase = phase;
        this.loopCount = loopCount;
    }

    public PipelineException(String message, String phase, int loopCount, Throwable cause) {
        super(message, cause);
        this.phase = phase;
        this.loopCount = loopCount;
    }

    public String getPhase() {
        return phase;
    }

    public int getLoopCount() {
        return loopCount;
    }
}