package io.github.lgp547.anydoorplugin;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.function.UnaryOperator;

public interface AnyDoorInfo {
    String ANY_DOOR_NAME = "any-door";

    String ANY_DOOR_JAR_MIN_VERSION = VersionHolder.VERSION;

    String ANY_DOOR_ATTACH_JAR = "any-door-attach-" + ANY_DOOR_JAR_MIN_VERSION + ".jar";

    String ANY_DOOR_JAR = "any-door-core-" + ANY_DOOR_JAR_MIN_VERSION + ".jar";

    String ANY_DOOR_ALL_DEPENDENCE_JAR = "any-door-all-dependence.jar";

    String ANY_DOOR_COMMON_JAR = "any-door-common-" + ANY_DOOR_JAR_MIN_VERSION + ".jar";

    UnaryOperator<String> ANY_DOOR_JAR_PATH = version -> "/io/github/lgp547/any-door/" + version + "/any-door-core-" + version + ".jar";


    /**
     * name -> path
     */
    Map<String, String> libMap = Map.of("any-door", "io.github.lgp547");

    final class VersionHolder {
        private static final String VERSION = loadVersion();

        private VersionHolder() {
        }

        private static String loadVersion() {
            Properties properties = new Properties();
            try (InputStream inputStream = AnyDoorInfo.class.getResourceAsStream("/anydoor-plugin.properties")) {
                if (inputStream == null) {
                    throw new IllegalStateException("Missing anydoor-plugin.properties");
                }
                properties.load(inputStream);
                String version = properties.getProperty("anydoor.version");
                if (version == null || version.isBlank()) {
                    throw new IllegalStateException("Missing anydoor.version");
                }
                return version.trim();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load AnyDoor version", e);
            }
        }
    }

}
