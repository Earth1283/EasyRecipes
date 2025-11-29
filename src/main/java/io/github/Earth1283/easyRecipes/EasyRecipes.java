package io.github.Earth1283.easyRecipes;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class EasyRecipes extends JavaPlugin {

    private RecipeLoader recipeLoader;

    @Override
    public void onEnable() {
        // Save the default config.yml if it doesn't exist
        saveDefaultConfig();

        // Initialize the RecipeLoader
        this.recipeLoader = new RecipeLoader(this);

        // Load and register all recipes
        loadRecipes();

        getLogger().info("EasyRecipes has been enabled!");
    }

    @Override
    public void onDisable() {
        // Unregister all custom recipes when the plugin shuts down
        recipeLoader.unregisterRecipes();
        getLogger().info("EasyRecipes has been disabled.");
    }

    /**
     * Loads recipes from the config file and registers them.
     */
    private void loadRecipes() {
        if (getConfig().getBoolean("enabled", true)) {
            try {
                // Ensure all old recipes are removed before loading new ones
                recipeLoader.unregisterRecipes();

                // Parse the YAML and register
                int count = recipeLoader.loadFromConfig();
                getLogger().info("Successfully loaded and registered " + count + " custom recipes.");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "An error occurred while loading recipes from config.yml:", e);
            }
        } else {
            getLogger().info("Custom recipes are disabled in config.yml.");
        }
    }

    /**
     * Handles the /easyrecipesreload command.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("easyrecipesreload")) {
            if (!sender.hasPermission("easyrecipes.reload")) {
                sender.sendMessage("§cYou do not have permission to use this command.");
                return true;
            }

            reloadConfig();
            loadRecipes();
            sender.sendMessage("§aEasyRecipes config and recipes reloaded successfully!");
            return true;
        }
        return false;
    }
}