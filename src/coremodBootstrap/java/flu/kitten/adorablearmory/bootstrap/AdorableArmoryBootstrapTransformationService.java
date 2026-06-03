package flu.kitten.adorablearmory.bootstrap;

import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AdorableArmoryBootstrapTransformationService implements ITransformationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdorableArmoryBootstrapTransformationService.class);
    private static boolean initialized;

    @Override
    public String name() {
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
            if (current.containsKey(TrueDemonBootstrapLaunchPlugin.PLUGIN_NAME)) {
                return;
            }

            ILaunchPluginService plugin = new TrueDemonBootstrapLaunchPlugin();
            try {
                Map<String, ILaunchPluginService> ordered = new LinkedHashMap<>();
                ordered.put(TrueDemonBootstrapLaunchPlugin.PLUGIN_NAME, plugin);
                ordered.putAll(current);
                pluginsField.set(handler, ordered);
            } catch (Throwable setFailure) {
                current.put(TrueDemonBootstrapLaunchPlugin.PLUGIN_NAME, plugin);
            }
            LOGGER.info("[AdorableArmory] injected {}", TrueDemonBootstrapLaunchPlugin.PLUGIN_NAME);
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
    public List<ITransformer> transformers() {
        return List.of();
    }
}
