package com.yosnowmow.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-level annotation that restricts a controller endpoint to users
 * who hold at least one of the specified roles.
 *
 * Enforced by RbacInterceptor on every incoming request.
 *
 * Example usage:
 *   @RequiresRole("ADMIN")
 *   @GetMapping("/admin/dashboard")
 *   public ResponseEntity<?> dashboard() { ... }
 *
 *   @RequiresRole({"WORKER", "ADMIN"})
 *   @PostMapping("/jobs/{id}/complete")
 *   public ResponseEntity<?> markComplete(...) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {

    /** One or more roles; user must hold at least one to proceed. */
    String[] value();
}
