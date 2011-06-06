
package org.eclipse.virgo.kernel.deployer.p2.publish;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.equinox.internal.p2.artifact.repository.simple.SimpleArtifactRepository;
import org.eclipse.equinox.internal.p2.artifact.repository.simple.SimpleArtifactRepositoryFactory;
import org.eclipse.equinox.internal.p2.metadata.repository.SimpleMetadataRepositoryFactory;
import org.eclipse.equinox.internal.p2.publisher.Messages;
import org.eclipse.equinox.internal.p2.publisher.eclipse.IProductDescriptor;
import org.eclipse.equinox.internal.p2.publisher.eclipse.ProductFile;
import org.eclipse.equinox.p2.publisher.IPublisherAction;
import org.eclipse.equinox.p2.publisher.IPublisherInfo;
import org.eclipse.equinox.p2.publisher.Publisher;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.publisher.eclipse.ProductAction;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.eclipse.osgi.util.NLS;
import org.eclipse.virgo.kernel.deployer.p2.VirgoPublisher;
import org.eclipse.virgo.kernel.deployer.p2.publish.actions.VirgoBundlesAction;
import org.eclipse.virgo.kernel.deployer.p2.publish.actions.ZippedResourcesPublisherAction;
import org.osgi.service.component.ComponentContext;

/**
 * This {@link VirgoPublisher} handles the local FS publishing of artifacts and products.
 * 
 * <strong>Concurrent Semantics</strong><br />
 * Thread-safe.
 */
public class LocalFSVirgoPublisher implements VirgoPublisher {

    private static final String ZIP_EXT = ".zip";

    private static final String JAR_EXT = ".jar";

    private static final String ENV_PROPERTY = "all.all.all";

    private static final String VIRGO_PRODUCT = "/virgo.product";

    private static final String[][] VIRGO_MAPPING_RULES = new String[][] { { "(& (classifier=osgi.bundle))", "${repoUrl}/${id}_${version}.jar" },
        { "(& (classifier=binary))", "${repoUrl}/${id}" }, { "(& (classifier=org.eclipse.update.feature))", "${repoUrl}/${id}_${version}.jar" } };;

    private final Object monitor = new Object();

    public void activate(ComponentContext context) {
    }

    public void deactivate(ComponentContext context) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void publishProduct(String sourceLocation, String targetLocation) throws URISyntaxException {
        publishProduct(sourceLocation, targetLocation, null);
    }

    @Override
    public void publishProduct(String sourceLocation, String targetLocation, Map<String, String> additionalArgs) throws URISyntaxException {
        synchronized (this.monitor) {
            IPublisherInfo info = createPublisherInfo(targetLocation);
            Publisher publisher = new Publisher(info);
            publisher.publish(createPublisherActionsForQualifiedArtifacts(sourceLocation, additionalArgs), null);
            publisher.publish(createProductAction(sourceLocation, targetLocation), null);
        }

    }

    /**
     * Create the product publisher action. This action will take care of updating the p2 metadata repository with the
     * products description
     * 
     * @param sourceLocation - the folder where the product file is located
     * @param targetLocation - the location of the target repository that will be updated with the product file
     *        definition
     * @return - return the {@link ProductAction} that will handle the publishing of the product
     */
    private IPublisherAction[] createProductAction(String sourceLocation, String targetLocation) {
        IProductDescriptor productDescriptor = null;
        String productFile = sourceLocation + VIRGO_PRODUCT;
        try {
            productDescriptor = new ProductFile(productFile);
        } catch (Exception e) {
            throw new IllegalArgumentException(NLS.bind(Messages.exception_errorLoadingProductFile, productFile, e.toString()));
        }
        return new IPublisherAction[] { new ProductAction(targetLocation, productDescriptor, "tooling", null) };
    }

    /**
     * Initializes the publisher info used for the publishing operations.
     * 
     * @param targetLocation - the location where a p2 repository will be created with that {@link IPublisherInfo}.
     * @return - the {@link IPublisherInfo} that will be used to create a p2 repository in the specified location
     * @throws URISyntaxException - when the URI for the p2 repository is with wrong syntax
     */
    private IPublisherInfo createPublisherInfo(String targetLocation) throws URISyntaxException {
        targetLocation = targetLocation.startsWith("file:") ? targetLocation : "file:" + targetLocation;

        PublisherInfo pInfo = new PublisherInfo();

        pInfo.setConfigurations(new String[] { ENV_PROPERTY });

        initialisePublisherInfoRepositories(targetLocation, pInfo);
        pInfo.setArtifactOptions(IPublisherInfo.A_PUBLISH | IPublisherInfo.A_INDEX);
        return pInfo;
    }

    /**
     * Initializes the {@link IPublisherInfo} p2 metadata and artifact repositories on the specified location
     * 
     * @param targetLocation - the location where the p2 repository will be published
     * @param pInfo - the {@link IPublisherInfo} created for the target p2 repository
     * @throws URISyntaxException - when the URI for the artifact or metadata p2 repositories is with wrong syntax
     */
    @SuppressWarnings("unchecked")
    private void initialisePublisherInfoRepositories(String targetLocation, PublisherInfo pInfo) throws URISyntaxException {
        IMetadataRepository metadataRepository = new SimpleMetadataRepositoryFactory().create(new URI(targetLocation), "Virgo Metadata Repository",
            IMetadataRepositoryManager.TYPE_SIMPLE_REPOSITORY, Collections.EMPTY_MAP);

        IArtifactRepository artifactRepository = new SimpleArtifactRepositoryFactory().create(new URI(targetLocation), "Virgo Artifact Repository",
            IArtifactRepositoryManager.TYPE_SIMPLE_REPOSITORY, Collections.EMPTY_MAP);

        ((SimpleArtifactRepository) artifactRepository).setRules(VIRGO_MAPPING_RULES);
        ((SimpleArtifactRepository) artifactRepository).initializeAfterLoad(new URI(targetLocation));

        pInfo.setMetadataRepository(metadataRepository);
        pInfo.setArtifactRepository(artifactRepository);
    }

    /**
     * Creates {@link IPublisherAction} for each qualified file at the specified location
     * 
     * @param location - a location containing the artifacts that will be published in a p2 repository
     * @return - an array of {@link IPublisherAction} that will be used for publishing to a p2 repository
     */
    private IPublisherAction[] createPublisherActionsForQualifiedArtifacts(String location, Map<String, String> args) {
        location = location.startsWith("file:") ? location.substring(5) : location;

        File repository = new File(location);
        List<File> bundles = new ArrayList<File>();
        List<File> zippedFiles = new ArrayList<File>();

        collectQualifiedArtifacts(location, repository, bundles, zippedFiles);

        List<IPublisherAction> publisherActions = createPublisherActions(bundles, zippedFiles, args);
        return publisherActions.toArray(new IPublisherAction[publisherActions.size()]);
    }

    private List<IPublisherAction> createPublisherActions(List<File> bundles, List<File> zippedFiles, Map<String, String> args) {
        List<IPublisherAction> publisherActions = new ArrayList<IPublisherAction>();
        if (bundles.size() > 0) {
            VirgoBundlesAction bundleAction = new VirgoBundlesAction(bundles.toArray(new File[bundles.size()]));
            publisherActions.add(bundleAction);
        }
        if (zippedFiles.size() > 0) {
            ZippedResourcesPublisherAction zippedAction = new ZippedResourcesPublisherAction(zippedFiles.toArray(new File[zippedFiles.size()]), args);
            publisherActions.add(zippedAction);
        }
        return publisherActions;
    }

    private void collectQualifiedArtifacts(String location, File repository, List<File> bundles, List<File> zippedFiles) {
        if (repository.isDirectory()) {
            for (File file : repository.listFiles()) {
                if (file.getName().endsWith(JAR_EXT) || file.isDirectory()) {
                    bundles.add(file);
                }
                if (file.getName().endsWith(ZIP_EXT)) {
                    zippedFiles.add(file);
                }
            }
        } else {
            if (location.endsWith(JAR_EXT)) {
                bundles.add(new File(location));
            }
            if (location.endsWith(ZIP_EXT)) {
                zippedFiles.add(new File(location));
            }
        }
    }
}
