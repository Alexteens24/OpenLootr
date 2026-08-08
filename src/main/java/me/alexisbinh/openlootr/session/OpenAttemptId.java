package me.alexisbinh.openlootr.session;

import java.util.UUID;

public record OpenAttemptId(UUID value) {
    public OpenAttemptId {
        if (value == null) {
            throw new NullPointerException("value");
        }
    }

    public static OpenAttemptId create() {
        return new OpenAttemptId(UUID.randomUUID());
    }
}
