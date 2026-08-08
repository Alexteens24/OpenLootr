package me.alexisbinh.openlootr.codec;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import java.util.List;
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

    @Test
    void roundTripsNestedAndCustomItemDataWithoutNormalization() throws Exception {
        ItemStack custom = ItemStack.of(Material.DIAMOND_SWORD);
        var customMeta = custom.getItemMeta();
        customMeta.displayName(Component.text("Vault key"));
        customMeta.lore(List.of(Component.text("player-owned data")));
        customMeta.addEnchant(Enchantment.UNBREAKING, 5, true);
        customMeta.getPersistentDataContainer().set(new NamespacedKey("openlootr", "codec_fixture"),
                PersistentDataType.STRING, "preserve-me");
        custom.setItemMeta(customMeta);

        ItemStack shulker = ItemStack.of(Material.SHULKER_BOX);
        BlockStateMeta shulkerMeta = (BlockStateMeta) shulker.getItemMeta();
        ShulkerBox box = (ShulkerBox) shulkerMeta.getBlockState();
        box.getInventory().setItem(11, custom.clone());
        shulkerMeta.setBlockState(box);
        shulker.setItemMeta(shulkerMeta);

        ItemStack[] contents = new ItemStack[27];
        contents[4] = custom;
        contents[19] = shulker;
        ItemStack[] decoded = codec.decode(codec.encode(contents), 27, ContainerCodec.CURRENT_VERSION);

        assertEquals(custom, decoded[4]);
        assertEquals(shulker, decoded[19]);
    }
}
