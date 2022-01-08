package org.mapleir;

import org.mapleir.cli.cmd.RunCommand;
import picocli.CommandLine;

public class Main {
    public static void main(final String[] args) {
        int exitCode = new CommandLine(new RunCommand()).execute(args);
        System.exit(exitCode);
    }
}
