package com.ewos.organization.domain;

/** Kinds of structural change recorded in {@code organization_node_versions}. */
public enum NodeChangeType {
    CREATED,
    RENAMED,
    MOVED,
    MERGED_INTO,
    SPLIT_FROM,
    DEACTIVATED,
    REACTIVATED
}
