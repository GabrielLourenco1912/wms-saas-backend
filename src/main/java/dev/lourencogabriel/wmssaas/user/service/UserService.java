package dev.lourencogabriel.wmssaas.user.service;

import dev.lourencogabriel.wmssaas.auth.authorization.WmsAuthorization;
import dev.lourencogabriel.wmssaas.auth.service.AccountSecurityService;
import dev.lourencogabriel.wmssaas.shared.model.UserAccountStatus;
import dev.lourencogabriel.wmssaas.user.dto.UserDtos;
import dev.lourencogabriel.wmssaas.user.dto.UserResponse;
import dev.lourencogabriel.wmssaas.user.exception.EmailAlreadyInUseException;
import dev.lourencogabriel.wmssaas.user.model.AccessProfile;
import dev.lourencogabriel.wmssaas.user.model.SystemRoleCode;
import dev.lourencogabriel.wmssaas.user.repository.AccessProfileRepository;
import dev.lourencogabriel.wmssaas.user.repository.UserRepository;
import dev.lourencogabriel.wmssaas.web.exception.BusinessRuleException;
import dev.lourencogabriel.wmssaas.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import dev.lourencogabriel.wmssaas.shared.api.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final AccessProfileRepository profileRepository;
    private final AccountSecurityService accountSecurityService;
    private final WmsAuthorization authorization;

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public UserResponse current() {
        return UserResponse.from(this.userRepository.findById(
                SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow(() -> new AccessDeniedException("User is unavailable")));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'USER_VIEW')")
    public PageResponse<UserResponse> findAll(Pageable pageable) {
        return PageResponse.from(this.userRepository.findAll(pageable).map(UserResponse::from));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@wmsAuthorization.can(authentication, 'USER_VIEW')")
    public UserResponse findById(String id) {
        return UserResponse.from(this.userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id)));
    }

    @Transactional
    @PreAuthorize("@wmsAuthorization.can(authentication, 'USER_EDIT')")
    public UserResponse update(String id, UserDtos.UpdateRequest request) {
        // Serialize administrator changes so concurrent demotions cannot remove all managers.
        this.profileRepository.findByCodeForUpdate(SystemRoleCode.GESTOR.name())
                .orElseThrow(() -> new BusinessRuleException("Manager profile is unavailable"));
        var user = this.userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
        boolean profileChanged = !user.getProfile().getCode().equals(request.profileCode());
        if (profileChanged && !this.authorization.can(
                SecurityContextHolder.getContext().getAuthentication(), "USER_PROFILE_ASSIGN")) {
            throw new AccessDeniedException("Profile assignment permission is required");
        }
        AccessProfile profile = this.profileRepository.findByCodeForUpdate(request.profileCode())
                .filter(AccessProfile::isActive)
                .orElseThrow(() -> new BusinessRuleException("An active WMS profile is required"));
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (this.userRepository.existsByEmailAndUlidNot(email, id)) {
            throw new EmailAlreadyInUseException(email);
        }
        boolean emailChanged = !email.equals(user.getEmail());
        if (request.status() == UserAccountStatus.ACTIVE
                && (emailChanged || user.getEmailVerifiedAt() == null)) {
            throw new BusinessRuleException("An unverified email requires the INVITED status");
        }
        if (user.hasActiveAccess() && SystemRoleCode.GESTOR.name().equals(user.getProfile().getCode())
                && (profileChanged || request.status() != UserAccountStatus.ACTIVE)
                && this.userRepository.countByProfile_CodeAndStatusAndEmailVerifiedAtIsNotNull(
                        SystemRoleCode.GESTOR.name(), UserAccountStatus.ACTIVE) <= 1) {
            throw new BusinessRuleException("The last active manager cannot be disabled or demoted");
        }
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setProfile(profile);
        if (emailChanged) {
            user.setEmailVerifiedAt(null);
        }
        this.accountSecurityService.applyAdministrativeUpdate(user, request.status(), emailChanged || profileChanged);
        return UserResponse.from(this.userRepository.save(user));
    }
}
