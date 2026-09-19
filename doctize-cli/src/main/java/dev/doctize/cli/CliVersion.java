package dev.doctize.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import picocli.CommandLine.IVersionProvider;

/** Reports the version the tool was built as, which the build writes into {@code version.properties}. */
final class CliVersion implements IVersionProvider {

    private static final String RESOURCE = "version.properties";

    @Override
    public String[] getVersion() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = CliVersion.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                properties.load(in);
            }
        }
        return new String[] {"doctize " + properties.getProperty("version", "(unknown version)")};
    }
}
