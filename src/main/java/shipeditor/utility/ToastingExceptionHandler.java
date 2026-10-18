package shipeditor.utility;

import lombok.extern.log4j.Log4j2;
import shipeditor.utility.components.ToastManager;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Wraps the current default uncaught exception handler: the original handler still runs (logging), then a
 * dismissible ERROR toast is shown, throttled so that a repeating EDT painting failure cannot spam the user.
 */
@Log4j2
public final class ToastingExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final long MIN_TOAST_INTERVAL_MS = 5000L;
    private static final int MAX_DETAIL_LENGTH = 160;

    private final Thread.UncaughtExceptionHandler delegate;
    private final AtomicLong lastToastMillis = new AtomicLong(Long.MIN_VALUE);

    private ToastingExceptionHandler(Thread.UncaughtExceptionHandler delegate) {
        this.delegate = delegate;
    }

    /**
     * Installs the toasting handler on top of whatever default handler is currently set. Does nothing if it is
     * already installed.
     */
    public static void install() {
        Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        if (current instanceof ToastingExceptionHandler) {
            return;
        }
        Thread.setDefaultUncaughtExceptionHandler(new ToastingExceptionHandler(current));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            if (delegate != null) {
                delegate.uncaughtException(thread, throwable);
            } else {
                log.error("Uncaught exception in thread {}", thread != null ? thread.getName() : "?", throwable);
            }
        } catch (Throwable ignored) {
            // The delegate failing must never prevent the toast or recurse.
        }
        try {
            long now = System.currentTimeMillis();
            long last = lastToastMillis.get();
            if ((last == Long.MIN_VALUE || now - last >= MIN_TOAST_INTERVAL_MS)
                    && lastToastMillis.compareAndSet(last, now)) {
                ToastManager.show(describe(throwable), ToastManager.ToastLevel.ERROR);
            }
        } catch (Throwable toastFailure) {
            log.debug("Failed to show error toast: {}", toastFailure.getMessage());
        }
    }

    private static String describe(Throwable throwable) {
        if (throwable == null) {
            return "Unexpected error. See the logs for details.";
        }
        String detail = throwable.getMessage();
        if (detail != null && detail.length() > MAX_DETAIL_LENGTH) {
            detail = detail.substring(0, MAX_DETAIL_LENGTH) + "...";
        }
        String name = throwable.getClass().getSimpleName();
        return "Unexpected error: " + (detail != null && !detail.isBlank() ? name + ": " + detail : name)
                + "\nSee the logs for details.";
    }

}
