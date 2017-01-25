/* Copyright (c) Shantanu Kumar. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file LICENSE at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */


package keypin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class Config {

    public static Map<?, ?> readConfig(Iterable<? extends ConfigIO> readers, String filename,
            Logger logger) throws Exception {
        for (ConfigIO eachReader: readers) {
            if (eachReader.canRead(filename)) {
                // try filesystem
                do {
                    final File file = new File(filename);
                    info(logger, "Searching in filesystem: %s", file.getAbsolutePath());
                    if (file.isFile()) {
                        if (!file.canRead()) {
                            exit(logger, "File is not readable: %s", file.getAbsolutePath());
                        }
                        info(logger, "Found in filesystem, now reading config from: " + file.getAbsolutePath());
                        return Collections.unmodifiableMap(eachReader.readConfig(new FileInputStream(file)));
                    }
                } while (false);
                // try classpath
                do {
                    info(logger, "Not found in filesystem, now searching in classpath: %s", filename);
                    final InputStream resource =
                            Thread.currentThread().getContextClassLoader().getResourceAsStream(filename);
                    if (resource != null) {
                        logger.info("Found in classpath, now reading config from: " + filename);
                        return Collections.unmodifiableMap(eachReader.readConfig(resource));
                    }
                } while (false);
                exit(logger, "Found neither on the filesystem nor in classpath: %s", filename);
            }
        }
        exit(logger, "File '%s' cannot be read by any available config reader", filename);
        throw new IllegalStateException("Unreachable code");
    }

    public static void writeConfig(Iterable<ConfigIO> writers, String filename, Map<?, ?> config, boolean escape,
            Logger logger) throws Exception {
        for (ConfigIO eachWriter: writers) {
            if (eachWriter.canWrite(filename)) {
                try (final OutputStream out = new FileOutputStream(filename)) {
                    eachWriter.writeConfig(out, config, escape);
                }
                return;
            }
        }
        exit(logger, "File '%s' cannot be written by any available config writer", filename);
        throw new IllegalStateException("Unreachable code");
    }

    public static Map<?, ?> readCascadingConfig(Iterable<? extends ConfigIO> readers, Iterable<String> filenames,
            Object parentKey, Logger logger) throws Exception {
        final Map<Object, Object> config = new LinkedHashMap<>();
        for (String eachFilename: filenames) {
            Map<Object, Object> eachResultConfig = new LinkedHashMap<>();
            Map<?, ?> eachConfig = readConfig(readers, eachFilename, logger);
            if (eachConfig.containsKey(parentKey)) {
                Object parentValue = eachConfig.get(parentKey);
                if (parentValue == null) {
                    exit(logger, "NULL value found for the parent key %s", parentKey);
                }
                Iterable<String> parentFilenames = null;
                if (parentValue instanceof Iterable<?>) {
                    final List<String> parentValueItems = new ArrayList<>();
                    for (Object item: (Iterable<?>) parentValue) {
                        if (item instanceof String) {
                            parentValueItems.add((String) item);
                        } else {
                            expected(logger, "string filename", item);
                        }
                    }
                    parentFilenames = parentValueItems;
                } else if (parentValue instanceof String) {  // support string names for properties files
                    parentFilenames = trimmedTokens((String) parentValue, ",");
                } else {
                    expected(logger, "one or more filenames", parentValue);
                }
                // add parent config
                eachResultConfig.putAll(readCascadingConfig(readers, parentFilenames, parentKey, logger));
            }
            // add current config (after adding parent)
            eachResultConfig.putAll(eachConfig);
            // remove parent key after resolution
            eachResultConfig.remove(parentKey);
            // add current config to the final config
            config.putAll(eachResultConfig);
        }
        return Collections.unmodifiableMap(config);
    }

    public static Map<?, ?> realize(final Map<Object, Object> config, final Mapper mapper,
            final Logger logger) {
        final AtomicReference<Function> realizerHolder = new AtomicReference<>();
        Function realizer = new Function() {
            @Override
            public Object execute(Object each) {
                return realize(each, config, mapper, realizerHolder.get(), logger);
            }
        };
        realizerHolder.set(realizer);
        final Map<Object, Object> result = new LinkedHashMap<>(config);
        for (final Entry<Object, Object> pair: result.entrySet()) {
            final Object key = pair.getKey();
            result.put(key, realize(pair.getValue(), config, mapper, realizer, logger));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Return a literal value string for the specified string. If the token contains any variable, it would not be
     * evaluated after quoting.
     * @param token string value to be literalized
     * @return literal string replacement
     */
    public static String escape(String token) {
        if (token == null) {
            return null;
        }
        final int n = token.length();
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            char ch = token.charAt(i);
            if (ch == '$') {
                sb.append('\\');
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    // =============== helper functions =================

    private static Object realize(Object value, Map<Object, Object> lookup, Mapper mapper, Function realizer,
            Logger logger) {
        if (value instanceof String) {
            return realize((String) value, lookup, logger);
        } else if (value instanceof Collection<?>) {
            return mapper.processCollection((Collection<?>) value, realizer);
        } else if (value instanceof Map<?, ?>) {
            return mapper.processMap((Map<?, ?>) value, realizer, realizer);
        } else {
            return mapper.processValue(value);
        }
    }

    /**
     * Recursive variable substitution using property values, e.g. the following property definition
     * <tt>
     * baz=awesome-sauce
     * foo.bar=${baz}/quux
     * </tt>
     * will realize foo.bar as "awesome-sauce/auux".
     * @param template string template to be realized
     * @param lookup   properties to look up variable values
     * @return         realized string template
     */
    private static String realize(final String template, final Map<?, ?> lookup, final Logger logger) {
        if (template == null) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        boolean var = false;
        boolean varBracket = false;
        String varName = "";
        for (int i = 0; i < template.length(); i++) {
            final char ch = template.charAt(i);
            if (var) {
                if (varName.isEmpty()) {
                    if (ch == '{') {
                        if (!varBracket) {
                            varBracket = true;
                        } else {
                            exit(logger, "Error realizing template '%s', found nested '{' char in variable", template);
                        }
                    } else if (Character.isJavaIdentifierStart(ch)) {
                        varName += ch;
                    } else {
                        exit(logger, "Error realizing template '%s', illegal start of variable name '%s'",
                                template, template.substring(i));
                    }
                } else {
                    if ((varBracket && ch == '}') ||
                            !(ch == '.' || ch == '-' || Character.isJavaIdentifierPart(ch) || ch == '|')) {
                        sb.append(realize(readValue(varName, template, lookup, logger), lookup, logger));
                        var = false;
                        varBracket = false;
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
                    if (i > 0 && template.charAt(i - 1) == '\\') {  // escaped variable marker?
                        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\\') {
                            sb.setCharAt(sb.length() - 1, '$');  // omit escape char in buffer, append variable marker
                        }
                    } else {
                        var = true;
                        varName = "";
                    }
                } else {
                    var = false;
                    varName = "";
                    sb.append(ch);
                }
            }
        }
        if (var) {
            sb.append(realize(readValue(varName, template, lookup, logger), lookup, logger));
        }
        return sb.toString();
    }

    private static String readValue(String varName, String template, Map<?, ?> lookup, Logger logger) {
        // there may be several variables separated by '|' character, so tokenize and check
        final String[] tokens = varName.split(Pattern.quote("|"));
        if (tokens.length > 1) {
            info(logger, "(Template '%s', Expression '%s') Total %d variables found: %s",
                    template, varName, tokens.length, Arrays.toString(tokens));
        }
        for (final String eachName: tokens) {
            // try to read from environment variable
            final String eValue = System.getenv(eachName);
            if (eValue != null) {
                info(logger, "(Template '%s', Expression '%s') Variable '%s' resolved via environment lookup",
                        template, varName, eachName);
                return eValue;
            }
            if (lookup.containsKey(eachName)) {
                info(logger, "(Template '%s', Expression '%s') Variable '%s' resolved via property value",
                        template, varName, eachName);
                return String.valueOf(lookup.get(eachName));
            }
        }
        exit(logger, "Error realizing template '%s', no value found for property '%s'", template, varName);
        throw new IllegalStateException("Unreachable code");
    }

    private static List<String> trimmedTokens(String token, String regex) {
        String[] tokens = token.split(",");
        for (int i = 0; i < tokens.length; i++) {
            tokens[i] = tokens[i].trim();
        }
        return Arrays.asList(tokens);
    }

    private static void exit(final Logger logger, final String format, Object...args) {
        final String message = String.format(format, args);
        logger.error(message);
        throw new IllegalArgumentException(message);
    }

    private static void info(final Logger logger, final String format, final Object...args) {
        final String message = String.format(format, args);
        logger.info(message);
    }

    private static void expected(final Logger logger, final String expectation, final Object value) {
        exit(logger, "Expected %s, but found (%s) %s",
                expectation, (value == null)? "NULL": value.getClass(), (value == null)? "NULL": value.toString());
    }

}
