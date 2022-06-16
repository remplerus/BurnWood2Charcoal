package com.rempler.burnlog2char;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.data.tags.BlockTagsProvider;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forge.event.lifecycle.GatherDataEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Random;
import java.util.function.Consumer;

@Mod("burnlog2char")
public class BurnLog2Char
{
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String MODID = "burnlog2char";
    public static final TagKey<Item> FLINT_STEEL = ItemTags.create(new ResourceLocation("forge", "flint_and_steels"));

    public BurnLog2Char()
    {
        LOGGER.info("Starting up " + MODID);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_CONFIG);
        Config.loadConfig(Config.COMMON_CONFIG, FMLPaths.CONFIGDIR.get().resolve(MODID + "-common.toml"));
        MinecraftForge.EVENT_BUS.addListener(EventHandler::onItemUseOnBlockEvent);
        MinecraftForge.EVENT_BUS.addListener(EventHandler::onEntityEvent);
    }

    public static class Config {
        public static final ForgeConfigSpec COMMON_CONFIG;
        public static final ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();
        public static final ForgeConfigSpec.IntValue charcoalChance;
        public static final ForgeConfigSpec.IntValue charcoalChanceFlint;

        static {
            COMMON_BUILDER.push("config");
            charcoalChance = COMMON_BUILDER.comment("Set the chance of dropping charcoal [Default: 50]")
                    .defineInRange("charcoalChance", 30, 0, 100);
            charcoalChanceFlint = COMMON_BUILDER.comment("Set the chance of dropping charcoal by using flint and steel [Default: 50]")
                    .defineInRange("charcoalChanceFlint", 60, 0, 100);
            COMMON_BUILDER.pop();

            COMMON_CONFIG = COMMON_BUILDER.build();
        }

        public static void loadConfig(ForgeConfigSpec spec, Path path) {
            CommentedFileConfig fileConfig = CommentedFileConfig.builder(path).sync().autosave().writingMode(WritingMode.REPLACE).build();
            fileConfig.load();
            spec.setConfig(fileConfig);
        }
    }

    public static class ItemTagsGenerator extends ItemTagsProvider {
        public ItemTagsGenerator(DataGenerator generator, BlockTagsProvider blockTagsProvider, @Nullable ExistingFileHelper existingFileHelper) {
            super(generator, blockTagsProvider, BurnLog2Char.MODID, existingFileHelper);
        }

        protected void addTags() {
            tag(FLINT_STEEL).add(Items.FLINT_AND_STEEL);
        }
    }

    public static class RecipeGenerator extends RecipeProvider {
        public RecipeGenerator(DataGenerator generator) {
            super(generator);
        }

        protected void buildCraftingRecipes(Consumer<FinishedRecipe> consumer) {
            SimpleCookingRecipeBuilder.campfireCooking(Ingredient.of(ItemTags.LOGS_THAT_BURN), Items.CHARCOAL, 0.15F, 200)
                    .unlockedBy("has_log", has(ItemTags.LOGS_THAT_BURN))
                    .save(consumer, new ResourceLocation(BurnLog2Char.MODID, "campfire_charcoal"));
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class EventHandler {
        public static void onItemUseOnBlockEvent(PlayerInteractEvent.RightClickBlock event) {
            Level level = event.getWorld();
            if (!level.isClientSide) {
                ServerLevel serverLevel = (ServerLevel) level;
                ItemStack stack = event.getItemStack();
                if (stack.is(FLINT_STEEL)) {
                    BlockPos pos = event.getPos();
                    if (serverLevel.getBlockState(pos).is(BlockTags.LOGS_THAT_BURN)) {
                        serverLevel.destroyBlock(pos, false);
                        if (new Random().nextInt(0, 100) < Config.charcoalChanceFlint.get()) {
                            serverLevel.addFreshEntity(new ItemEntity(serverLevel, pos.getX() + 0.5D, pos.getY() + 0.5D,
                                    pos.getZ() + 0.5D, Items.CHARCOAL.getDefaultInstance()));
                        }
                        stack.hurtAndBreak(1, event.getPlayer(), (player) -> player.broadcastBreakEvent(event.getPlayer().getUsedItemHand()));
                        event.setCanceled(true);
                    }
                }
                if (stack.is(ItemTags.LOGS_THAT_BURN)) {
                    BlockState state = serverLevel.getBlockState(event.getPos());
                    FluidState fstate = serverLevel.getFluidState(event.getPos().above());
                    if (state.is(BlockTags.FIRE) || fstate.is(FluidTags.LAVA)) {
                        Vec3 playerPos = event.getPlayer().position();
                        if (new Random().nextInt(0, 100) < Config.charcoalChance.get()) {
                            serverLevel.addFreshEntity(new ItemEntity(serverLevel, playerPos.x(), playerPos.y() + 1D,
                                    playerPos.z(), Items.CHARCOAL.getDefaultInstance()));
                        }
                        event.setCanceled(true);
                    }
                }
            }
        }

        public static void onEntityEvent(EntityEvent event) {
            if (event.getEntity() != null) {
                Level level = event.getEntity().level;
                if(!level.isClientSide) {
                    ServerLevel serverLevel = (ServerLevel) level;
                    if (event.getEntity() instanceof ItemEntity itemEntity) {
                        if (itemEntity.getItem().is(ItemTags.LOGS_THAT_BURN)) {
                            BlockState state = serverLevel.getBlockState(new BlockPos(itemEntity.position()));
                            Vec3 itemEntityPos = itemEntity.position();
                            if (state.is(BlockTags.FIRE) || state.getFluidState().is(FluidTags.LAVA)) {
                                if (state.is(BlockTags.FIRE)) {
                                    itemEntity.playSound(SoundEvents.GENERIC_BURN, 0.4f, 2);
                                }
                                if (new Random().nextInt(0, 100) < Config.charcoalChance.get()) {
                                    serverLevel.addFreshEntity(new ItemEntity(serverLevel, itemEntityPos.x(), itemEntityPos.y() + 2D,
                                            itemEntityPos.z(), Items.CHARCOAL.getDefaultInstance()));
                                }
                            }
                        }
                    }
                }
            }
        }

        @SubscribeEvent
        public static void gatherData(GatherDataEvent event) {
            if (event.includeServer()) {
                ExistingFileHelper fileHelper = event.getExistingFileHelper();
                DataGenerator generator = event.getGenerator();
                generator.addProvider(new ItemTagsGenerator(generator, new BlockTagsProvider(generator, BurnLog2Char.MODID, fileHelper), fileHelper));
                generator.addProvider(new RecipeGenerator(generator));
            }
        }
    }
}
