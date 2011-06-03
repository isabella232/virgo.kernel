package org.eclipse.virgo.kernel.p2.build;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.core.ProvisionException;
import org.eclipse.equinox.p2.engine.IEngine;
import org.eclipse.equinox.p2.engine.IProfile;
import org.eclipse.equinox.p2.engine.IProfileRegistry;
import org.eclipse.equinox.p2.engine.IProvisioningPlan;
import org.eclipse.equinox.p2.engine.ProvisioningContext;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.planner.IPlanner;
import org.eclipse.equinox.p2.planner.IProfileChangeRequest;
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.QueryUtil;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.eclipse.virgo.kernel.deployer.p2.VirgoPublisher;

/**
 * This is a helper class used during building the kernel distribution to create a p2 repository and create a kernel distro for zip operation.
 * 
 * <strong>Concurrent Semantics</strong><br />
 * Not thread-safe.
 */
public class VirgoBuildP2RepoPublisher {

    private static final String P2_FOLDER = "/p2";

    private static final String OSGI_CONFIG_AREA = "/work/osgi/configuration";

    private static final String TARGET_LOCATION_OFFSET = "/lib/kernel";

    private static final String TARGET_PROFILE = "profile";

    private static final String IU_TO_INSTALL = "installIU";

    private static final String INSTALL_LOCATION = "install.location";

    private static final String SOURCE_P2_REPO = "source.p2.repo";

    private static final String LAUNCH_CONFIG_LOCATION = "launch.config.location";

    private static final String VIRGO_PRODUCT = "virgo.product";

    private static final String PRODUCT_LOCATION = "product.location";

    private static final String INSTALL_KERNEL = "installKernel";

    private static final String P2_REPO_PUBLISH = "p2repoPublish";

    private static final String OPERATION_TYPE = "operationType";

    public void activate(ComponentContext context) throws Exception {
        BundleContext bundleContext = context.getBundleContext();

        ServiceReference<VirgoPublisher> publisherRef = bundleContext.getServiceReference(VirgoPublisher.class);
        VirgoPublisher publisher = bundleContext.getService(publisherRef);

        String publishType = System.getProperty(OPERATION_TYPE);

        if (publishType.equals(P2_REPO_PUBLISH)) {
            String launchConfigLocation = System.getProperty(LAUNCH_CONFIG_LOCATION);
            String productLocation = System.getProperty(PRODUCT_LOCATION);
            
            launchConfigLocation = replaceBackslashesWithSlash(launchConfigLocation);
            productLocation = replaceBackslashesWithSlash(productLocation);
            
            new ProductFileBuilder().generateProductFile(productLocation, VIRGO_PRODUCT, launchConfigLocation);
            publisher.publishProduct(productLocation, productLocation);
        }

        if (publishType.equals(INSTALL_KERNEL)) {
            // sourceRepo location should start with 'file:' by default
            String sourceRepo = System.getProperty(SOURCE_P2_REPO);
            String installLocation = System.getProperty(INSTALL_LOCATION);
            String installIU = System.getProperty(IU_TO_INSTALL);
            String targetProfile = System.getProperty(TARGET_PROFILE);

            installLocation = replaceBackslashesWithSlash(installLocation);
            sourceRepo = replaceBackslashesWithSlash(sourceRepo);
            installProduct(bundleContext, sourceRepo, installLocation, installIU, targetProfile);
        }

        // We are done - exit now.
        System.exit(0);
    }

    private void installProduct(BundleContext bundleContext, String sourceRepoLocation, String installLocation, String installIU, String targetProfile)
        throws ProvisionException, URISyntaxException {
        IProvisioningAgent pAgent = initialiseProvisioningAgentForInstallLocation(bundleContext, installLocation);

        IMetadataRepositoryManager metadataRepoManager = (IMetadataRepositoryManager) pAgent.getService(IMetadataRepositoryManager.class.getName());

        ProvisioningContext provisioningContext = initialiseProvisioningContext(sourceRepoLocation, pAgent, metadataRepoManager);

        IProfile targetSystemProfile = initialiseTargetP2Profile(installLocation, targetProfile, pAgent);

        IInstallableUnit unitToInstall = getKernelProductInstallableUnit(sourceRepoLocation, installIU, metadataRepoManager);

        IProvisioningPlan plan = createPlan(targetSystemProfile, provisioningContext, pAgent, new IInstallableUnit[] { unitToInstall });

        IStatus status = executePlan(pAgent, plan);
        if (!status.isOK()) {
            throw new ProvisionException("Failed to install Virgo Kernel product");
        }
    }

    private IInstallableUnit getKernelProductInstallableUnit(String sourceRepoLocation, String installIU,
        IMetadataRepositoryManager metadataRepoManager) throws URISyntaxException, ProvisionException, OperationCanceledException {
        IMetadataRepository metadataRepo = loadMetadataRepository(sourceRepoLocation, metadataRepoManager);

        IQuery<IInstallableUnit> matchQuery = QueryUtil.createIUQuery(installIU);
        IQueryResult<IInstallableUnit> result = metadataRepo.query(matchQuery, null);
        IInstallableUnit unitToInstall = null;
        if (result.iterator().hasNext()) {
            unitToInstall = result.iterator().next();
        }
        return unitToInstall;
    }

    private ProvisioningContext initialiseProvisioningContext(String sourceRepoLocation, IProvisioningAgent pAgent,
        IMetadataRepositoryManager metadataRepoManager) throws URISyntaxException, ProvisionException, OperationCanceledException {
        IArtifactRepositoryManager artifactRepoManager = (IArtifactRepositoryManager) pAgent.getService(IArtifactRepositoryManager.class.getName());

        metadataRepoManager.loadRepository(new URI(sourceRepoLocation), null);
        artifactRepoManager.loadRepository(new URI(sourceRepoLocation), null);

        ProvisioningContext provisioningContext = new ProvisioningContext(pAgent);
        provisioningContext.setMetadataRepositories(metadataRepoManager.getKnownRepositories(0));
        provisioningContext.setArtifactRepositories(artifactRepoManager.getKnownRepositories(0));
        return provisioningContext;
    }

    private IMetadataRepository loadMetadataRepository(String sourceRepoLocation, IMetadataRepositoryManager metadataRepoManager)
        throws URISyntaxException, ProvisionException, OperationCanceledException {
        IMetadataRepository metadataRepo = metadataRepoManager.loadRepository(new URI(sourceRepoLocation), null);
        return metadataRepo;
    }

    private IProfile initialiseTargetP2Profile(String installLocation, String targetProfile, IProvisioningAgent pAgent) throws ProvisionException {
        IProfileRegistry registry = (IProfileRegistry) pAgent.getService(IProfileRegistry.class.getName());

        Map<String, String> props = new HashMap<String, String>();
        props.put(IProfile.PROP_INSTALL_FOLDER, installLocation + TARGET_LOCATION_OFFSET);
        props.put(IProfile.PROP_CACHE, installLocation + TARGET_LOCATION_OFFSET);
        props.put(IProfile.PROP_CONFIGURATION_FOLDER, installLocation + OSGI_CONFIG_AREA);
        String env = getEnvironmentProperty();
        if (env != null) {
            props.put(IProfile.PROP_ENVIRONMENTS, env);
        }

        IProfile targetSystemProfile = registry.addProfile(targetProfile, props);
        return targetSystemProfile;
    }

    private IProvisioningAgent initialiseProvisioningAgentForInstallLocation(BundleContext bundleContext, String installLocation)
        throws URISyntaxException, ProvisionException {
        ServiceReference<IProvisioningAgentProvider> pAgentRef = bundleContext.getServiceReference(IProvisioningAgentProvider.class);
        IProvisioningAgentProvider pAgentProvider = bundleContext.getService(pAgentRef);

        String agentInstallLocation = createAgentInstallLocation(installLocation);

        IProvisioningAgent pAgent = pAgentProvider.createAgent(new URI(agentInstallLocation));
        return pAgent;
    }

    private String createAgentInstallLocation(String installLocation) {
        // this is done to avoid 'URI not hierarchical' issue as the ProvisioningAgentProvider accepts hierarchical URIs
        String agentInstallLocation = "file:/" + installLocation;
        agentInstallLocation = agentInstallLocation + TARGET_LOCATION_OFFSET + P2_FOLDER;
        return agentInstallLocation;
    }

    private IStatus executePlan(IProvisioningAgent pAgent, IProvisioningPlan plan) {
        IEngine engine = (IEngine) pAgent.getService(IEngine.class.getName());
        IStatus planStatus = plan.getStatus();
        if (planStatus.getSeverity() == IStatus.ERROR || planStatus.getSeverity() == IStatus.CANCEL) {
            return planStatus;
        }

        if (plan.getInstallerPlan() != null) {
            IStatus installerPlanStatus = engine.perform(plan.getInstallerPlan(), null);
            if (!installerPlanStatus.isOK()) {
                return installerPlanStatus;
            }
        }
        return engine.perform(plan, null);
    }

    private IProvisioningPlan createPlan(IProfile profile, ProvisioningContext provisioningContext, IProvisioningAgent agent,
        IInstallableUnit[] unitToInstall) {
        IPlanner planner = (IPlanner) agent.getService(IPlanner.SERVICE_NAME);

        IProfileChangeRequest request = planner.createChangeRequest(profile);
        if (unitToInstall != null) {
            request.addAll(Arrays.asList(unitToInstall));
        }

        return planner.getProvisioningPlan(request, provisioningContext, null);
    }

    private String getEnvironmentProperty() {
        HashMap<String, String> values = new HashMap<String, String>();
        values.put("osgi.os", "all");
        values.put("osgi.ws", "all");
        values.put("osgi.arch", "all");
        return values.isEmpty() ? null : envToString(values);
    }

    private String envToString(Map<String, String> context) {
        StringBuffer result = new StringBuffer();
        for (Map.Entry<String, String> entry : context.entrySet()) {
            if (result.length() > 0) {
                result.append(',');
            }
            result.append(entry.getKey());
            result.append('=');
            result.append(entry.getValue());
        }
        return result.toString();
    }

    private String replaceBackslashesWithSlash(String location) {
        location = location.replace("\\", "/");
        return location;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
     */
    public void deactivate(ComponentContext bundleContext) throws Exception {

    }

}
