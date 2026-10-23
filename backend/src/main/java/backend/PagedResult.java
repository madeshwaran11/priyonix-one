package backend;

import java.util.List;

/**
 * Generic pagination envelope returned by list endpoints.
 *
 * Pages are 1-based (page 1 is the first page). {@code totalPages} is 0 when
 * there are no records at all.
 */
public record PagedResult<T>(
        List<T> items,
        int page,
        int size,
        long total,
        int totalPages) {

    public static <T> PagedResult<T> of(List<T> all, int page, int size) {
        int safeSize = size < 1 ? 1 : Math.min(size, 100);
        int total = all.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);

        int safePage = page < 1 ? 1 : page;
        int from = (int) Math.min((long) (safePage - 1) * safeSize, total);
        int to = Math.min(from + safeSize, total);

        return new PagedResult<>(
                List.copyOf(all.subList(from, to)),
                safePage,
                safeSize,
                total,
                totalPages);
    }
}
