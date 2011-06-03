
package org.eclipse.virgo.kernel.deployer.p2.publish.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.equinox.internal.p2.metadata.ArtifactKey;
import org.eclipse.equinox.internal.p2.publisher.Messages;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IProvidedCapability;
import org.eclipse.equinox.p2.metadata.MetadataFactory;
import org.eclipse.equinox.p2.metadata.MetadataFactory.InstallableUnitDescription;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.publisher.AbstractPublisherAction;
import org.eclipse.equinox.p2.publisher.IPublisherAction;
import org.eclipse.equinox.p2.publisher.IPublisherInfo;
import org.eclipse.equinox.p2.publisher.IPublisherResult;
import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;
import org.eclipse.equinox.spi.p2.publisher.PublisherHelper;

/**
 * Publishes a zipped resource artifact
 * <strong>Concurrent Semantics</strong><br />
 * Thread-safe.
 */
public class ZippedResourcesPublisherAction extends AbstractPublisherAction {

    private static final Version DEFAULT_VERSION = Version.createOSGi(1, 0, 0);

    private static final String ZIPPED_RESOURCES_NAME = "zipped.resources";

    private final File[] locations;

    private final Object monitor = new Object();

    public ZippedResourcesPublisherAction(File[] locations) {
        this.locations = locations;
    }

    /**
     * Executes the action, resulting in a published artifact and metadata for it
     * 
     * @param publisherInfo - initialized {@link IPublisherInfo} with repositories to be used by this {@link IPublisherAction}
     * @param results - {@link IPublisherResult} that will be passed on the next publishing stages
     * @param monitor - {@link IProgressMonitor} used for monitoring the progress of this action, can be <b>null</b>
     * @return - the {@link IStatus} containing the result of the operation
     */
    @Override
    public IStatus perform(IPublisherInfo publisherInfo, IPublisherResult results, IProgressMonitor monitor) {
        if (this.locations == null) {
            throw new IllegalStateException(Messages.exception_noBundlesOrLocations);
        }
        synchronized (this.monitor) {
            setPublisherInfo(publisherInfo);
            try {
                publishZippedIUs(publisherInfo, results, monitor);
            } catch (OperationCanceledException e) {
                return Status.CANCEL_STATUS;
            }
        }
        return Status.OK_STATUS;
    }

    private void publishZippedIUs(IPublisherInfo publisherInfo, IPublisherResult results, IProgressMonitor monitor) {

        InstallableUnitDescription iuDescription = createZippedResourceIUDescriptionShell();

        addZippedResourcesToIUDescription(publisherInfo, iuDescription);

        setTouchpointInstructionsToIUDescription(iuDescription);

        results.addIU(MetadataFactory.createInstallableUnit(iuDescription), IPublisherResult.ROOT);

    }

    private void setTouchpointInstructionsToIUDescription(InstallableUnitDescription iuDescription) {
        Map<String, String> touchpointData = new HashMap<String, String>();
        // the install folder is moved two folders up in order to ensure Virgo's root structure is kept the same
        touchpointData.put("install", "unzip(source:@artifact, target:${installFolder}/../../);");
        touchpointData.put("uninstall", "cleanupzip(source:@artifact, target:${installFolder}/../../);");
        iuDescription.addTouchpointData(MetadataFactory.createTouchpointData(touchpointData));
    }

    private void addZippedResourcesToIUDescription(IPublisherInfo publisherInfo, InstallableUnitDescription iuDescription) {
        List<IArtifactKey> zippedArtifacts = new ArrayList<IArtifactKey>();

        for (File zippedFile : this.locations) {
            IArtifactKey key = new ArtifactKey(PublisherHelper.BINARY_ARTIFACT_CLASSIFIER, zippedFile.getName(), DEFAULT_VERSION);
            IArtifactDescriptor zipDescriptor = PublisherHelper.createArtifactDescriptor(publisherInfo, key, zippedFile);
            publishArtifact(zipDescriptor, zippedFile, publisherInfo);
            zippedArtifacts.add(key);
        }

        iuDescription.setArtifacts(zippedArtifacts.toArray(new IArtifactKey[zippedArtifacts.size()]));
    }

    private InstallableUnitDescription createZippedResourceIUDescriptionShell() {
        InstallableUnitDescription iuDescription = new MetadataFactory.InstallableUnitDescription();
        iuDescription.setId(ZIPPED_RESOURCES_NAME);
        iuDescription.setVersion(DEFAULT_VERSION);

        ArrayList<IProvidedCapability> providedCapabilities = new ArrayList<IProvidedCapability>();
        IProvidedCapability p2IUCapability = MetadataFactory.createProvidedCapability(IInstallableUnit.NAMESPACE_IU_ID, iuDescription.getId(),
            DEFAULT_VERSION);
        providedCapabilities.add(p2IUCapability);
        iuDescription.setCapabilities(providedCapabilities.toArray(new IProvidedCapability[providedCapabilities.size()]));
        iuDescription.setTouchpointType(MetadataFactory.createTouchpointType("org.eclipse.equinox.p2.native", DEFAULT_VERSION));
        return iuDescription;
    }
}
