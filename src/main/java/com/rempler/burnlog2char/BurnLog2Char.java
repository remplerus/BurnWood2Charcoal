package com.rempler.burnlog2char;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import net.minecraft.data.BlockTagsProvider;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.ItemTagsProvider;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ITag;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.GatherDataEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Random;

@Mod("burnlog2char")
public class BurnLog2Char
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BurnLog2Char.class);
    public static final String MODID = "burnlog2char";
    public static final ITag.INamedTag<Item> FLINT_STEEL = ItemTags.createOptional(new ResourceLocation("forge", "flint_and_steels"));

    public BurnLog2Char()
    {
        LOGGER.info("Starting up " + MODID);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_CONFIG);
        Config.loadConfig(Config.COMMON_CONFIG, FMLPaths.CONFIGDIR.get().resolve(MODID + "-common.toml"));
        MinecraftForge.EVENT_BUS.addListener(EventHandler::onItemUseEvent);
    }

    public static class Config {
        public static final ForgeConfigSpec COMMON_CONFIG;
        public static final ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();
        public static final ForgeConfigSpec.IntValue charcoalChance;

        static {
            COMMON_BUILDER.push("config");
            charcoalChance = COMMON_BUILDER.comment("Set the chance of dropping charcoal [Default: 50]")
                    .defineInRange("charcoalChance", 50, 0, 100);
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
        public ItemTagsGenerator(DataGenerator generator, BlockTagsProvider blockTagsProvider, ExistingFileHelper existingFileHelper) {
            super(generator, blockTagsProvider, BurnLog2Char.MODID, existingFileHelper);
        }

        protected void addTags() {
            tag(FLINT_STEEL).add(Items.FLINT_AND_STEEL);
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class EventHandler {
        public static void onItemUseEvent(PlayerInteractEvent.RightClickBlock event) {
            ItemStack stack = event.getItemStack();
            World level = event.getWorld();
            if (FLINT_STEEL.contains(stack.getItem())) {
                if (level.getBlockState(event.getPos()).is(BlockTags.LOGS_THAT_BURN)) {
                    BlockPos pos = event.getPos();
                    if (!level.isClientSide) {
                        ServerWorld serverLevel = (ServerWorld) level;
                        level.destroyBlock(event.getPos(), false);
                        if (new Random().nextInt(0, 100) < Config.charcoalChance.get()) {
                            serverLevel.addFreshEntity(new ItemEntity(serverLevel, pos.getX() + 0.5D, pos.getY() + 0.5D,
                                            pos.getZ() + 0.5D, Items.CHARCOAL.getDefaultInstance()));
                        }
                        stack.hurtAndBreak(1, event.getPlayer(), (player) -> {
                            player.broadcastBreakEvent(event.getPlayer().getUsedItemHand());
                        });
                        event.setCanceled(true);
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
            }
        }
    }
}
