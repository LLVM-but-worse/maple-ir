package org.mapleir;

import org.mapleir.cli.cmd.MainCommand;
import org.mapleir.cli.cmd.RunCommand;
import org.mapleir.cli.cmd.VersionCommand;
import picocli.CommandLine;

public class Main {
    public static final String VERSION = Main.class.getPackage().getImplementationVersion();

    public static void main(final String[] args) {
        final int exitCode = new CommandLine(new MainCommand())
                .addSubcommand("run", new RunCommand())
                .addSubcommand("version", new VersionCommand())
                .execute(args);
        System.exit(exitCode);
    }
}
