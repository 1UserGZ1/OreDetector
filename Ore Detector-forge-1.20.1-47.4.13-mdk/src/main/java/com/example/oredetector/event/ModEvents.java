package com.example.oredetector.event;

import com.example.oredetector.OreDetector;
import com.example.oredetector.item.OreDetectorItem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = OreDetector.MOD_ID)
public class ModEvents {

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        ItemEntity itemEntity = event.getEntity();
        ItemStack stack = itemEntity.getItem();

        // 如果丢弃的是矿石探测器，清除其NBT数据
        if (stack.getItem() instanceof OreDetectorItem) {
            stack.removeTagKey("Active");
            stack.removeTagKey("Targets");
            stack.removeTagKey("Mineral");
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        // 这里可以添加方块被破坏时的处理逻辑
        // 例如：当被追踪的矿石被破坏时，更新探测器的状态
    }
}