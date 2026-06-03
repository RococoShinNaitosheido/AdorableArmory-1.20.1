package flu.kitten.adorablearmory.coremod;

import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Based on the ModLauncher plugin injection pattern from WoZhiZhan/CoreModAPI
 * (Apache-2.0), adapted as project-local code for AdorableArmory.
 */
public final class AdorableArmoryTransformationService implements ITransformationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdorableArmoryTransformationService.class);
    private static boolean initialized;

    @Override
    public @NotNull String name() {
        return "adorablearmory_coremod";
    }

    @Override
    public void initialize(IEnvironment environment) {
        if (initialized) return;
        initialized = true;
        injectTrueDemonLaunchPlugin();
    }

    @SuppressWarnings("unchecked")
    private static void injectTrueDemonLaunchPlugin() {
        try {
            Field launchPluginsField = Launcher.class.getDeclaredField("launchPlugins");
            launchPluginsField.setAccessible(true);
            stripFinalModifier(launchPluginsField);

            LaunchPluginHandler handler = (LaunchPluginHandler) launchPluginsField.get(Launcher.INSTANCE);
            Field pluginsField = LaunchPluginHandler.class.getDeclaredField("plugins");
            pluginsField.setAccessible(true);
            stripFinalModifier(pluginsField);

            Map<String, ILaunchPluginService> current = (Map<String, ILaunchPluginService>) pluginsField.get(handler);
            if (current.containsKey(TrueDemonLaunchPlugin.PLUGIN_NAME)) {
                return;
            }

            ILaunchPluginService plugin = new TrueDemonLaunchPlugin();
            try {
                Map<String, ILaunchPluginService> ordered = new LinkedHashMap<>();
                ordered.put(TrueDemonLaunchPlugin.PLUGIN_NAME, plugin);
                ordered.putAll(current);
                pluginsField.set(handler, ordered);
            } catch (Throwable setFailure) {
                current.put(TrueDemonLaunchPlugin.PLUGIN_NAME, plugin);
            }
            LOGGER.info("[AdorableArmory] injected {}", TrueDemonLaunchPlugin.PLUGIN_NAME);
        } catch (Throwable t) {
            LOGGER.error("[AdorableArmory] failed to inject true demon launch plugin", t);
        }
    }

    private static void stripFinalModifier(Field field) {
        try {
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {
    }

    @Override
    public @NotNull List<ITransformer> transformers() {
        return List.of();
    }
}
