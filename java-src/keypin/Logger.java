package keypin;

public interface Logger {

    /**
     * Log the information message.
     * @param message message to log
     */
    void info(String message);

    /**
     * Log the error message. Possibly also abort if initialization in progress.
     * @param message message to log
     */
    void error(String message);

}
