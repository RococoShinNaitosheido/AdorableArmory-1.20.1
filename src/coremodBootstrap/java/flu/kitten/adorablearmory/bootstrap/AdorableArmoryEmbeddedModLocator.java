package flu.kitten.adorablearmory.bootstrap;

import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class AdorableArmoryEmbeddedModLocator extends AbstractJarFileModLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdorableArmoryEmbeddedModLocator.class);
    private static final String EMBEDDED_MAIN_JAR = "/META-INF/adorablearmory/adorablearmory-main.jar";
    private Path extractedMainJar;

    @Override
    public List<ModFileOrException> scanMods() {
        Path mainJar = locateEmbeddedMainJar();
        if (mainJar == null) {
            return List.of();
        }
        return super.scanMods();
    }

    @Override
    public Stream<Path> scanCandidates() {
        return extractedMainJar == null ? Stream.empty() : Stream.of(extractedMainJar);
    }

    @Override
    public String name() {
        return "adorablearmory embedded main mod";
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {
    }

    private Path locateEmbeddedMainJar() {
        if (extractedMainJar != null) {
            return extractedMainJar;
        }

        try (InputStream input = AdorableArmoryEmbeddedModLocator.class.getResourceAsStream(EMBEDDED_MAIN_JAR)) {
            if (input == null) {
                return null;
            }

            byte[] bytes = input.readAllBytes();
            String hash = sha256(bytes);
            Path cacheDir = FMLPaths.GAMEDIR.get().resolve(".adorablearmory").resolve("embedded-mods").resolve(hash.substring(0, 16));
            Files.createDirectories(cacheDir);

            Path output = cacheDir.resolve("adorablearmory-main.jar");
            if (!Files.exists(output)) {
                Files.write(output, bytes);
                LOGGER.info("[AdorableArmory] extracted embedded main mod jar to {}", output);
            }

            extractedMainJar = output;
            return output;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[AdorableArmory] failed to expose embedded main mod jar", e);
            return null;
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
