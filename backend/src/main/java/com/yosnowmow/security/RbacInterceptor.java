package com.yosnowmow.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * HandlerInterceptor that enforces @RequiresRole on controller methods.
 *
 * Runs after FirebaseTokenFilter has already verified the token and
 * populated the SecurityContext with an AuthenticatedUser.
 *
 * Logic:
 *   1. If the handler method has no @RequiresRole annotation → pass through
 *   2. If it does → check that the authenticated user holds at least one
 *      of the required roles
 *   3. If not → throw AccessDeniedException (caught by GlobalExceptionHandler → 403)
 */
@Component
public class RbacInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {

        // Only inspect annotated controller methods; skip static resources, etc.
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }

        RequiresRole annotation = method.getMethodAnnotation(RequiresRole.class);

        // No annotation — endpoint is open to any authenticated user
        if (annotation == null) {
            return true;
        }

        // Retrieve the authenticated user placed in the SecurityContext
        // by FirebaseTokenFilter
        Object principal = SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        if (!(principal instanceof AuthenticatedUser user)) {
            throw new AccessDeniedException("No authenticated user found");
        }

        String[] requiredRoles = annotation.value();
        boolean hasAnyRole = Arrays.stream(requiredRoles).anyMatch(user::hasRole);

        if (!hasAnyRole) {
            throw new AccessDeniedException(
                    "Insufficient role. Required: " + Arrays.toString(requiredRoles)
                            + ", held: " + user.roles());
        }

        return true;
    }
}
