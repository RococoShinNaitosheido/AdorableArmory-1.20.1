package flu.kitten.adorablearmory.register;

import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.block.LolaBlock;
import flu.kitten.adorablearmory.block.LolaBlockEntity;
import flu.kitten.adorablearmory.block.LolaItemBlock;
import flu.kitten.adorablearmory.entity.boss.ScarletLoraAlysia;
import flu.kitten.adorablearmory.entity.effect.AnemiaSpecialEffect;
import flu.kitten.adorablearmory.item.SparklingDreamIdolStar;
import flu.kitten.adorablearmory.item.tool.SoftLightEndLove;
import flu.kitten.adorablearmory.material.AdorableArmoryMaterial;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class AdorableArmoryRegister {
    // DeferredRegister
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AdorableArmory.MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, AdorableArmory.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AdorableArmory.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AdorableArmory.MODID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, AdorableArmory.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AdorableArmory.MODID);
    // Block
    public static final RegistryObject<Block> LOLA = BLOCKS.register("lola", () -> new LolaBlock(BlockBehaviour.Properties.copy(Blocks.GLASS).sound(SoundType.GLASS).strength(0.5F))); // lightLevel(state -> 5)
    // BlockEntity
    public static final RegistryObject<BlockEntityType<LolaBlockEntity>> LOLA_BLOCK_ENTITY = BLOCK_ENTITIES.register("lola", () -> BlockEntityType.Builder.of(LolaBlockEntity::new, LOLA.get()).build(null));
    // Entity
    public static final RegistryObject<EntityType<ScarletLoraAlysia>> SCARLET_LORA_ALYSIA = ENTITY_TYPES.register("scarlet_lora_alysia", () -> EntityType.Builder.of(ScarletLoraAlysia::new, MobCategory.MONSTER).sized(0.6F, 1.8F).build("scarlet_lora_alysia"));
    public static final RegistryObject<EntityType<AnemiaSpecialEffect>> ANEMIA_SPECIAL_EFFECT = ENTITY_TYPES.register("anemia_special_effect", () -> EntityType.Builder.of(AnemiaSpecialEffect::new, MobCategory.MISC).sized(5, 5).build("anemia_special_effect"));
    // Item
    public static final RegistryObject<Item> SPARKLING_DREAM_IDOL_STAR = item("sparkling_dream_idol_star", () -> new SparklingDreamIdolStar(new Item.Properties()));
    public static final RegistryObject<Item> SOFT_LIGHT_END_LOVE = item("soft_light_end_love", () -> new SoftLightEndLove(AdorableArmoryMaterial.ENDING_LOVE_TIER, -1, -2.5f, new Item.Properties()));
    public static final RegistryObject<Item> LOLA_ITEM = item("lola", () -> new LolaItemBlock(LOLA.get(), new Item.Properties()));
    // EggItem
    public static final RegistryObject<Item> SCARLET_LORA_ALYSIA_EGG = item("scarlet_lora_alysia_egg", () -> new ForgeSpawnEggItem(SCARLET_LORA_ALYSIA, 0xed3c57, 0xf2ba74, new Item.Properties()));
    // Creative Tab
    public static final RegistryObject<CreativeModeTab> ADORABLE_ARMORY_TAB = CREATIVE_TABS.register("adorablearmory_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(SPARKLING_DREAM_IDOL_STAR.get()))
                    .title(Component.translatable("itemGroup.adorablearmory_tab"))
                    .displayItems((parameters, output) -> {
                        output.accept(SPARKLING_DREAM_IDOL_STAR.get()); // sparkling_dream_idol_star
                        output.accept(LOLA_ITEM.get()); // lola
                        output.accept(SOFT_LIGHT_END_LOVE.get()); // soft_light_end_love
                        output.accept(SCARLET_LORA_ALYSIA_EGG.get());
                    })
                    .build()
    );

    // Tool
    private static RegistryObject<Item> item(String name, Supplier<Item> supplier) {
        return ITEMS.register(name, supplier);
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ENTITY_TYPES.register(bus);
        SOUND_EVENTS.register(bus);
        CREATIVE_TABS.register(bus);
    }

    private AdorableArmoryRegister() {}
}
