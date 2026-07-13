package com.codeforge.ai.infrastructure.security;

import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUserContext {

    private CurrentUserContext() {
    }

    public static CurrentUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof CurrentUser currentUser ? currentUser : null;
    }

    public static CurrentUser requireCurrentUser() {
        CurrentUser currentUser = getCurrentUser();
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return currentUser;
    }
}
