package fr.an.projectanalysis.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Base class for REST controllers, providing {@link #withLog} to run an endpoint's action while
 * logging the call before and after (with elapsed time), consistently prefixed with the
 * controller's own {@link #baseUrl}. On failure, the error is logged (with elapsed time) and
 * rethrown wrapped as a {@link RuntimeException}, unless it already is one.
 */
@Slf4j
public abstract class AbstractRestController {

    /** A {@link java.util.function.Supplier}-like callback allowed to throw a checked exception. */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    /** A {@link Runnable}-like callback allowed to throw a checked exception. */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    private final String baseUrl;

    protected AbstractRestController(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** Runs {@code action}, logging the endpoint call before and after (with elapsed time). */
    protected <T> T withLog(String method, String path, String paramsText, ThrowingSupplier<T> action) {
        String endpointUrl = baseUrl + path;
        String endpointCallMsg = "http " + method + " " + endpointUrl + ((paramsText != null && !paramsText.isBlank()) ? " " + paramsText : "");
        log.info(endpointCallMsg);
        long startTime = System.currentTimeMillis();
        try {
            T result = action.get();
            long millis = System.currentTimeMillis() - startTime;
            if (millis > 300) {
                log.info("... done " + endpointCallMsg + " (took " + millis + " ms)");
            }
            return result;
        } catch (Exception ex) {
            long millis = System.currentTimeMillis() - startTime;
            log.error("... Failed " + endpointCallMsg + " (took " + millis + " ms), rethrowing " + ex.getMessage());
            throw (ex instanceof RuntimeException re) ? re : new RuntimeException(ex);
        }
    }

    /** Void-returning variant of {@link #withLog(String, String, String, ThrowingSupplier)}. */
    protected void withLog(String method, String path, String paramsText, ThrowingRunnable action) {
        withLog(method, path, paramsText, () -> {
            action.run();
            return null;
        });
    }

}
