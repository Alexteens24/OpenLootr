package me.alexisbinh.openlootr.codec;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContainerCodecTest {
    private final ContainerCodec codec = new ContainerCodec();

    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    void roundTripsExactArrayLengthAndSlots() throws Exception {
        ItemStack[] contents = new ItemStack[27];
        contents[0] = ItemStack.of(Material.DIAMOND, 3);
        contents[13] = ItemStack.of(Material.SHULKER_BOX);
        byte[] encoded = codec.encode(contents);

        ItemStack[] decoded = codec.decode(encoded, 27, ContainerCodec.CURRENT_VERSION);
        assertEquals(27, decoded.length);
        assertEquals(Material.DIAMOND, decoded[0].getType());
        assertEquals(3, decoded[0].getAmount());
        assertEquals(Material.SHULKER_BOX, decoded[13].getType());
    }

    @Test
    void failsClosedOnVersionAndSizeMismatch() {
        assertThrows(CodecException.class, () -> codec.decode(new byte[]{1}, 27, 1));
        assertThrows(CodecException.class, () -> codec.decode(new byte[]{2, 0}, 27, 1));

        ItemStack[] contents = new ItemStack[27];
        byte[] encoded = codec.encode(contents);
        assertThrows(CodecException.class, () -> codec.decode(encoded, 54, 1));
    }
}
