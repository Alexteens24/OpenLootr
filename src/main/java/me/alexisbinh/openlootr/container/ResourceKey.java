package me.alexisbinh.openlootr.container;

import java.util.Objects;

public record ResourceKey(String namespace, String value) {
    public ResourceKey {
        namespace = requirePart(namespace, "namespace");
        value = requirePart(value, "value");
    }

    private static String requirePart(String part, String label) {
        Objects.requireNonNull(part, label);
        if (part.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return part;
    }

    @Override
    public String toString() {
        return namespace + ':' + value;
    }
}
