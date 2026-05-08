package com.allanvital.maestrao.model;

/**
 * @author Allan Vital (https://allanvital.com)
 */
public enum JobShell {
    BASH("Bash", "bash"),
    SH("Sh", "sh");

    private final String label;
    private final String program;

    JobShell(String label, String program) {
        this.label = label;
        this.program = program;
    }

    public String getLabel() {
        return label;
    }

    public String getProgram() {
        return program;
    }
}
