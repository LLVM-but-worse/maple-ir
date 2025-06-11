package org.mapleir.cli.cmd;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.mapleir.Main;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "main",
        mixinStandardHelpOptions = true,
        version = "main 1.0",
        description = "Main parent command."
)
public class MainCommand implements Callable<Integer> {
    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    @Override
    public Integer call() throws Exception {
        System.out.println(
                "java -jar maple.jar run <args> | Run deobfuscation\n"
                + "java -jar maple.jar version    | View version\n"
        );
        return 0;
    }
}