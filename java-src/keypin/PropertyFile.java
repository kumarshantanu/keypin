package keypin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map.Entry;
import java.util.Properties;

public class PropertyFile {

    private static void exit(final Logger logger, final String format, Object...args) {
        final String message = String.format(format, args);
        logger.error(message);
        throw new IllegalArgumentException(message);
    }

    private static void info(final Logger logger, final String format, final Object...args) {
        final String message = String.format(format, args);
        logger.info(message);
    }

    public static Properties realize(final Properties props, final Logger logger) {
        final Properties result = new Properties();
        result.putAll(props);
        for (final Entry<Object, Object> pair: result.entrySet()) {
            final String key = (String) pair.getKey();
            result.setProperty(key, realize((String) pair.getValue(), props, logger));  // munge values
        }
        return result;
    }

    /**
     * Recursive variable substitution using property values, e.g. the following property definition
     * <tt>
     * baz=awesome-sauce
     * foo.bar=${baz}/quux
     * </tt>
     * will realize foo.bar as "awesome-sauce/auux".
     * @param template string template to be realized
     * @param props    properties to look up variable values
     * @return         realized string template
     */
    private static String realize(final String template, final Properties props, final Logger logger) {
        if (template == null) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        boolean var = false;
        String varName = "";
        for (int i = 0; i < template.length(); i++) {
            final char ch = template.charAt(i);
            if (var) {
                if (varName.isEmpty()) {
                    if (ch == '{') {
                        // ignore
                    } else if (Character.isJavaIdentifierStart(ch)) {
                        varName += ch;
                    } else {
                        exit(logger, "Error realizing template '%s', illegal start of variable name '%s'",
                                template, template.substring(i));
                    }
                } else {
                    if (ch == '}' || !(ch == '.' || ch == '-' || Character.isJavaIdentifierPart(ch))) {
                        if (props.containsKey(varName)) {
                            sb.append(realize(props.getProperty(varName), props, logger));
                        } else {
                            exit(logger, "Error realizing template '%s', property '%s' not found: ", template, varName);
                        }
                        var = false;
                        varName = "";
                        // push back the index - do not throw away current char
                        if (ch != '}') {
                            i--;
                        }
                    } else {
                        varName += ch;
                    }
                }
            } else {
                if (ch == '$') {
                    var = true;
                    varName = "";
                } else {
                    var = false;
                    varName = "";
                    sb.append(ch);
                }
            }
        }
        if (var) {
            if (props.containsKey(varName)) {
                sb.append(realize(props.getProperty(varName), props, logger));
            } else {
                throw new IllegalArgumentException("No such property found: " + varName);
            }
        }
        return sb.toString();
    }

    /**
     * Read configuration as {@link InputStream} from file or classpath resource.
     * @param configFilename
     * @return
     * @throws IOException
     */
    public static InputStream readConfigAsStream(final String configFilename, final Logger logger) throws IOException {
        info(logger, "Searching for config file '%s' in base directory '%s'",
                configFilename, new File(".").getAbsoluteFile().getParent());
        final File configFile = new File(configFilename);
        if (!configFile.exists()) {
            info(logger, "Config file '%s' not found on filesystem, searching now in classpath", configFilename);
            final InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(configFilename);
            if (in == null) {
                exit(logger, "Config file '%s' not found in the filesystem or classpath", configFilename);
            }
            info(logger, "Found in classpath");
            return in;
        }
        if (!configFile.isFile()) {
            exit(logger, "Config file '%s' is not a file", configFilename);
        }
        if (!configFile.canRead()) {
            exit(logger, "Config file '%s' is not readable", configFilename);
        }
        info(logger, "Found in filesystem");
        return new FileInputStream(configFile);
    }

    public static Properties readConfig(final String configFilename, final Logger logger) throws IOException {
        try (final InputStream in = readConfigAsStream(configFilename, logger)) {
            final Properties props = new Properties();
            props.load(in);
            return props;
        } catch (final IOException e) {
            logger.error(String.format("Error reading properties file '%s': %s", configFilename, e.getMessage()));
            throw e;
        }
    }

    public static Properties readCascadingConfig(final String configFile, final String parentKey, final Logger logger)
            throws IOException {
        final Properties props = readConfig(configFile, logger);
        final String baseConfigFiles = props.getProperty(parentKey);
        if (baseConfigFiles == null) {
            info(logger, "No parent (property '%s') found for config file '%s'", parentKey, configFile);
            return props;
        } else {
            info(logger, "Config file '%s' has parent config file(s): %s", configFile, baseConfigFiles);
            final String[] names = baseConfigFiles.split(",");
            // loop over each name and report error on empty name if any
            for (int i = 0; i < names.length; i++) {
                names[i] = names[i].trim();
                if (names[i].isEmpty()) {
                    exit(logger, "Config file '%s' has empty parent config filename: %s", configFile, baseConfigFiles);
                }
            }
            // construct result by resolving each parent and put into a common holder
            final Properties result = new Properties();
            for (final String eachBaseConfig: names) {
                final Properties eachBaseProps = readCascadingConfig(eachBaseConfig, parentKey, logger);
                result.putAll(eachBaseProps);
            }
            // child overrides all parents
            result.putAll(props);
            return result;
        }
    }

    public static Properties resolveConfig(final String configFile, final String parentKey, final Logger logger)
            throws IOException {
        return realize(readCascadingConfig(configFile, parentKey, logger), logger);
    }

    public static Properties resolveConfig(final String configFile, final Logger logger)
            throws IOException {
        return realize(readConfig(configFile, logger), logger);
    }

}
