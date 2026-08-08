package me.alexisbinh.openlootr.loot;

public final class LootGenerationCancelledException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LootGenerationCancelledException() {
        super("A LootGenerateEvent listener cancelled personal loot generation");
    }
}
