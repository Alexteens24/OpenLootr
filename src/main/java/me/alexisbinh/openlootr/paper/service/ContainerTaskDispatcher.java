package me.alexisbinh.openlootr.paper.service;

@FunctionalInterface
interface ContainerTaskDispatcher {
    void execute(Runnable task, Runnable retired);
}
