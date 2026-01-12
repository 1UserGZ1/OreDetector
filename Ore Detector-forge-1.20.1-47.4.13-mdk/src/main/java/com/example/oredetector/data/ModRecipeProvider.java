package com.example.oredetector.data;

import com.example.oredetector.OreDetector;
import com.example.oredetector.item.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.crafting.conditions.IConditionBuilder;

import java.util.List;
import java.util.function.Consumer;

public class ModRecipeProvider extends RecipeProvider implements IConditionBuilder {

    public ModRecipeProvider(PackOutput pOutput) {
        super(pOutput);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> pWriter) {
        // 为矿物探测器添加合成配方
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, ModItems.ORE_DETECTOR.get())
                .pattern(" E ")
                .pattern("ERE")
                .pattern(" E ")
                .define('E', Items.EMERALD)
                .define('R', Items.REDSTONE)
                .unlockedBy(getHasName(Items.EMERALD), has(Items.EMERALD))
                .save(pWriter);
    }
}
