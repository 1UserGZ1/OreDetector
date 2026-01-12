package com.example.oredetector.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;

public class OreDetectorConfig {
    public static class Client {
        public final ForgeConfigSpec.BooleanValue enableXRay;
        public final ForgeConfigSpec.IntValue renderDistance;
        public final ForgeConfigSpec.IntValue lineWidth;
        public final ForgeConfigSpec.BooleanValue enableParticles;

        public Client(ForgeConfigSpec.Builder builder) {
            builder.push("rendering");

            enableXRay = builder
                    .comment("Enable X-Ray effect for ore outlines")
                    .define("enableXRay", true);

            renderDistance = builder
                    .comment("Maximum distance to render ore outlines (in blocks)")
                    .defineInRange("renderDistance", 32, 8, 128);

            lineWidth = builder
                    .comment("Width of ore outline lines")
                    .defineInRange("lineWidth", 3, 1, 10);

            enableParticles = builder
                    .comment("Enable particle effects for ores")
                    .define("enableParticles", true);

            builder.pop();
        }
    }

    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        final Pair<Client, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT_SPEC = specPair.getRight();
        CLIENT = specPair.getLeft();
    }

    public static void register(ModLoadingContext context) {
        context.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
    }
}