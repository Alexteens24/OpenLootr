package me.alexisbinh.openlootr.container;

public interface ContainerAdapter<T> {
    boolean supports(T candidate);

    ContainerDescriptor describe(T candidate);

    IdentityStrategy identityStrategy();

    boolean canOpenAsPersonalMenu();

    AutomationPolicy automationPolicy();
}
