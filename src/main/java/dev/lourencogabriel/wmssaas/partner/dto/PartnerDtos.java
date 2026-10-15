package dev.lourencogabriel.wmssaas.partner.dto;

import dev.lourencogabriel.wmssaas.partner.model.Partner;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public final class PartnerDtos {
    private PartnerDtos() {}

    public record PartnerRequest(@NotBlank @Size(max=150) String legalName,
            @Pattern(regexp="[0-9]{11}|[0-9]{14}") String taxId,
            @NotBlank @Pattern(regexp="CLIENTE|FORNECEDOR|AMBOS") String type,
            @NotNull Boolean active) {}

    public record PartnerResponse(String id, String legalName, String taxId, String type, Boolean active, Instant updatedAt, Instant createdAt) {
        public static PartnerResponse from(Partner e) { return new PartnerResponse(e.getId(), e.getLegalName(), e.getTaxId(), e.getType(), e.getActive(), e.getUpdatedAt(), e.getCreatedAt()); }
    }
}
