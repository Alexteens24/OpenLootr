package me.alexisbinh.openlootr.paper.service;

public final class ContainerUnavailableException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public ContainerUnavailableException(String message) {
        super(message);
    }
}
