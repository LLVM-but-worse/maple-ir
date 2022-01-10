package org.mapleir.cli.cmd;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.mapleir.Main;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "version",
        mixinStandardHelpOptions = true,
        version = "version 1.0",
        description = "Version command."
)
public class VersionCommand implements Callable<Integer> {
    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    @Override
    public Integer call() throws Exception {
        LOGGER.info(
                "Maple-IR | Copyright (c) 2022 Bibl and rcx \n"
                + "Running Maple-IR v" + Main.VERSION + "!"
        );
        return 0;
    }
}
