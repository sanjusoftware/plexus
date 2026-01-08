package com.bankengine.common.annotation;

import org.hibernate.annotations.Filter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Filter(name = "bankTenantFilter")
public @interface TenantEntity { }