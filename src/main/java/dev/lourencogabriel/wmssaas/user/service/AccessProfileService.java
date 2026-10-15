package dev.lourencogabriel.wmssaas.user.service;

import dev.lourencogabriel.wmssaas.auth.authorization.WmsPermission;
import dev.lourencogabriel.wmssaas.auth.service.AccountSecurityService;
import dev.lourencogabriel.wmssaas.user.dto.ProfileDtos;
import dev.lourencogabriel.wmssaas.user.model.SystemRoleCode;
import dev.lourencogabriel.wmssaas.user.model.AccessProfile;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import dev.lourencogabriel.wmssaas.shared.api.LookupOption;
import dev.lourencogabriel.wmssaas.shared.persistence.SearchSpecifications;
import org.springframework.data.domain.Pageable;
import dev.lourencogabriel.wmssaas.user.repository.AccessProfileRepository;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import dev.lourencogabriel.wmssaas.web.exception.BusinessRuleException;
import dev.lourencogabriel.wmssaas.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AccessProfileService {
    private static final Set<WmsPermission> MANAGEMENT_PERMISSIONS = Set.of(
            WmsPermission.USER_VIEW, WmsPermission.USER_CREATE, WmsPermission.USER_EDIT,
            WmsPermission.USER_PROFILE_ASSIGN, WmsPermission.PROFILE_VIEW, WmsPermission.PROFILE_EDIT);

    private final AccessProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final AccountSecurityService accountSecurityService;

    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PROFILE_VIEW')")
    public PageResponse<ProfileDtos.Response> findAll(String q, Boolean active, Pageable pageable) {
        var specification = SearchSpecifications.<AccessProfile>text(q, "code", "name")
                .and(SearchSpecifications.equal("active", active));
        return PageResponse.from(this.profileRepository.findAll(specification, pageable).map(ProfileDtos.Response::from));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PROFILE_VIEW')")
    public PageResponse<LookupOption> lookup(String q, Boolean active, Pageable pageable) {
        return findAll(q, active, pageable).map(profile -> new LookupOption(profile.code(), profile.name()));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PROFILE_VIEW')")
    public ProfileDtos.Response findById(String code) {
        return ProfileDtos.Response.from(this.profileRepository.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", code)));
    }

    @Transactional
    @PreAuthorize("@wmsAuthorization.can(authentication, 'PROFILE_EDIT')")
    public ProfileDtos.Response update(String code, ProfileDtos.UpdateRequest request) {
        var profile = this.profileRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", code));
        if (SystemRoleCode.GESTOR.name().equals(code)
                && (!request.active() || !request.permissions().containsAll(MANAGEMENT_PERMISSIONS))) {
            throw new BusinessRuleException("The manager profile must retain active user and profile management");
        }
        profile.setName(request.name().trim());
        if (profile.isActive() && !request.active()) {
            for (var user : this.userRepository.findAllByProfile_Code(code)) {
                this.accountSecurityService.applyAdministrativeUpdate(user, user.getStatus(), true);
            }
        }
        profile.setActive(request.active());
        profile.setPermissions(new HashSet<>(request.permissions()));
        return ProfileDtos.Response.from(this.profileRepository.save(profile));
    }
}
