package me.alexisbinh.openlootr.storage;

public record StorageHealth(
        boolean initialized,
        int schemaVersion,
        String journalMode,
        String synchronousMode,
        String databasePath
) {
    public static StorageHealth unavailable(String databasePath) {
        return new StorageHealth(false, 0, "unknown", "unknown", databasePath);
    }
}
