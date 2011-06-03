package org.eclipse.virgo.kernel.deployer.p2.publish.actions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;
import org.eclipse.equinox.p2.metadata.IInstallableUnitFragment;
import org.eclipse.equinox.p2.metadata.IProvidedCapability;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.metadata.IUpdateDescriptor;
import org.eclipse.equinox.p2.metadata.MetadataFactory;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.metadata.VersionRange;
import org.eclipse.equinox.p2.metadata.MetadataFactory.InstallableUnitDescription;
import org.eclipse.equinox.p2.metadata.MetadataFactory.InstallableUnitFragmentDescription;
import org.eclipse.equinox.p2.publisher.IPublisherInfo;
import org.eclipse.equinox.p2.publisher.IPublisherResult;
import org.eclipse.equinox.p2.publisher.AdviceFileAdvice;
import org.eclipse.equinox.p2.publisher.eclipse.BundlesAction;
import org.eclipse.equinox.p2.publisher.eclipse.IBundleShapeAdvice;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;
import org.eclipse.equinox.spi.p2.publisher.LocalizationHelper;
import org.eclipse.equinox.spi.p2.publisher.PublisherHelper;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.BundleSpecification;
import org.eclipse.osgi.service.resolver.ExportPackageDescription;
import org.eclipse.osgi.service.resolver.HostSpecification;
import org.eclipse.osgi.service.resolver.ImportPackageSpecification;
import org.osgi.framework.Constants;

/**
 * This class provides extension to the standard p2 BundlesAction by including the whole manifest in the published metadata.
 *
 */
public class VirgoBundlesAction extends BundlesAction {
    
    private static final String CAPABILITY_NS_OSGI_BUNDLE = "osgi.bundle"; //$NON-NLS-1$
    private static final String CAPABILITY_NS_OSGI_FRAGMENT = "osgi.fragment"; //$NON-NLS-1$
    
    private static final String[] BUNDLE_IU_PROPERTY_MAP = {Constants.BUNDLE_NAME, IInstallableUnit.PROP_NAME, Constants.BUNDLE_DESCRIPTION, IInstallableUnit.PROP_DESCRIPTION, Constants.BUNDLE_VENDOR, IInstallableUnit.PROP_PROVIDER, Constants.BUNDLE_CONTACTADDRESS, IInstallableUnit.PROP_CONTACT, Constants.BUNDLE_DOCURL, IInstallableUnit.PROP_DOC_URL, Constants.BUNDLE_UPDATELOCATION, IInstallableUnit.PROP_BUNDLE_LOCALIZATION, Constants.BUNDLE_LOCALIZATION, IInstallableUnit.PROP_BUNDLE_LOCALIZATION};

    public VirgoBundlesAction(BundleDescription[] bundles) {
        super(bundles);
    }
    
    public VirgoBundlesAction(File[] files) {
        super(files);
    }

    @Override
    protected void generateBundleIUs(BundleDescription[] bundleDescriptions, IPublisherInfo info, IPublisherResult result, IProgressMonitor monitor) {
     // This assumes that hosts are processed before fragments because for each fragment the host
        // is queried for the strings that should be translated.
        for (int i = 0; i < bundleDescriptions.length; i++) {
            if (monitor.isCanceled())
                throw new OperationCanceledException();

            BundleDescription bd = bundleDescriptions[i];
            if (bd != null && bd.getSymbolicName() != null && bd.getVersion() != null) {
                //First check to see if there is already an IU around for this
                IInstallableUnit bundleIU = queryForIU(result, bundleDescriptions[i].getSymbolicName(), PublisherHelper.fromOSGiVersion(bd.getVersion()));
                IArtifactKey key = createBundleArtifactKey(bd.getSymbolicName(), bd.getVersion().toString());
                if (bundleIU == null) {
                    createAdviceFileAdvice(bundleDescriptions[i], info);
                    // Create the bundle IU according to any shape advice we have
                    bundleIU = createBundleIU(bd, key, info);
                }

                File location = new File(bd.getLocation());
                IArtifactDescriptor ad = PublisherHelper.createArtifactDescriptor(info, key, location);
                processArtifactPropertiesAdvice(bundleIU, ad, info);

                // Publish according to the shape on disk
                File bundleLocation = new File(bd.getLocation());
                if (bundleLocation.isDirectory())
                    publishArtifact(ad, bundleLocation, bundleLocation.listFiles(), info);
                else
                    publishArtifact(ad, bundleLocation, info);

                IInstallableUnit fragment = null;
                if (isFragment(bd)) {
                    // TODO: Need a test case for multiple hosts
                    String hostId = bd.getHost().getName();
                    VersionRange hostVersionRange = PublisherHelper.fromOSGiVersionRange(bd.getHost().getVersionRange());
                    IQueryResult<IInstallableUnit> hosts = queryForIUs(result, hostId, hostVersionRange);

                    for (Iterator<IInstallableUnit> itor = hosts.iterator(); itor.hasNext();) {
                        IInstallableUnit host = itor.next();
                        String fragmentId = makeHostLocalizationFragmentId(bd.getSymbolicName());
                        fragment = queryForIU(result, fragmentId, PublisherHelper.fromOSGiVersion(bd.getVersion()));
                        if (fragment == null) {
                            String[] externalizedStrings = getExternalizedStrings(host);
                            fragment = createHostLocalizationFragment(bundleIU, bd, hostId, externalizedStrings);
                        }
                    }

                }

                result.addIU(bundleIU, IPublisherResult.ROOT);
                if (fragment != null)
                    result.addIU(fragment, IPublisherResult.NON_ROOT);

                InstallableUnitDescription[] others = processAdditionalInstallableUnitsAdvice(bundleIU, info);
                for (int iuIndex = 0; others != null && iuIndex < others.length; iuIndex++) {
                    result.addIU(MetadataFactory.createInstallableUnit(others[iuIndex]), IPublisherResult.ROOT);
                }
            }
        }
    }
    
    public static IInstallableUnit createBundleIU(BundleDescription bd, IArtifactKey key, IPublisherInfo info) {
        @SuppressWarnings("unchecked")
        Map<String, String> manifest = (Map<String, String>) bd.getUserObject();
        Map<Locale, Map<String, String>> manifestLocalizations = null;
        if (manifest != null && bd.getLocation() != null)
            manifestLocalizations = getManifestLocalizations(manifest, new File(bd.getLocation()));
        InstallableUnitDescription iu = new MetadataFactory.InstallableUnitDescription();
        iu.setSingleton(bd.isSingleton());
        iu.setId(bd.getSymbolicName());
        iu.setVersion(PublisherHelper.fromOSGiVersion(bd.getVersion()));
        iu.setFilter(bd.getPlatformFilter());
        iu.setUpdateDescriptor(MetadataFactory.createUpdateDescriptor(bd.getSymbolicName(), computeUpdateRange(bd.getVersion()), IUpdateDescriptor.NORMAL, null));
        iu.setArtifacts(new IArtifactKey[] {key});
        iu.setTouchpointType(PublisherHelper.TOUCHPOINT_OSGI);

        boolean isFragment = bd.getHost() != null;
        //      boolean requiresAFragment = isFragment ? false : requireAFragment(bd, manifest);

        //Process the required bundles
        BundleSpecification requiredBundles[] = bd.getRequiredBundles();
        ArrayList<IRequirement> reqsDeps = new ArrayList<IRequirement>();
        //      if (requiresAFragment)
        //          reqsDeps.add(MetadataFactory.createRequiredCapability(CAPABILITY_TYPE_OSGI_FRAGMENTS, bd.getSymbolicName(), VersionRange.emptyRange, null, false, false));
        if (isFragment)
            reqsDeps.add(MetadataFactory.createRequirement(CAPABILITY_NS_OSGI_BUNDLE, bd.getHost().getName(), PublisherHelper.fromOSGiVersionRange(bd.getHost().getVersionRange()), null, false, false));
        for (int j = 0; j < requiredBundles.length; j++)
            reqsDeps.add(MetadataFactory.createRequirement(CAPABILITY_NS_OSGI_BUNDLE, requiredBundles[j].getName(), PublisherHelper.fromOSGiVersionRange(requiredBundles[j].getVersionRange()), null, requiredBundles[j].isOptional(), false));

        // Process the import packages
        ImportPackageSpecification osgiImports[] = bd.getImportPackages();
        for (int i = 0; i < osgiImports.length; i++) {
            // TODO we need to sort out how we want to handle wild-carded dynamic imports - for now we ignore them
            ImportPackageSpecification importSpec = osgiImports[i];
            String importPackageName = importSpec.getName();
            if (importPackageName.indexOf('*') != -1)
                continue;
            VersionRange versionRange = PublisherHelper.fromOSGiVersionRange(importSpec.getVersionRange());
            //TODO this needs to be refined to take into account all the attribute handled by imports
            reqsDeps.add(MetadataFactory.createRequirement(PublisherHelper.CAPABILITY_NS_JAVA_PACKAGE, importPackageName, versionRange, null, isOptional(importSpec), false));
        }
        iu.setRequirements(reqsDeps.toArray(new IRequirement[reqsDeps.size()]));

        // Create set of provided capabilities
        ArrayList<IProvidedCapability> providedCapabilities = new ArrayList<IProvidedCapability>();
        providedCapabilities.add(PublisherHelper.createSelfCapability(bd.getSymbolicName(), PublisherHelper.fromOSGiVersion(bd.getVersion())));
        providedCapabilities.add(MetadataFactory.createProvidedCapability(CAPABILITY_NS_OSGI_BUNDLE, bd.getSymbolicName(), PublisherHelper.fromOSGiVersion(bd.getVersion())));

        // Process the export package
        ExportPackageDescription exports[] = bd.getExportPackages();
        for (int i = 0; i < exports.length; i++) {
            //TODO make sure that we support all the refinement on the exports
            providedCapabilities.add(MetadataFactory.createProvidedCapability(PublisherHelper.CAPABILITY_NS_JAVA_PACKAGE, exports[i].getName(), PublisherHelper.fromOSGiVersion(exports[i].getVersion())));
        }
        // Here we add a bundle capability to identify bundles
        if (manifest != null && manifest.containsKey("Eclipse-SourceBundle")) //$NON-NLS-1$
            providedCapabilities.add(SOURCE_BUNDLE_CAPABILITY);
        else
            providedCapabilities.add(BUNDLE_CAPABILITY);
        if (isFragment)
            providedCapabilities.add(MetadataFactory.createProvidedCapability(CAPABILITY_NS_OSGI_FRAGMENT, bd.getHost().getName(), PublisherHelper.fromOSGiVersion(bd.getVersion())));

        if (manifestLocalizations != null) {
            for (Entry<Locale, Map<String, String>> locEntry : manifestLocalizations.entrySet()) {
                Locale locale = locEntry.getKey();
                Map<String, String> translatedStrings = locEntry.getValue();
                for (Entry<String, String> entry : translatedStrings.entrySet()) {
                    iu.setProperty(locale.toString() + '.' + entry.getKey(), entry.getValue());
                }
                providedCapabilities.add(PublisherHelper.makeTranslationCapability(bd.getSymbolicName(), locale));
            }
        }
        iu.setCapabilities(providedCapabilities.toArray(new IProvidedCapability[providedCapabilities.size()]));
        processUpdateDescriptorAdvice(iu, info);
        processCapabilityAdvice(iu, info);

        // Set certain properties from the manifest header attributes as IU properties.
        // The values of these attributes may be localized (strings starting with '%')
        // with the translated values appearing in the localization IU fragments
        // associated with the bundle IU.
        if (manifest != null) {
            int i = 0;
            while (i < BUNDLE_IU_PROPERTY_MAP.length) {
                if (manifest.containsKey(BUNDLE_IU_PROPERTY_MAP[i])) {
                    String value = manifest.get(BUNDLE_IU_PROPERTY_MAP[i]);
                    if (value != null && value.length() > 0) {
                        iu.setProperty(BUNDLE_IU_PROPERTY_MAP[i + 1], value);
                    }
                }
                i += 2;
            }
        }

        // Define the immutable metadata for this IU. In this case immutable means
        // that this is something that will not impact the configuration.
        Map<String, String> touchpointData = new HashMap<String, String>();
        touchpointData.put("manifest", toManifestString(manifest)); //$NON-NLS-1$
        if (isDir(bd, info))
            touchpointData.put("zipped", "true"); //$NON-NLS-1$ //$NON-NLS-2$
        processTouchpointAdvice(iu, touchpointData, info);

        processInstallableUnitPropertiesAdvice(iu, info);
        return MetadataFactory.createInstallableUnit(iu);
    }
    
    private static String toManifestString(Map<String, String> p) {
        if (p == null)
            return null;
        StringBuffer result = new StringBuffer();     
        for (String key : p.keySet()) {
            String value = p.get(key);
            if (value != null)
                result.append(key).append(": ").append(value).append('\n'); //$NON-NLS-1$
        }
        return result.length() == 0 ? null : result.toString();
    }
    
    private static boolean isOptional(ImportPackageSpecification importedPackage) {
        if (importedPackage.getDirective(Constants.RESOLUTION_DIRECTIVE).equals(ImportPackageSpecification.RESOLUTION_DYNAMIC) || importedPackage.getDirective(Constants.RESOLUTION_DIRECTIVE).equals(ImportPackageSpecification.RESOLUTION_OPTIONAL))
            return true;
        return false;
    }
    
    static VersionRange computeUpdateRange(org.osgi.framework.Version base) {
        VersionRange updateRange = null;
        if (!base.equals(org.osgi.framework.Version.emptyVersion)) {
            updateRange = new VersionRange(Version.emptyVersion, true, PublisherHelper.fromOSGiVersion(base), false);
        } else {
            updateRange = VersionRange.emptyRange;
        }
        return updateRange;
    }
    
    private static boolean isDir(BundleDescription bundle, IPublisherInfo info) {
        Collection<IBundleShapeAdvice> advice = info.getAdvice(null, true, bundle.getSymbolicName(), PublisherHelper.fromOSGiVersion(bundle.getVersion()), IBundleShapeAdvice.class);
        // if the advice has a shape, use it
        if (advice != null && !advice.isEmpty()) {
            // we know there is some advice but if there is more than one, take the first.
            String shape = advice.iterator().next().getShape();
            if (shape != null)
                return shape.equals(IBundleShapeAdvice.DIR);
        }
        // otherwise go with whatever we figured out from the manifest or the shape on disk
        @SuppressWarnings("unchecked")
        Map<String, String> manifest = (Map<String, String>) bundle.getUserObject();
        String format = manifest.get(BUNDLE_SHAPE);
        return DIR.equals(format);
    }

    // Return a map from locale to property set for the manifest localizations
    // from the given bundle directory and given bundle localization path/name
    // manifest property value.
    private static Map<Locale, Map<String, String>> getManifestLocalizations(Map<String, String> manifest, File bundleLocation) {
        Map<Locale, Map<String, String>> localizations;
        Locale defaultLocale = null; // = Locale.ENGLISH; // TODO: get this from GeneratorInfo
        String[] bundleManifestValues = getManifestCachedValues(manifest);
        String bundleLocalization = bundleManifestValues[BUNDLE_LOCALIZATION_INDEX]; // Bundle localization is the last one in the list

        if ("jar".equalsIgnoreCase(new Path(bundleLocation.getName()).getFileExtension()) && //$NON-NLS-1$
                bundleLocation.isFile()) {
            localizations = LocalizationHelper.getJarPropertyLocalizations(bundleLocation, bundleLocalization, defaultLocale, bundleManifestValues);
            //localizations = getJarManifestLocalization(bundleLocation, bundleLocalization, defaultLocale, bundleManifestValues);
        } else {
            localizations = LocalizationHelper.getDirPropertyLocalizations(bundleLocation, bundleLocalization, defaultLocale, bundleManifestValues);
            // localizations = getDirManifestLocalization(bundleLocation, bundleLocalization, defaultLocale, bundleManifestValues);
        }

        return localizations;
    }
    
    private void createAdviceFileAdvice(BundleDescription bundleDescription, IPublisherInfo publisherInfo) {
        String location = bundleDescription.getLocation();
        if (location == null)
            return;

        AdviceFileAdvice advice = new AdviceFileAdvice(bundleDescription.getSymbolicName(), PublisherHelper.fromOSGiVersion(bundleDescription.getVersion()), new Path(location), AdviceFileAdvice.BUNDLE_ADVICE_FILE);
        if (advice.containsAdvice())
            publisherInfo.addAdvice(advice);

    }
    
    private boolean isFragment(BundleDescription bd) {
        return (bd.getHost() != null ? true : false);
    }
    
    /**
     * @param id
     * @return the id for the iu fragment containing localized properties
     *         for the fragment with the given id.
     */
    private static String makeHostLocalizationFragmentId(String id) {
        return id + ".translated_host_properties"; //$NON-NLS-1$
    }
    
    private IInstallableUnitFragment createHostLocalizationFragment(IInstallableUnit bundleIU, BundleDescription bd, String hostId, String[] hostBundleManifestValues) {
        Map<Locale, Map<String, String>> hostLocalizations = getHostLocalizations(new File(bd.getLocation()), hostBundleManifestValues);
        if (hostLocalizations == null || hostLocalizations.isEmpty())
            return null;
        return createLocalizationFragmentOfHost(bd, hostId, hostBundleManifestValues, hostLocalizations);
    }
    
    /*
     * @param hostId
     * @param bd
     * @param locale
     * @param localizedStrings
     * @return installableUnitFragment
     */
    private static IInstallableUnitFragment createLocalizationFragmentOfHost(BundleDescription bd, String hostId, String[] hostManifestValues, Map<Locale, Map<String, String>> hostLocalizations) {
        InstallableUnitFragmentDescription fragment = new MetadataFactory.InstallableUnitFragmentDescription();
        String fragmentId = makeHostLocalizationFragmentId(bd.getSymbolicName());
        fragment.setId(fragmentId);
        fragment.setVersion(PublisherHelper.fromOSGiVersion(bd.getVersion())); // TODO: is this a meaningful version?

        HostSpecification hostSpec = bd.getHost();
        IRequirement[] hostReqs = new IRequirement[] {MetadataFactory.createRequirement(IInstallableUnit.NAMESPACE_IU_ID, hostSpec.getName(), PublisherHelper.fromOSGiVersionRange(hostSpec.getVersionRange()), null, false, false, false)};
        fragment.setHost(hostReqs);

        fragment.setSingleton(true);
        fragment.setProperty(InstallableUnitDescription.PROP_TYPE_FRAGMENT, Boolean.TRUE.toString());

        // Create a provided capability for each locale and add the translated properties.
        ArrayList<IProvidedCapability> providedCapabilities = new ArrayList<IProvidedCapability>(hostLocalizations.keySet().size());
        providedCapabilities.add(PublisherHelper.createSelfCapability(fragmentId, fragment.getVersion()));
        for (Entry<Locale, Map<String, String>> localeEntry : hostLocalizations.entrySet()) {
            Locale locale = localeEntry.getKey();
            Map<String, String> translatedStrings = localeEntry.getValue();
            for (Entry<String, String> entry : translatedStrings.entrySet()) {
                fragment.setProperty(locale.toString() + '.' + entry.getKey(), entry.getValue());
            }
            providedCapabilities.add(PublisherHelper.makeTranslationCapability(hostId, locale));
        }
        fragment.setCapabilities(providedCapabilities.toArray(new IProvidedCapability[providedCapabilities.size()]));

        return MetadataFactory.createInstallableUnitFragment(fragment);
    }
    
}
