package com.example.oredetector.render;

import com.example.oredetector.OreDetector;
import com.example.oredetector.item.OreDetectorItem;
import com.example.oredetector.util.OreDetectorHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Queue;
import java.util.LinkedList;

@Mod.EventBusSubscriber(modid = OreDetector.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class OreRenderer {
    private static final Random RANDOM = new Random();
    private static int particleTickCounter = 0;
    
    // ========== 渲染方块边框 ==========
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // 在渲染的最后阶段，确保在所有东西之后渲染
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        
        if (player == null || mc.level == null) {
            return;
        }

        List<BlockPos> targetBlocks = null;
        String mineralName = null;
        
        // 检查探测器
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof OreDetectorItem) {
            targetBlocks = OreDetectorItem.getActiveTargets(mainHand, mc.level);
            mineralName = OreDetectorItem.getCurrentMineral(mainHand);
        }
        
        if ((targetBlocks == null || targetBlocks.isEmpty()) || mineralName == null || mineralName.isEmpty()) {
            ItemStack offHand = player.getOffhandItem();
            if (offHand.getItem() instanceof OreDetectorItem) {
                targetBlocks = OreDetectorItem.getActiveTargets(offHand, mc.level);
                mineralName = OreDetectorItem.getCurrentMineral(offHand);
            }
        }
        
        if (targetBlocks != null && !targetBlocks.isEmpty() && mineralName != null && !mineralName.isEmpty()) {
            renderOreHighlights(event, targetBlocks, mineralName);
        }
    }
    
    private static void renderOreHighlights(RenderLevelStageEvent event, 
                                           List<BlockPos> targetBlocks, 
                                           String mineralName) {
        Minecraft mc = Minecraft.getInstance();
        PoseStack poseStack = event.getPoseStack();
        
        // 保存当前渲染状态
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        // 关键设置：禁用深度测试和深度写入 - 实现透视效果
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.lineWidth(4.0f); // 设置更粗的线
        
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
        
        // 获取相机位置
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x(), -cameraPos.y(), -cameraPos.z());
        
        int color = OreDetectorHelper.getOreColor(mineralName);
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float a = 0.9F;
        
        // 渲染所有目标方块
        for (BlockPos pos : targetBlocks) {
            double x = pos.getX();
            double y = pos.getY();
            double z = pos.getZ();
            
            // 距离限制
            double distance = pos.distSqr(mc.player.blockPosition());
            if (distance <= 32 * 32) { // 32格内
                // 绘制边框
                renderGlowingOutline(poseStack, vertexConsumer, x, y, z, r, g, b, a);
            }
        }
        
        // 绘制从矿物到玩家的连接线
        renderConnectionLines(poseStack, bufferSource, targetBlocks, mineralName, mc);
        
        poseStack.popPose();
        
        // 恢复渲染状态
        RenderSystem.lineWidth(1.0f);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        
        bufferSource.endBatch(RenderType.lines());
    }
    
    private static void renderConnectionLines(PoseStack poseStack, MultiBufferSource bufferSource, 
                                             List<BlockPos> targetBlocks, String mineralName, Minecraft mc) {
        if (targetBlocks == null || targetBlocks.isEmpty() || mc.player == null) {
            return;
        }
        
        // 获取玩家位置（使用身体中心位置，而不是眼睛位置）
        Vec3 playerPos = mc.player.position().add(0, 0.5, 0);
        
        // 计算矿物颜色
        int color = OreDetectorHelper.getOreColor(mineralName);
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float a = 0.9F;
        
        // 检测连接的矿物组
        List<List<BlockPos>> connectedGroups = findConnectedGroups(targetBlocks);
        
        // 为每个连接组绘制一条线
        for (List<BlockPos> group : connectedGroups) {
            if (!group.isEmpty()) {
                // 选择组中最接近玩家的方块作为连接线的起点
                BlockPos closestPos = findClosestBlock(group, mc.player.blockPosition());
                if (closestPos != null) {
                    // 计算矿物方块中心位置
                    double x = closestPos.getX() + 0.5;
                    double y = closestPos.getY() + 0.5;
                    double z = closestPos.getZ() + 0.5;
                    
                    // 绘制连接线
                    VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
                    
                    // 绘制多条线，创建发光效果
                    for (int i = 0; i < 3; i++) {
                        float offset = i * 0.02f;
                        float currentAlpha = a * (0.8f - i * 0.2f);
                        
                        // 起点（矿物中心）
                        vertexConsumer.vertex(poseStack.last().pose(), 
                            (float) x, 
                            (float) y, 
                            (float) z)
                            .color(r, g, b, currentAlpha)
                            .normal(poseStack.last().normal(), 0, 1, 0)
                            .endVertex();
                        
                        // 终点（玩家身体中心位置）
                        vertexConsumer.vertex(poseStack.last().pose(), 
                            (float) playerPos.x, 
                            (float) playerPos.y, 
                            (float) playerPos.z)
                            .color(r, g, b, currentAlpha)
                            .normal(poseStack.last().normal(), 0, 1, 0)
                            .endVertex();
                    }
                }
            }
        }
    }
    
    private static List<List<BlockPos>> findConnectedGroups(List<BlockPos> blocks) {
        List<List<BlockPos>> groups = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        
        for (BlockPos pos : blocks) {
            if (!visited.contains(pos)) {
                List<BlockPos> group = new ArrayList<>();
                Queue<BlockPos> queue = new LinkedList<>();
                
                queue.add(pos);
                visited.add(pos);
                
                while (!queue.isEmpty()) {
                    BlockPos current = queue.poll();
                    group.add(current);
                    
                    // 检查相邻的6个方向
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                // 只检查相邻的方块（曼哈顿距离为1）
                                if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 1) {
                                    BlockPos neighbor = current.offset(dx, dy, dz);
                                    if (blocks.contains(neighbor) && !visited.contains(neighbor)) {
                                        queue.add(neighbor);
                                        visited.add(neighbor);
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (!group.isEmpty()) {
                    groups.add(group);
                }
            }
        }
        
        return groups;
    }
    
    private static BlockPos findClosestBlock(List<BlockPos> blocks, BlockPos reference) {
        BlockPos closest = null;
        double minDistance = Double.MAX_VALUE;
        
        for (BlockPos pos : blocks) {
            double distance = pos.distSqr(reference);
            if (distance < minDistance) {
                minDistance = distance;
                closest = pos;
            }
        }
        
        return closest;
    }
    
    private static void renderGlowingOutline(PoseStack poseStack, VertexConsumer vertexConsumer,
                                           double x, double y, double z,
                                           float r, float g, float b, float a) {
        // 绘制三个不同大小的线框，创建发光效果
        for (int i = 0; i < 3; i++) {
            double offset = i * 0.01;
            LevelRenderer.renderLineBox(
                poseStack,
                vertexConsumer,
                x - offset, y - offset, z - offset,
                x + 1 + offset, y + 1 + offset, z + 1 + offset,
                r, g, b, a * (0.8f - i * 0.2f)
            );
        }
    }
    
    // ========== 渲染粒子效果 ==========
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        
        if (player == null || mc.level == null) {
            return;
        }
        
        // 每3帧更新一次粒子
        if (particleTickCounter++ % 3 != 0) {
            return;
        }
        
        List<BlockPos> targetBlocks = null;
        String mineralName = null;
        
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof OreDetectorItem) {
            targetBlocks = OreDetectorItem.getActiveTargets(mainHand, mc.level);
            mineralName = OreDetectorItem.getCurrentMineral(mainHand);
        }
        
        if ((targetBlocks == null || targetBlocks.isEmpty()) || mineralName == null || mineralName.isEmpty()) {
            ItemStack offHand = player.getOffhandItem();
            if (offHand.getItem() instanceof OreDetectorItem) {
                targetBlocks = OreDetectorItem.getActiveTargets(offHand, mc.level);
                mineralName = OreDetectorItem.getCurrentMineral(offHand);
            }
        }
        
        if (targetBlocks != null && !targetBlocks.isEmpty() && mineralName != null && !mineralName.isEmpty()) {
            renderParticles(targetBlocks, mineralName, mc);
        }
        
        if (particleTickCounter > 1000) particleTickCounter = 0;
    }
    
    private static void renderParticles(List<BlockPos> targetBlocks, 
                                       String mineralName,
                                       Minecraft mc) {
        int color = OreDetectorHelper.getOreColor(mineralName);
        
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        
        Vector3f colorVector = new Vector3f(r, g, b);
        
        int particlesAdded = 0;
        for (BlockPos pos : targetBlocks) {
            if (particlesAdded >= 3) { // 限制粒子数量
                break;
            }
            
            double distance = pos.distSqr(mc.player.blockPosition());
            if (distance <= 16 * 16) { // 16格内
                double centerX = pos.getX() + 0.5;
                double centerY = pos.getY() + 0.5;
                double centerZ = pos.getZ() + 0.5;
                
                // 在方块上方生成粒子
                mc.level.addParticle(
                    new DustParticleOptions(colorVector, 1.0f),
                    centerX + (RANDOM.nextDouble() - 0.5) * 0.5,
                    centerY + 1.0 + RANDOM.nextDouble() * 0.5,
                    centerZ + (RANDOM.nextDouble() - 0.5) * 0.5,
                    0, 0.05, 0
                );
                
                particlesAdded++;
            }
        }
    }
}