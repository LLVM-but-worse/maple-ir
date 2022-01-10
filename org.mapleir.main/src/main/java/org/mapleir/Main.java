package org.mapleir;

import org.mapleir.cli.cmd.MainCommand;
import org.mapleir.cli.cmd.RunCommand;
import org.mapleir.cli.cmd.VersionCommand;
import picocli.CommandLine;

public class Main {
    public static final String VERSION = Main.class.getPackage().getImplementationVersion();
    public static final String LOGO = "" +
            "    __  ___            __           ________ \n" +
            "   /  |/  /___ _____  / /__        /  _/ __ \\\n" +
            "  / /|_/ / __ `/ __ \\/ / _ \\______ / // /_/ /\n" +
            " / /  / / /_/ / /_/ / /  __/_____// // _, _/ \n" +
            "/_/  /_/\\__,_/ .___/_/\\___/     /___/_/ |_|  \n" +
            "            /_/                              \n";

    public static void main(final String[] args) {
        System.out.println(
                Main.LOGO
                        + "Maple-IR | Copyright (c) 2022 Bibl and rcx \n"

        );
        final int exitCode = new CommandLine(new MainCommand())
                .addSubcommand("run", new RunCommand())
                .addSubcommand("version", new VersionCommand())
                .execute(args);
        System.exit(exitCode);
    }
}
