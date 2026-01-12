package com.example.oredetector;

import com.example.oredetector.item.ModItems;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(OreDetector.MOD_ID)
public class OreDetector {
    public static final String MOD_ID = "oredetector";

    public OreDetector() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        // 注册物品
        ModItems.register(modEventBus);
        
        MinecraftForge.EVENT_BUS.register(this);
    }
}