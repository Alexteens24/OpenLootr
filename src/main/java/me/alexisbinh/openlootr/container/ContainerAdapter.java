package me.alexisbinh.openlootr.container;

public interface ContainerAdapter<T> {
    boolean supports(T candidate);

    ContainerKind kind(T candidate);

    int logicalSize(T candidate);

    IdentityStrategy identityStrategy();

    boolean canOpenAsPersonalMenu();

    AutomationPolicy automationPolicy();
}
