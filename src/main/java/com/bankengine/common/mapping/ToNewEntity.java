package com.bankengine.common.mapping;

import org.mapstruct.Mapping;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.CLASS)
@ToAuditableEntity
@Mapping(target = "status", constant = "DRAFT")
@Mapping(target = "version", constant = "1")
public @interface ToNewEntity {
}