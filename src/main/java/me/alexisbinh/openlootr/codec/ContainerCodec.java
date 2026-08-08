package me.alexisbinh.openlootr.codec;

import me.alexisbinh.openlootr.instance.LootInstanceRecord;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Objects;

public final class ContainerCodec {
    public static final int CURRENT_VERSION = 1;
    private static final byte FORMAT_V1 = 0x01;

    public byte[] encode(ItemStack[] contents) {
        Objects.requireNonNull(contents, "contents");
        LootInstanceRecord.validateSize(contents.length);
        byte[] body = ItemStack.serializeItemsAsBytes(contents);
        byte[] encoded = new byte[body.length + 1];
        encoded[0] = FORMAT_V1;
        System.arraycopy(body, 0, encoded, 1, body.length);
        return encoded;
    }

    public ItemStack[] decode(byte[] encoded, int expectedSize, int columnCodecVersion) throws CodecException {
        LootInstanceRecord.validateSize(expectedSize);
        if (columnCodecVersion != CURRENT_VERSION) {
            throw new CodecException("Unsupported codec column version " + columnCodecVersion);
        }
        if (encoded == null || encoded.length < 2) {
            throw new CodecException("Stored inventory data is missing or too short");
        }
        int embeddedVersion = Byte.toUnsignedInt(encoded[0]);
        if (embeddedVersion != columnCodecVersion) {
            throw new CodecException("Codec version mismatch: column=" + columnCodecVersion
                    + ", embedded=" + embeddedVersion);
        }
        try {
            ItemStack[] decoded = ItemStack.deserializeItemsFromBytes(Arrays.copyOfRange(encoded, 1, encoded.length));
            if (decoded.length != expectedSize) {
                throw new CodecException("Inventory size mismatch: stored=" + decoded.length
                        + ", expected=" + expectedSize);
            }
            return decoded;
        } catch (CodecException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CodecException("Failed to decode Paper item array", exception);
        }
    }
}
