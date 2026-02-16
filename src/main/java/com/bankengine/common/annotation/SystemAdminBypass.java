package com.bankengine.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker to temporarily lift Hibernate Tenant Filters.
 * Used by the SYSTEM bank to orchestrate across different tenants.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SystemAdminBypass {
}