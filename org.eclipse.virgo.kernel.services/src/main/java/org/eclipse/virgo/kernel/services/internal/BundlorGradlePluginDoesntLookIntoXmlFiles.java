package org.eclipse.virgo.kernel.services.internal;

import org.eclipse.virgo.nano.core.KernelConfig;
import org.eclipse.virgo.kernel.artifact.bundle.BundleBridge;
import org.eclipse.virgo.kernel.artifact.library.LibraryBridge;
import org.eclipse.virgo.kernel.artifact.par.ParBridge;
import org.eclipse.virgo.kernel.artifact.plan.PlanBridge;
import org.eclipse.virgo.kernel.artifact.properties.PropertiesBridge;
import org.osgi.service.cm.ConfigurationAdmin;

public class BundlorGradlePluginDoesntLookIntoXmlFiles {

    private KernelConfig kernelConfig;
    private BundleBridge bundleBridge;
    private LibraryBridge libraryBridge;
    private ParBridge parBridge;
    private PlanBridge planBridge;
    private PropertiesBridge propertiesBridge;
    private ConfigurationAdmin configurationAdmin;
}
