package me.alexisbinh.openlootr.storage;

import me.alexisbinh.openlootr.instance.InstanceKey;
import me.alexisbinh.openlootr.instance.LootInstanceRecord;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class SqliteLootStorage implements LootStorage {
    public static final int SCHEMA_VERSION = 1;

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS loot_instances (
                container_uuid  TEXT    NOT NULL,
                player_uuid     TEXT    NOT NULL,
                container_size  INTEGER NOT NULL,
                codec_version   INTEGER NOT NULL,
                revision        INTEGER NOT NULL,
                generation_seed INTEGER NOT NULL,
                inventory_data  BLOB    NOT NULL,
                created_at      INTEGER NOT NULL,
                updated_at      INTEGER NOT NULL,
                PRIMARY KEY (container_uuid, player_uuid),
                CHECK (container_size IN (9, 18, 27, 36, 45, 54)),
                CHECK (codec_version > 0),
                CHECK (revision >= 0)
            )
            """;

    private static final String SELECT_ONE = """
            SELECT container_size, codec_version, revision, generation_seed,
                   inventory_data, created_at, updated_at
            FROM loot_instances
            WHERE container_uuid = ? AND player_uuid = ?
            """;

    private static final String INSERT_FIRST = """
            INSERT INTO loot_instances (
                container_uuid, player_uuid, container_size, codec_version,
                revision, generation_seed, inventory_data, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(container_uuid, player_uuid) DO NOTHING
            """;

    private static final String UPDATE_CAS = """
            UPDATE loot_instances
            SET container_size = ?, codec_version = ?, revision = ?,
                inventory_data = ?, updated_at = ?
            WHERE container_uuid = ? AND player_uuid = ? AND revision = ?
            """;

    private final Path databasePath;
    private Connection connection;
    private volatile StorageHealth health;

    public SqliteLootStorage(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath();
        this.health = StorageHealth.unavailable(this.databasePath.toString());
    }

    @Override
    public void initialize() {
        if (connection != null) {
            return;
        }
        try {
            Path parent = databasePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            applyPragmas();
            migrateSchema();
            health = readHealth();
        } catch (Exception exception) {
            closeQuietly();
            throw new StorageException("Failed to initialize SQLite at " + databasePath, exception);
        }
    }

    private void applyPragmas() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=FULL");
            statement.execute("PRAGMA busy_timeout=5000");
        }
    }

    private void migrateSchema() throws SQLException {
        int version = pragmaInt("user_version");
        if (version > SCHEMA_VERSION) {
            throw new StorageException("Database schema " + version
                    + " is newer than supported schema " + SCHEMA_VERSION);
        }
        if (version == SCHEMA_VERSION) {
            return;
        }

        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_TABLE);
            statement.execute("PRAGMA user_version=" + SCHEMA_VERSION);
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    @Override
    public Optional<LootInstanceRecord> find(InstanceKey key) {
        requireInitialized();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
            statement.setString(1, canonical(key.containerId()));
            statement.setString(2, canonical(key.playerId()));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new LootInstanceRecord(
                        key,
                        result.getInt("container_size"),
                        result.getInt("codec_version"),
                        result.getLong("revision"),
                        result.getLong("generation_seed"),
                        result.getBytes("inventory_data"),
                        result.getLong("created_at"),
                        result.getLong("updated_at")
                ));
            }
        } catch (SQLException exception) {
            throw new StorageException("Failed to load instance " + key, exception);
        }
    }

    @Override
    public boolean insertFirst(LootInstanceRecord record) {
        requireInitialized();
        try (PreparedStatement statement = connection.prepareStatement(INSERT_FIRST)) {
            bindIdentity(statement, record.key(), 1);
            statement.setInt(3, record.containerSize());
            statement.setInt(4, record.codecVersion());
            statement.setLong(5, record.revision());
            statement.setLong(6, record.generationSeed());
            statement.setBytes(7, record.inventoryData());
            statement.setLong(8, record.createdAt());
            statement.setLong(9, record.updatedAt());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new StorageException("Failed to insert first instance " + record.key(), exception);
        }
    }

    @Override
    public boolean updateCas(LootInstanceRecord record, long expectedRevision) {
        requireInitialized();
        if (record.revision() <= expectedRevision) {
            throw new IllegalArgumentException("new revision must be greater than expected revision");
        }
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_CAS)) {
            statement.setInt(1, record.containerSize());
            statement.setInt(2, record.codecVersion());
            statement.setLong(3, record.revision());
            statement.setBytes(4, record.inventoryData());
            statement.setLong(5, record.updatedAt());
            bindIdentity(statement, record.key(), 6);
            statement.setLong(8, expectedRevision);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new StorageException("Failed CAS update for instance " + record.key(), exception);
        }
    }

    @Override
    public StorageHealth health() {
        return health;
    }

    @Override
    public void close() {
        closeQuietly();
        health = StorageHealth.unavailable(databasePath.toString());
    }

    private StorageHealth readHealth() throws SQLException {
        return new StorageHealth(
                true,
                pragmaInt("user_version"),
                pragmaString("journal_mode").toLowerCase(Locale.ROOT),
                synchronousName(pragmaInt("synchronous")),
                databasePath.toString()
        );
    }

    private int pragmaInt(String name) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA " + name)) {
            if (!result.next()) {
                throw new SQLException("PRAGMA " + name + " returned no row");
            }
            return result.getInt(1);
        }
    }

    private String pragmaString(String name) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA " + name)) {
            if (!result.next()) {
                throw new SQLException("PRAGMA " + name + " returned no row");
            }
            return result.getString(1);
        }
    }

    private static String synchronousName(int value) {
        return switch (value) {
            case 0 -> "OFF";
            case 1 -> "NORMAL";
            case 2 -> "FULL";
            case 3 -> "EXTRA";
            default -> "UNKNOWN(" + value + ')';
        };
    }

    private static void bindIdentity(PreparedStatement statement, InstanceKey key, int start) throws SQLException {
        statement.setString(start, canonical(key.containerId()));
        statement.setString(start + 1, canonical(key.playerId()));
    }

    private static String canonical(java.util.UUID uuid) {
        return uuid.toString().toLowerCase(Locale.ROOT);
    }

    private void requireInitialized() {
        if (connection == null) {
            throw new StorageException("SQLite storage is not initialized");
        }
    }

    private void closeQuietly() {
        Connection current = connection;
        connection = null;
        if (current == null) {
            return;
        }
        try {
            current.close();
        } catch (SQLException ignored) {
            // Disable is best-effort; the caller already has any earlier failure logged.
        }
    }
}
