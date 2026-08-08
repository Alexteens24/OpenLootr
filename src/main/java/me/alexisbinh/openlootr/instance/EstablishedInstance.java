package me.alexisbinh.openlootr.instance;

import java.util.Objects;

public record EstablishedInstance(LootInstanceRecord record, boolean created) {
    public EstablishedInstance {
        Objects.requireNonNull(record, "record");
    }
}
