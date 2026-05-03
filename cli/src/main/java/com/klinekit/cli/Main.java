package com.klinekit.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "klinekit",
        mixinStandardHelpOptions = true,
        version = "klinekit 0.1.0 (M1)",
        description = "Java backtest engine for crypto strategies.",
        subcommands = { BacktestCommand.class }
)
public final class Main implements Runnable {

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }

    public static void main(String[] args) {
        int exit = new CommandLine(new Main()).execute(args);
        System.exit(exit);
    }
}
