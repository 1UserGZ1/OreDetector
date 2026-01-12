package com.example.oredetector.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class OreDetectorHelper {
    // 矿石映射表 - 扩展支持深层矿石和所有变种
    private static final Map<String, List<String>> ORE_MAPPINGS = new HashMap<>();

    static {
        // 煤矿石相关
        ORE_MAPPINGS.put("煤", Arrays.asList("coal_ore", "deepslate_coal_ore", "coal", "煤炭"));
        ORE_MAPPINGS.put("coal", Arrays.asList("coal_ore", "deepslate_coal_ore", "coal"));

        // 铁矿石相关
        ORE_MAPPINGS.put("铁", Arrays.asList("iron_ore", "deepslate_iron_ore", "raw_iron", "iron", "铁"));
        ORE_MAPPINGS.put("iron", Arrays.asList("iron_ore", "deepslate_iron_ore", "raw_iron", "iron"));

        // 金矿石相关
        ORE_MAPPINGS.put("金", Arrays.asList("gold_ore", "deepslate_gold_ore", "nether_gold_ore",
                "raw_gold", "gold", "金"));
        ORE_MAPPINGS.put("gold", Arrays.asList("gold_ore", "deepslate_gold_ore", "nether_gold_ore",
                "raw_gold", "gold"));

        // 钻石相关
        ORE_MAPPINGS.put("钻石", Arrays.asList("diamond_ore", "deepslate_diamond_ore", "diamond", "钻石"));
        ORE_MAPPINGS.put("diamond", Arrays.asList("diamond_ore", "deepslate_diamond_ore", "diamond"));

        // 绿宝石相关
        ORE_MAPPINGS.put("绿宝石", Arrays.asList("emerald_ore", "deepslate_emerald_ore", "emerald", "绿宝石"));
        ORE_MAPPINGS.put("emerald", Arrays.asList("emerald_ore", "deepslate_emerald_ore", "emerald"));

        // 红石相关
        ORE_MAPPINGS.put("红石", Arrays.asList("redstone_ore", "deepslate_redstone_ore", "redstone", "红石"));
        ORE_MAPPINGS.put("redstone", Arrays.asList("redstone_ore", "deepslate_redstone_ore", "redstone"));

        // 青金石相关
        ORE_MAPPINGS.put("青金石", Arrays.asList("lapis_ore", "deepslate_lapis_ore", "lapis", "青金石"));
        ORE_MAPPINGS.put("lapis", Arrays.asList("lapis_ore", "deepslate_lapis_ore", "lapis"));

        // 铜相关
        ORE_MAPPINGS.put("铜", Arrays.asList("copper_ore", "deepslate_copper_ore", "raw_copper", "copper", "铜"));
        ORE_MAPPINGS.put("copper", Arrays.asList("copper_ore", "deepslate_copper_ore", "raw_copper", "copper"));

        // 石英相关
        ORE_MAPPINGS.put("石英", Arrays.asList("nether_quartz_ore", "quartz", "石英"));
        ORE_MAPPINGS.put("quartz", Arrays.asList("nether_quartz_ore", "quartz"));

        // 远古残骸和下界合金
        ORE_MAPPINGS.put("远古残骸", Arrays.asList("ancient_debris", "netherite_scrap", "netherite", "netherite_block", "远古残骸", "下界合金"));
        ORE_MAPPINGS.put("ancient", Arrays.asList("ancient_debris", "netherite_scrap", "netherite", "netherite_block"));
        ORE_MAPPINGS.put("debris", Arrays.asList("ancient_debris", "netherite_scrap", "netherite", "netherite_block"));
        ORE_MAPPINGS.put("netherite", Arrays.asList("ancient_debris", "netherite_scrap", "netherite", "netherite_block"));
        ORE_MAPPINGS.put("下界合金", Arrays.asList("ancient_debris", "netherite_scrap", "netherite", "netherite_block", "下界合金"));
        ORE_MAPPINGS.put("netherite_block", Arrays.asList("ancient_debris", "netherite_scrap", "netherite", "netherite_block"));

        // 紫水晶
        ORE_MAPPINGS.put("紫水晶", Arrays.asList("amethyst", "amethyst_block", "budding_amethyst", "amethyst_shard", "紫水晶"));
        ORE_MAPPINGS.put("amethyst", Arrays.asList("amethyst", "amethyst_block", "budding_amethyst", "amethyst_shard"));
        ORE_MAPPINGS.put("amethyst_block", Arrays.asList("amethyst", "amethyst_block", "budding_amethyst", "amethyst_shard"));
        ORE_MAPPINGS.put("budding_amethyst", Arrays.asList("amethyst", "amethyst_block", "budding_amethyst", "amethyst_shard"));
        ORE_MAPPINGS.put("amethyst_shard", Arrays.asList("amethyst", "amethyst_block", "budding_amethyst", "amethyst_shard"));

        // 粗矿相关
        ORE_MAPPINGS.put("粗铁", Arrays.asList("iron_ore", "deepslate_iron_ore", "raw_iron", "铁"));
        ORE_MAPPINGS.put("raw_iron", Arrays.asList("iron_ore", "deepslate_iron_ore", "raw_iron", "iron"));
        ORE_MAPPINGS.put("粗金", Arrays.asList("gold_ore", "deepslate_gold_ore", "nether_gold_ore", "raw_gold", "金"));
        ORE_MAPPINGS.put("raw_gold", Arrays.asList("gold_ore", "deepslate_gold_ore", "nether_gold_ore", "raw_gold", "gold"));
        ORE_MAPPINGS.put("粗铜", Arrays.asList("copper_ore", "deepslate_copper_ore", "raw_copper", "铜"));
        ORE_MAPPINGS.put("raw_copper", Arrays.asList("copper_ore", "deepslate_copper_ore", "raw_copper", "copper"));
    }

    public static List<BlockPos> findMatchingBlocks(ServerLevel level, BlockPos centerPos,
                                                    String mineralName, int radius) {
        List<BlockPos> foundBlocks = new ArrayList<>();
        List<String> searchTerms = getSearchTerms(mineralName);

        if (searchTerms.isEmpty()) {
            return foundBlocks;
        }

        int halfRadius = radius / 2;
        // 减少垂直搜索范围，只搜索合理的高度
        int verticalRange = 64; // 只搜索玩家周围64格高度
        BlockPos minPos = centerPos.offset(-halfRadius, -verticalRange / 2, -halfRadius);
        BlockPos maxPos = centerPos.offset(halfRadius, verticalRange / 2, halfRadius);

        // 限制搜索的方块数量
        int maxBlocks = 256; // 最多返回256个方块

        for (BlockPos pos : BlockPos.betweenClosed(minPos, maxPos)) {
            if (foundBlocks.size() >= maxBlocks) {
                break; // 达到上限，停止搜索
            }

            BlockState state = level.getBlockState(pos);

            if (isMatchingBlock(state, searchTerms)) {
                foundBlocks.add(pos.immutable());
            }
        }

        // 按距离排序，最近的在前
        foundBlocks.sort(Comparator.comparingDouble(pos -> pos.distSqr(centerPos)));

        // 进一步限制结果数量
        int keepLimit = 10; // 只保留前10个最近的方块
        if (foundBlocks.size() > keepLimit) {
            foundBlocks = foundBlocks.subList(0, keepLimit);
        }

        return foundBlocks;
    }

    public static boolean isMatchingBlock(BlockState state, String mineralName) {
        return isMatchingBlock(state, getSearchTerms(mineralName));
    }

    private static boolean isMatchingBlock(BlockState state, List<String> searchTerms) {
        if (searchTerms.isEmpty()) {
            return false;
        }

        Block block = state.getBlock();
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(block);  // 这里使用ForgeRegistries

        if (blockId == null) {
            return false;
        }

        String blockPath = blockId.getPath().toLowerCase();

        for (String term : searchTerms) {
            if (blockPath.contains(term)) {
                return true;
            }
        }

        return false;
    }

    public static List<String> getSearchTerms(String mineralName) {
        mineralName = mineralName.toLowerCase();

        // 首先检查精确匹配
        if (ORE_MAPPINGS.containsKey(mineralName)) {
            return ORE_MAPPINGS.get(mineralName);
        }

        // 模糊匹配 - 优先匹配长度更长的键
        Map.Entry<String, List<String>> bestMatch = null;
        int maxKeyLength = 0;
        
        for (Map.Entry<String, List<String>> entry : ORE_MAPPINGS.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if ((mineralName.contains(key) || key.contains(mineralName)) && key.length() > maxKeyLength) {
                bestMatch = entry;
                maxKeyLength = key.length();
            }
        }
        
        if (bestMatch != null) {
            return bestMatch.getValue();
        }

        // 尝试从物品名中提取矿石类型
        String[] mineralWords = mineralName.split(" ");
        for (String word : mineralWords) {
            if (ORE_MAPPINGS.containsKey(word)) {
                return ORE_MAPPINGS.get(word);
            }
        }

        return Collections.emptyList();
    }

    // 获取矿石颜色（用于高亮）
    public static int getOreColor(String mineralName) {
        mineralName = mineralName.toLowerCase();

        if (mineralName.contains("coal") || mineralName.contains("煤")) {
            return 0xFF333333; // 深灰色
        } else if (mineralName.contains("iron") || mineralName.contains("铁")) {
            return 0xFFD8D8D8; // 浅灰色
        } else if (mineralName.contains("gold") || mineralName.contains("金")) {
            return 0xFFFFD700; // 金色
        } else if (mineralName.contains("diamond") || mineralName.contains("钻石")) {
            return 0xFF00FFFF; // 青色
        } else if (mineralName.contains("emerald") || mineralName.contains("绿宝石")) {
            return 0xFF00FF00; // 绿色
        } else if (mineralName.contains("redstone") || mineralName.contains("红石")) {
            return 0xFFFF0000; // 红色
        } else if (mineralName.contains("lapis") || mineralName.contains("青金石")) {
            return 0xFF0000FF; // 蓝色
        } else if (mineralName.contains("quartz") || mineralName.contains("石英")) {
            return 0xFFF0F0F0; // 白色
        } else if (mineralName.contains("copper") || mineralName.contains("铜")) {
            return 0xFFFFA500; // 橙色
        } else if (mineralName.contains("ancient") || mineralName.contains("debris") ||
                mineralName.contains("netherite") || mineralName.contains("远古") || mineralName.contains("下界合金")) {
            return 0xFF8B4513; // 棕色
        } else if (mineralName.contains("amethyst") || mineralName.contains("紫水晶")) {
            return 0xFF9966CC; // 紫色
        }

        return 0xFFFFFFFF; // 白色
    }
}