
package org.eclipse.virgo.kernel.p2.build;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Main {

    protected static final String SIMPLECONFIGURATOR_BSN = "org.eclipse.equinox.simpleconfigurator";

    private static final String ORG_ECLIPSE_OSGI = "org.eclipse.osgi";

    private static final String BSN_VERSION_SEPARATOR = "_";

    private static final String SC_CONFIG_DIR = "configuration/" + SIMPLECONFIGURATOR_BSN;

    private static final String BINFO_FILE_NAME = "bundles.info";

    private static final String P2_CLIENT_DIR = "p2";

    public static void main(String[] args) throws IOException {
        if (args != null && args.length == 1) {
            String baseDir = args[0];

            String bundlesInfoContent = new String();
            File baseDirFile = new File(baseDir + File.separatorChar + P2_CLIENT_DIR);
            if (baseDirFile.isDirectory()) {
                for (File file : baseDirFile.listFiles()) {
                    if (file.getName().endsWith(".jar") && file.getName().contains(BSN_VERSION_SEPARATOR)) {
                        String[] bsnVersionPair = file.getName().split(BSN_VERSION_SEPARATOR);
                        String name = bsnVersionPair[0];
                        String version = bsnVersionPair[1];
                        version = version.substring(0, version.indexOf(".jar"));
                        int startLevel = 4;
                        if (name.equals(SIMPLECONFIGURATOR_BSN)) {
                            startLevel = 1;
                        }
                        if (name.equals(ORG_ECLIPSE_OSGI)) {
                            startLevel = -1;
                        }
                        bundlesInfoContent = bundlesInfoContent + name + "," + version + "," + file.getName() + "," + startLevel + "," + "true\n";
                    }
                }
            }
            
            File bundlesInfoFolder = new File(baseDir + File.separatorChar + P2_CLIENT_DIR + File.separatorChar + SC_CONFIG_DIR);
            bundlesInfoFolder.mkdirs();
            File bundlesInfo = new File(baseDir + File.separatorChar + P2_CLIENT_DIR + File.separatorChar + SC_CONFIG_DIR + File.separatorChar + BINFO_FILE_NAME);
            bundlesInfo.createNewFile();
            FileWriter writer = null;
            try {
                writer = new FileWriter(bundlesInfo);
                writer.write(bundlesInfoContent);
            } catch (IOException e) {
                throw e;
            } finally {
                writer.flush();
                writer.close();
            }
        } else {
            throw new IllegalArgumentException("Required argument for build-kernel's location is missing or wrong.");
        }
    }

}
