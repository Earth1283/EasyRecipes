package io.github.Earth1283.easyRecipes;

import org.bukkit.ChatColor;
import org.bukkit.Keyed; // <-- ADDED IMPORT
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class RecipeLoader {

    private final EasyRecipes plugin;
    private final List<NamespacedKey> registeredKeys = new ArrayList<>();

    public RecipeLoader(EasyRecipes plugin) {
        this.plugin = plugin;
    }

    /**
     * Unregisters all custom recipes registered by this plugin.
     */
    public void unregisterRecipes() {
        Iterator<Recipe> it = plugin.getServer().recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();

            // FIX: Check if the recipe is Keyed (which all modern recipes are) and use getKey()
            if (recipe instanceof Keyed) {
                NamespacedKey key = ((Keyed) recipe).getKey();
                if (registeredKeys.contains(key)) {
                    it.remove();
                }
            }
        }
        plugin.getLogger().info("Unregistered " + registeredKeys.size() + " old custom recipes.");
        registeredKeys.clear();
    }

    /**
     * Parses the plugin's config.yml and registers all defined recipes.
     * @return The number of recipes successfully registered.
     */
    public int loadFromConfig() {
        ConfigurationSection recipesSection = plugin.getConfig().getConfigurationSection("recipes");
        if (recipesSection == null) {
            plugin.getLogger().warning("No 'recipes' section found in config.yml. No custom recipes loaded.");
            return 0;
        }

        Set<String> recipeKeys = recipesSection.getKeys(false);
        int registeredCount = 0;

        for (String recipeId : recipeKeys) {
            ConfigurationSection recipeSection = recipesSection.getConfigurationSection(recipeId);
            if (recipeSection == null) {
                plugin.getLogger().warning("Invalid recipe format for ID: " + recipeId);
                continue;
            }

            try {
                // 1. Parse the result ItemStack
                ItemStack result = parseItemStack(recipeSection.getConfigurationSection("result"));
                if (result == null) {
                    plugin.getLogger().warning("Skipping recipe '" + recipeId + "': Invalid result item definition.");
                    continue;
                }

                // 2. Determine recipe type
                String typeString = recipeSection.getString("type", "SHAPED").toUpperCase();
                RecipeType type;
                try {
                    type = RecipeType.valueOf(typeString);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Skipping recipe '" + recipeId + "': Invalid type '" + typeString + "'. Must be SHAPED or SHAPELESS.");
                    continue;
                }

                // 3. Register the recipe
                NamespacedKey key = new NamespacedKey(plugin, recipeId.toLowerCase());
                Recipe recipe = null;

                if (type == RecipeType.SHAPED) {
                    recipe = createShapedRecipe(key, result, recipeSection);
                } else if (type == RecipeType.SHAPELESS) {
                    recipe = createShapelessRecipe(key, result, recipeSection);
                }

                if (recipe != null) {
                    plugin.getServer().addRecipe(recipe);
                    registeredKeys.add(key);
                    registeredCount++;
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load recipe ID: " + recipeId, e);
            }
        }

        return registeredCount;
    }

    /**
     * Creates a ShapedRecipe instance from the configuration section.
     */
    private ShapedRecipe createShapedRecipe(NamespacedKey key, ItemStack result, ConfigurationSection section) {
        List<String> shape = section.getStringList("shape");
        ConfigurationSection keySection = section.getConfigurationSection("key");

        if (shape.isEmpty() || keySection == null) {
            plugin.getLogger().warning("Shaped recipe '" + key.getKey() + "' is missing 'shape' or 'key' definition.");
            return null;
        }

        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shape.toArray(new String[0]));

        for (String charKey : keySection.getKeys(false)) {
            if (charKey.length() != 1) continue;

            ConfigurationSection ingredientSection = keySection.getConfigurationSection(charKey);
            if (ingredientSection != null) {
                ItemStack ingredient = parseItemStack(ingredientSection);
                if (ingredient != null) {
                    // Using getType() is correct for modern Spigot APIs
                    recipe.setIngredient(charKey.charAt(0), ingredient.getType());
                } else {
                    plugin.getLogger().warning("Invalid ingredient definition for key '" + charKey + "' in recipe: " + key.getKey());
                }
            }
        }
        return recipe;
    }

    /**
     * Creates a ShapelessRecipe instance from the configuration section.
     */
    private ShapelessRecipe createShapelessRecipe(NamespacedKey key, ItemStack result, ConfigurationSection section) {
        List<?> ingredientsList = section.getList("ingredients");
        if (ingredientsList == null || ingredientsList.isEmpty()) {
            plugin.getLogger().warning("Shapeless recipe '" + key.getKey() + "' is missing 'ingredients' list.");
            return null;
        }

        ShapelessRecipe recipe = new ShapelessRecipe(key, result);

        for (Object ingredientObj : ingredientsList) {
            if (ingredientObj instanceof ConfigurationSection ingredientSection) {
                ItemStack ingredient = parseItemStack(ingredientSection);
                if (ingredient != null) {
                    // Using getType() is correct for modern Spigot APIs
                    recipe.addIngredient(ingredient.getType());
                } else {
                    plugin.getLogger().warning("Invalid ingredient definition in shapeless recipe: " + key.getKey());
                }
            } else {
                plugin.getLogger().warning("Ingredient list item is not a valid section in shapeless recipe: " + key.getKey());
            }
        }
        return recipe;
    }

    /**
     * Parses a ConfigurationSection into an ItemStack.
     * This handles material, amount, name, lore, and CustomModelData.
     */
    private ItemStack parseItemStack(ConfigurationSection section) {
        if (section == null) return null;

        String materialName = section.getString("material");
        if (materialName == null) return null;

        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            plugin.getLogger().warning("Invalid material name: " + materialName);
            return null;
        }

        int amount = section.getInt("amount", 1);
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();

        // Custom Name
        String name = section.getString("name");
        if (name != null && meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        }

        // Custom Lore
        List<String> lore = section.getStringList("lore");
        if (!lore.isEmpty() && meta != null) {
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(coloredLore);
        }

        // Custom Model Data
        int customModelData = section.getInt("custom-model-data", -1);
        if (customModelData != -1 && meta != null) {
            try {
                meta.setCustomModelData(customModelData);
            } catch (NoSuchMethodError e) {
                // Log a warning if the server version doesn't support CustomModelData (1.14+)
                plugin.getLogger().warning("CustomModelData is specified but not supported by this server version. Ignoring CMD for " + materialName);
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Enum to represent the supported recipe types for easy parsing.
     */
    private enum RecipeType {
        SHAPED,
        SHAPELESS
    }
}