package com.bankengine.common.mapping;

import org.mapstruct.Mapping;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.CLASS)
@ToAuditableEntity
@Mapping(target = "code", ignore = true)
@Mapping(target = "version", ignore = true)
public @interface ToVersionableEntity {
}