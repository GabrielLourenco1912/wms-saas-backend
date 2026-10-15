package dev.lourencogabriel.wmssaas.shared.api;

import jakarta.validation.constraints.Max;

/** Small pages for asynchronous dropdowns, matching the INAT lookup contract. */
public class LookupQuery extends PageQuery {
    public LookupQuery() {
        setSize(5);
    }

    @Override
    @Max(5)
    public int getSize() {
        return super.getSize();
    }
}
