package org.eclipse.virgo.kernel.deployer.p2;

import java.net.URISyntaxException;

import org.eclipse.equinox.p2.core.ProvisionException;

/**
 * Common interface for all p2 publishers used within Virgo.
 * 
 * Implementations of this interface are required to be thread safe.
 */
public interface VirgoPublisher {

    /**
     * Publishes a product and its dependencies in a p2 repository 
     * @param sourceLocation - the location of the folder where the product file is
     * @param targetLocation - the location of the published repository
     * @throws ProvisionException - when an error during publishing occurs
     * @throws URISyntaxException - when the locations have wrong syntax
     */
    public void publishProduct(String sourceLocation, String targetLocation) throws ProvisionException, URISyntaxException;
}
