package org.eclipse.virgo.kernel.osgi.internal;

import org.eclipse.virgo.nano.core.Shutdown;
import org.eclipse.virgo.repository.Repository;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.cm.ConfigurationAdmin;

public class BundlorGradlePluginDoesntLookIntoXmlFiles {

    private Shutdown shutdown;
    private Repository repository;
    private EventAdmin eventAdmin;
    private ConfigurationAdmin configurationAdmin;
}
