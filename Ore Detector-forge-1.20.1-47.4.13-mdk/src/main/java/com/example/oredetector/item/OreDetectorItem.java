package com.example.oredetector.item;

import com.example.oredetector.util.OreDetectorHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import javax.annotation.Nullable;
import java.util.*;

public class OreDetectorItem extends Item {
    private static final int COOLDOWN_TICKS = 40; // 2秒冷却
    private static final int SEARCH_RADIUS_CHUNKS = 3; // 3x3区块
    private static final int SEARCH_RADIUS_BLOCKS = SEARCH_RADIUS_CHUNKS * 16; // 48格

    // NBT标签键
    private static final String TAG_ACTIVE = "Active";
    private static final String TAG_TARGETS = "Targets";
    private static final String TAG_MINERAL = "Mineral";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_LAST_UPDATE = "LastUpdate";

    public OreDetectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack detector = player.getItemInHand(hand);

        // 检查冷却
        if (player.getCooldowns().isOnCooldown(this)) {
            player.displayClientMessage(Component.translatable("item.oredetector.ore_detector.cooldown")
                    .withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.fail(detector);
        }

        // 检查对面手是否有矿物
        InteractionHand oppositeHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack mineralItem = player.getItemInHand(oppositeHand);
        if (mineralItem.isEmpty()) {
            player.displayClientMessage(Component.translatable("item.oredetector.ore_detector.no_mineral")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(detector);
        }
        
        // 检查是否为矿石（通过检查是否能获取到搜索词）
        String mineralName = mineralItem.getHoverName().getString();
        List<String> searchTerms = OreDetectorHelper.getSearchTerms(mineralName);
        if (searchTerms.isEmpty()) {
            player.displayClientMessage(Component.literal("请在另一只手手持矿物")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(detector);
        }

        // 设置冷却
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

        // 只在服务器端执行搜索
        if (!level.isClientSide) {
            ServerPlayer serverPlayer = (ServerPlayer) player;
            ServerLevel serverLevel = (ServerLevel) level;

            List<BlockPos> foundBlocks = OreDetectorHelper.findMatchingBlocks(
                    serverLevel,
                    serverPlayer.blockPosition(),
                    mineralName,
                    SEARCH_RADIUS_BLOCKS
            );

            // 保存数据到NBT
            saveDetectorData(detector, foundBlocks, mineralName, level);

            // 发送消息给玩家
            if (foundBlocks.isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("item.oredetector.ore_detector.not_found", mineralName)
                                .withStyle(ChatFormatting.YELLOW),
                        true
                );
            } else {
                // 消耗耐久度
                detector.hurtAndBreak(1, player, (p) -> {
                    p.broadcastBreakEvent(hand);
                });
                
                if (foundBlocks.size() >= 10) {
                    player.displayClientMessage(
                            Component.translatable("item.oredetector.ore_detector.found_many",
                                            mineralName)
                                    .withStyle(ChatFormatting.GREEN),
                            true
                    );
                } else {
                    player.displayClientMessage(
                            Component.translatable("item.oredetector.ore_detector.found",
                                            foundBlocks.size(), mineralName)
                                    .withStyle(ChatFormatting.GREEN),
                            true
                    );
                }
            }
        }

        return InteractionResultHolder.success(detector);
    }

    private static final int UPDATE_INTERVAL = 20; // 每秒更新一次

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!(entity instanceof Player player) || level.isClientSide) {
            return;
        }

        // 检查是否在任意手中
        boolean inMainHand = player.getMainHandItem() == stack;
        boolean inOffHand = player.getOffhandItem() == stack;
        boolean inEitherHand = inMainHand || inOffHand;

        // 更新NBT中的激活状态
        CompoundTag tag = stack.getOrCreateTag();
        boolean wasActive = tag.getBoolean(TAG_ACTIVE);

        if (!inEitherHand && wasActive) {
            // 不在任何手中，停止工作
            tag.putBoolean(TAG_ACTIVE, false);
            player.displayClientMessage(
                    Component.translatable("item.oredetector.ore_detector.deactivated")
                            .withStyle(ChatFormatting.GRAY),
                    true
            );
            return;
        }

        // 只在玩家手持时更新
        if (!inEitherHand) {
            return;
        }

        // 检查是否需要重新激活
        if (!tag.getBoolean(TAG_ACTIVE)) {
            // 需要右键重新激活
            return;
        }

        // 限制更新频率
        long currentTime = level.getGameTime();
        long lastUpdate = tag.getLong(TAG_LAST_UPDATE);
        if (currentTime - lastUpdate < UPDATE_INTERVAL) {
            return;
        }
        tag.putLong(TAG_LAST_UPDATE, currentTime);

        // 获取存储的数据
        String currentDimension = level.dimension().location().toString();
        String storedDimension = tag.getString(TAG_DIMENSION);

        // 检查是否还在同一维度
        if (!storedDimension.equals(currentDimension)) {
            tag.putBoolean(TAG_ACTIVE, false);
            player.displayClientMessage(
                    Component.translatable("item.oredetector.ore_detector.wrong_dimension")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return;
        }

        // 获取目标方块列表
        List<BlockPos> targetBlocks = readTargetBlocks(tag);
        String mineralName = tag.getString(TAG_MINERAL);

        if (targetBlocks.isEmpty()) {
            return;
        }

        // 检查目标方块是否还存在
        List<BlockPos> remainingBlocks = new ArrayList<>();
        for (BlockPos pos : targetBlocks) {
            // 只检查加载的区块
            if (level.isInWorldBounds(pos) && level.hasChunkAt(pos)) {
                if (OreDetectorHelper.isMatchingBlock(level.getBlockState(pos), mineralName)) {
                    remainingBlocks.add(pos);
                }
            }
        }

        // 更新目标方块列表
        if (remainingBlocks.size() != targetBlocks.size()) {
            saveTargetBlocks(tag, remainingBlocks);

            if (remainingBlocks.isEmpty()) {
                tag.putBoolean(TAG_ACTIVE, false);
                player.displayClientMessage(
                        Component.translatable("item.oredetector.ore_detector.all_destroyed")
                                .withStyle(ChatFormatting.GRAY),
                        true
                );
            } else if (remainingBlocks.size() < targetBlocks.size()) {
                // 只在方块数量减少时发送消息
                player.displayClientMessage(
                        Component.translatable("item.oredetector.ore_detector.remaining",
                                        remainingBlocks.size())
                                .withStyle(ChatFormatting.GRAY),
                        true
                );
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        tooltip.add(Component.translatable("item.oredetector.ore_detector.desc_1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.oredetector.ore_detector.desc_2")
                .withStyle(ChatFormatting.GRAY));

        // 显示当前状态
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.getBoolean(TAG_ACTIVE)) {
            String mineralName = tag.getString(TAG_MINERAL);
            if (!mineralName.isEmpty()) {
                tooltip.add(Component.translatable("item.oredetector.ore_detector.status_active",
                        mineralName).withStyle(ChatFormatting.GREEN));
            }
        }
    }

    @Nullable
    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return super.initCapabilities(stack, nbt);
    }

    // NBT相关方法
    private void saveDetectorData(ItemStack stack, List<BlockPos> targetBlocks,
                                  String mineralName, Level level) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(TAG_ACTIVE, !targetBlocks.isEmpty());
        tag.putString(TAG_MINERAL, mineralName);
        tag.putString(TAG_DIMENSION, level.dimension().location().toString());
        tag.putLong(TAG_LAST_UPDATE, System.currentTimeMillis());

        saveTargetBlocks(tag, targetBlocks);
    }

    private void saveTargetBlocks(CompoundTag tag, List<BlockPos> blocks) {
        CompoundTag targetsTag = new CompoundTag();
        targetsTag.putInt("Count", blocks.size());

        for (int i = 0; i < blocks.size(); i++) {
            BlockPos pos = blocks.get(i);
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("X", pos.getX());
            posTag.putInt("Y", pos.getY());
            posTag.putInt("Z", pos.getZ());
            targetsTag.put("Pos" + i, posTag);
        }

        tag.put(TAG_TARGETS, targetsTag);
    }

    // 改为静态方法，使其可以在静态上下文中调用
    private static List<BlockPos> readTargetBlocks(CompoundTag tag) {
        List<BlockPos> blocks = new ArrayList<>();

        if (tag.contains(TAG_TARGETS)) {
            CompoundTag targetsTag = tag.getCompound(TAG_TARGETS);
            int count = targetsTag.getInt("Count");

            for (int i = 0; i < count; i++) {
                if (targetsTag.contains("Pos" + i)) {
                    CompoundTag posTag = targetsTag.getCompound("Pos" + i);
                    blocks.add(new BlockPos(
                            posTag.getInt("X"),
                            posTag.getInt("Y"),
                            posTag.getInt("Z")
                    ));
                }
            }
        }

        return blocks;
    }

    // 获取当前活动的目标方块（供渲染器使用）
    public static List<BlockPos> getActiveTargets(ItemStack stack, Level level) {
        if (stack.isEmpty() || !(stack.getItem() instanceof OreDetectorItem)) {
            return Collections.emptyList();
        }

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getBoolean(TAG_ACTIVE)) {
            return Collections.emptyList();
        }

        // 检查维度
        String currentDimension = level.dimension().location().toString();
        String storedDimension = tag.getString(TAG_DIMENSION);

        if (!storedDimension.equals(currentDimension)) {
            return Collections.emptyList();
        }

        List<BlockPos> allBlocks = readTargetBlocks(tag);
        List<BlockPos> existingBlocks = new ArrayList<>();
        String mineralName = tag.getString(TAG_MINERAL);

        // 检查每个方块是否仍然存在
        for (BlockPos pos : allBlocks) {
            if (level.isInWorldBounds(pos) && level.hasChunkAt(pos)) {
                if (OreDetectorHelper.isMatchingBlock(level.getBlockState(pos), mineralName)) {
                    existingBlocks.add(pos);
                }
            }
        }

        return existingBlocks;
    }

    public static String getCurrentMineral(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null ? tag.getString(TAG_MINERAL) : "";
    }
}