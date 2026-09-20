package dev.doctize.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Entry point of the {@code doctize} command line tool. */
@Command(
        name = "doctize",
        description = "Generates a documentation site from annotated Java code and Markdown guides.",
        mixinStandardHelpOptions = true,
        versionProvider = CliVersion.class,
        subcommands = {GenerateCommand.class, InitCommand.class, LicenseCommand.class})
public final class DoctizeCli implements Runnable {

    public static void main(String[] args) {
        System.exit(new CommandLine(new DoctizeCli()).execute(args));
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
