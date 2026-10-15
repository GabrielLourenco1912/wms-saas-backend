package dev.lourencogabriel.wmssaas.user.service;

import dev.lourencogabriel.wmssaas.auth.authorization.WmsPermission;
import dev.lourencogabriel.wmssaas.shared.api.LookupOption;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.user.dto.PermissionDtos;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

@Service
public class PermissionService {
    /** Permissions are a finite code catalog, so pagination does not require a database query. */
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PROFILE_VIEW')")
    public PageResponse<PermissionDtos.Response> findAll(String q, String resource,
                                                        String functionalRequirement, Pageable pageable) {
        String text = normalize(q);
        String prefix = normalize(resource);
        String requirement = normalize(functionalRequirement);
        var matches = Arrays.stream(WmsPermission.values())
                .filter(permission -> prefix.isEmpty() || normalize(permission.name()).startsWith(prefix + "_"))
                .filter(permission -> requirement.isEmpty() || normalize(permission.functionalRequirement()).equals(requirement))
                .filter(permission -> text.isEmpty() || normalize(permission.name()).contains(text)
                        || normalize(permission.functionalRequirement()).contains(text))
                .sorted(Comparator.comparing(Enum::name))
                .map(permission -> new PermissionDtos.Response(permission.name(), permission.functionalRequirement()))
                .toList();
        int from = (int) Math.min(pageable.getOffset(), matches.size());
        int to = Math.min(from + pageable.getPageSize(), matches.size());
        return PageResponse.from(new PageImpl<>(matches.subList(from, to), pageable, matches.size()));
    }

    @PreAuthorize("@wmsAuthorization.can(authentication, 'PROFILE_VIEW')")
    public PageResponse<LookupOption> lookup(String q, String resource, String functionalRequirement, Pageable pageable) {
        return findAll(q, resource, functionalRequirement, pageable)
                .map(permission -> new LookupOption(permission.code(),
                        permission.code() + " (" + permission.functionalRequirement() + ")"));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
