package com.pokermc.datagen;

import com.pokermc.PokerMod;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.data.server.recipe.RecipeExporter;
import net.minecraft.data.server.recipe.ShapedRecipeJsonBuilder;
import net.minecraft.item.Items;
import net.minecraft.recipe.book.RecipeCategory;
import net.minecraft.registry.RegistryWrapper;

import java.util.concurrent.CompletableFuture;

public class PokerRecipeProvider extends FabricRecipeProvider {

    public PokerRecipeProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    public void generate(RecipeExporter exporter) {
        ShapedRecipeJsonBuilder.create(RecipeCategory.BUILDING_BLOCKS, PokerMod.POKER_TABLE_ITEM)
                .pattern("GGG")
                .pattern("G G")
                .pattern("W W")
                .input('G', Items.GREEN_WOOL)
                .input('W', Items.OAK_PLANKS)
                .criterion(hasItem(Items.GREEN_WOOL), conditionsFromItem(Items.GREEN_WOOL))
                .criterion(hasItem(Items.OAK_PLANKS), conditionsFromItem(Items.OAK_PLANKS))
                .offerTo(exporter);

        ShapedRecipeJsonBuilder.create(RecipeCategory.MISC, PokerMod.ZCOIN_BAG_ITEM)
                .pattern("LLL")
                .pattern("L L")
                .pattern("LLL")
                .input('L', Items.LEATHER)
                .criterion(hasItem(Items.LEATHER), conditionsFromItem(Items.LEATHER))
                .offerTo(exporter);

        ShapedRecipeJsonBuilder.create(RecipeCategory.BUILDING_BLOCKS, PokerMod.BLACKJACK_TABLE_ITEM)
                .pattern("RRR")
                .pattern("R R")
                .pattern("W W")
                .input('R', Items.PINK_WOOL)
                .input('W', Items.OAK_PLANKS)
                .criterion(hasItem(Items.PINK_WOOL), conditionsFromItem(Items.PINK_WOOL))
                .criterion(hasItem(Items.OAK_PLANKS), conditionsFromItem(Items.OAK_PLANKS))
                .offerTo(exporter);
    }
}
