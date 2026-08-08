package me.alexisbinh.openlootr.container;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ContainerAdapterRegistry<T> {
    private final List<ContainerAdapter<? super T>> adapters;

    public ContainerAdapterRegistry(List<ContainerAdapter<? super T>> adapters) {
        this.adapters = List.copyOf(Objects.requireNonNull(adapters, "adapters"));
    }

    public Optional<ContainerAdapter<? super T>> find(T candidate) {
        return adapters.stream().filter(adapter -> adapter.supports(candidate)).findFirst();
    }

    public int size() {
        return adapters.size();
    }
}
