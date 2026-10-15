package dev.lourencogabriel.wmssaas.shared.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Query-string contract shared by collection and search endpoints. Pages are zero-based. */
@Getter
@Setter
public class PageQuery {
    @Min(0)
    private int page = 0;

    @Min(1)
    @Max(100)
    private int size = 20;

    @Size(max = 160)
    private String q = "";

    @AssertTrue(message = "page * size must not exceed 2147483647")
    public boolean isOffsetSupported() {
        return (long) page * size <= Integer.MAX_VALUE;
    }

    public Pageable toPageable() {
        return toPageable("id");
    }

    public Pageable toPageable(String idProperty) {
        return PageRequest.of(page, size, Sort.by(idProperty));
    }
}
