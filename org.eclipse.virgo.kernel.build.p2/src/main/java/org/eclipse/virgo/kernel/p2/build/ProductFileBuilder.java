
package org.eclipse.virgo.kernel.p2.build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Helper class for building product files from a defined startup configuration
 * <strong>Concurrent Semantics</strong><br />
 * Thread-safe.
 */
public class ProductFileBuilder {

    private static final String PROPERTY_PREFIX = "<property name=";

    private static final String START_LEVEL_1 = "startLevel=\"1\"";

    private static final String PRODUCT_SUFFIX = "</product>\n";

    private static final String CONFIGURATIONS_SUFFIX = "</configurations>\n";

    private static final String CONFIGURATIONS_PREFIX = "<configurations>\n";

    private static final String PLUGINS_SUFFIX = "</plugins>\n";

    private static final String PLUGINS_PREFIX = "<plugins>\n";

    private static final String PRODUCT_PREFIX = "<?xml version=\"1.0\"?>\n<?pde version=\"3.5\"?>\n<product name=\"Virgo Kernel Distribution\" uid=\"virgo.product\" version=\"1.0.0\" useFeatures=\"false\" includeLaunchers=\"false\">\n";

    private static final String ELEMENT_SUFFIX = "/>\n";

    private static final String PLUGIN_ELEMENT_PREFIX = "<plugin id=";

    private static final String AUTO_START_INSTRUCTION = "autoStart=\"true\"";

    private static final String BSN_VERSION_SEPARATOR = "_";

    private static final String PROP_LAUNCHER_BUNDLES = "launcher.bundles";

    private final Object monitor = new Object();

    /**
     * Generates a product file from launch configuration
     * @param targetLocation - the location where the product file will be saved
     * @param productFileName - the product file's name
     * @param launchConfigLocation - the location of the launch configuration
     * @throws IOException - when the write operation on the product file failed
     */
    public void generateProductFile(String targetLocation, String productFileName, String launchConfigLocation) throws IOException {
        synchronized (this.monitor) {
            File bundlesDir = new File(targetLocation);
            if (bundlesDir.isDirectory()) {
                File productFile = new File(targetLocation + "/" + productFileName);
                if (productFile.exists()) {
                    productFile.delete();
                    productFile.createNewFile();
                }
                FileWriter writer = new FileWriter(productFile);
                writer.write(PRODUCT_PREFIX);

                Map<String, String> productConfiguration = initializeProductConfiguration(launchConfigLocation);

                writeProductPlugins(bundlesDir, writer, productConfiguration);
                writeProductConfiguration(writer, productConfiguration);

                writer.write(PRODUCT_SUFFIX);
                writer.flush();
                writer.close();
            }
        }
    }

    private void writeProductConfiguration(FileWriter writer, Map<String, String> productConfiguration) throws IOException {
        writer.write(CONFIGURATIONS_PREFIX);
        for (String configuration : productConfiguration.values()) {
            writer.write(configuration);
        }
        writer.write(CONFIGURATIONS_SUFFIX);
    }

    private void writeProductPlugins(File bundlesDir, FileWriter writer, Map<String, String> productConfiguration) throws IOException {
        writer.write(PLUGINS_PREFIX);
        for (File bundle : bundlesDir.listFiles()) {
            if (bundle.getName().endsWith(".jar")) {
                String bsn = bundle.getName().substring(0, bundle.getName().indexOf(BSN_VERSION_SEPARATOR));
                if (productConfiguration.containsKey(bsn)) {
                    writer.write(PLUGIN_ELEMENT_PREFIX + "\"" + bsn + "\"" + ELEMENT_SUFFIX);
                }
            }
        }
        writer.write(PLUGINS_SUFFIX);
    }

    private Properties parseConfigProperties(String configPath) throws IOException {
        File file = new File(configPath);
        if (!file.exists()) {
            throw new FileNotFoundException("Config path '" + file.getAbsolutePath() + "' does not exist.");
        }
        Properties props = new Properties();
        InputStream stream = null;
        try {
            stream = new FileInputStream(file);
            props.load(stream);
            return props;
        } catch (IOException e) {
            throw new IOException("Unable to read config properties file '" + file.getAbsolutePath() + "'.", e);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ex) {
                }
            }
        }
    }

    private Map<String, String> parseBundlesToBeStarted(String entryList) {
        Map<String, String> bundlesConfiguration = new HashMap<String, String>();

        String[] entries = entryList.split(",");
        for (String entry : entries) {
            boolean isAutoStart = entry.contains("@start");
            String bsn = entry.substring(0, entry.indexOf(BSN_VERSION_SEPARATOR));
            if (isAutoStart) {
                bundlesConfiguration.put(bsn, createProductInstruction(bsn));
            }
        }
        return bundlesConfiguration;
    }

    private String createProductInstruction(String bsn) {
        if (bsn.equals(Main.SIMPLECONFIGURATOR_BSN)) {
            return PLUGIN_ELEMENT_PREFIX + "\"" + bsn + "\" " + AUTO_START_INSTRUCTION + " " + START_LEVEL_1 + ELEMENT_SUFFIX;
        } 
        return PLUGIN_ELEMENT_PREFIX + "\"" + bsn + "\" " + AUTO_START_INSTRUCTION + ELEMENT_SUFFIX;
    }

    private Map<String, String> initializeProductConfiguration(String launchConfigLocation) throws IOException {

        Properties launchConfig = parseConfigProperties(launchConfigLocation);

        Map<String, String> productConfiguration = new HashMap<String, String>();

        String launcherBundles = launchConfig.getProperty(PROP_LAUNCHER_BUNDLES);
        if (launcherBundles != null) {
            productConfiguration.putAll(parseBundlesToBeStarted(launcherBundles));
        }

        launchConfig.remove(PROP_LAUNCHER_BUNDLES);

        for (String propKey : launchConfig.stringPropertyNames()) {
            productConfiguration.put(propKey, PROPERTY_PREFIX + "\"" + propKey + "\" value=\"" + launchConfig.getProperty(propKey) + "\"" + ELEMENT_SUFFIX);   
        }

        return productConfiguration;
    }
}
