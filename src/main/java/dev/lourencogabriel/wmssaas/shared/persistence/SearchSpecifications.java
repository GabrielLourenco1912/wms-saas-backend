package dev.lourencogabriel.wmssaas.shared.persistence;

import jakarta.persistence.criteria.Path;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.Locale;

/** Composable database predicates; field paths must be constants supplied by the module. */
public final class SearchSpecifications {
    private SearchSpecifications() {}

    public static <T> Specification<T> all() {
        return (root, query, cb) -> cb.conjunction();
    }

    public static <T> Specification<T> equal(String field, Object value) {
        return (root, query, cb) -> {
            if (value == null || value instanceof String text && text.isBlank()) {
                return cb.conjunction();
            }
            Object normalized = value instanceof String text ? text.trim() : value;
            return cb.equal(path(root, field), normalized);
        };
    }

    public static <T> Specification<T> text(String text, String... fields) {
        if (text == null || text.isBlank() || fields.length == 0) return all();
        String pattern = "%" + text.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return (root, query, cb) -> cb.or(Arrays.stream(fields)
                .map(field -> cb.like(cb.lower(SearchSpecifications.<String>path(root, field)), pattern, '!'))
                .toArray(jakarta.persistence.criteria.Predicate[]::new));
    }

    @SuppressWarnings("unchecked")
    private static <V> Path<V> path(Path<?> root, String field) {
        Path<?> current = root;
        for (String segment : field.split("\\.")) current = current.get(segment);
        return (Path<V>) current;
    }
}
