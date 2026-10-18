package shipeditor.utility;

import lombok.extern.log4j.Log4j2;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thread-safe, deduplicated collection of data problems found while loading and resolving game data
 * (missing sprites, missing weapon specs, relocated assets, malformed CSV rows).
 * <p>
 * Call {@link #clear()} at the start of a full game data (re)load so stale problems do not accumulate.
 */
@Log4j2
public final class DataProblemsRegistry {

    /**
     * Category of a data problem.
     */
    public enum Category {
        MISSING_SPRITE("Missing sprite"),
        MISSING_WEAPON_SPEC("Missing weapon spec"),
        MOVED_ASSET("Moved asset"),
        BAD_CSV_ROW("Bad CSV row");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Single recorded problem. Equality over all fields provides the deduplication.
     *
     * @param category    problem category
     * @param source      file, entity or asset the problem relates to
     * @param packageName mod/package the problem was found in, or an empty string if unknown
     * @param message     human readable description
     */
    public record Problem(Category category, String source, String packageName, String message) {
    }

    private static final int MAX_PROBLEMS = 20_000;

    private static final Object LOCK = new Object();
    private static final Set<Problem> PROBLEMS = new LinkedHashSet<>();
    private static final Map<String, Integer> SKIPPED_ROWS = new LinkedHashMap<>();

    private DataProblemsRegistry() {
    }

    public static void record(Category category, String source, String packageName, String message) {
        if (category == null) {
            return;
        }
        Problem problem = new Problem(category,
                source != null ? source : "",
                packageName != null ? packageName : "",
                message != null ? message : "");
        synchronized (LOCK) {
            if (PROBLEMS.size() >= MAX_PROBLEMS) {
                return;
            }
            if (PROBLEMS.add(problem)) {
                log.debug("Data problem recorded: [{}] {} ({}) - {}", category, problem.source(),
                        problem.packageName(), problem.message());
            }
        }
    }

    /**
     * Convenience overload deriving the package name from a package folder path.
     */
    public static void recordInPackage(Category category, String source, Path packageFolder, String message) {
        Path name = packageFolder != null ? packageFolder.getFileName() : null;
        record(category, source, name != null ? name.toString() : "", message);
    }

    /**
     * Stores how many rows of a CSV file were skipped (blank/comment/invalid). Replaces any earlier count for the
     * same file, so reloading a file does not double count.
     */
    public static void recordSkippedRows(String csvFile, int count) {
        if (csvFile == null) {
            return;
        }
        synchronized (LOCK) {
            if (count > 0) {
                SKIPPED_ROWS.put(csvFile, count);
            } else {
                SKIPPED_ROWS.remove(csvFile);
            }
        }
    }

    public static List<Problem> getProblems() {
        synchronized (LOCK) {
            return new ArrayList<>(PROBLEMS);
        }
    }

    public static List<Problem> getProblems(Category category) {
        List<Problem> result = new ArrayList<>();
        for (Problem problem : getProblems()) {
            if (category == null || problem.category() == category) {
                result.add(problem);
            }
        }
        return result;
    }

    public static int getSkippedRowsTotal() {
        synchronized (LOCK) {
            int total = 0;
            for (int count : SKIPPED_ROWS.values()) {
                total += count;
            }
            return total;
        }
    }

    public static Map<String, Integer> getSkippedRows() {
        synchronized (LOCK) {
            return new LinkedHashMap<>(SKIPPED_ROWS);
        }
    }

    public static int size() {
        synchronized (LOCK) {
            return PROBLEMS.size();
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            PROBLEMS.clear();
            SKIPPED_ROWS.clear();
        }
    }

}
