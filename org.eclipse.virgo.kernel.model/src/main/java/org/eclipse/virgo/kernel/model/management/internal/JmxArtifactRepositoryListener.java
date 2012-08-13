/*******************************************************************************
 * Copyright (c) 2008, 2010 VMware Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   VMware Inc. - initial contribution
 *******************************************************************************/

package org.eclipse.virgo.kernel.model.management.internal;

import java.lang.management.ManagementFactory;
import java.util.Set;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.eclipse.virgo.kernel.model.Artifact;
import org.eclipse.virgo.kernel.model.BundleArtifact;
import org.eclipse.virgo.kernel.model.CompositeArtifact;
import org.eclipse.virgo.kernel.model.internal.ArtifactRepositoryListener;
import org.eclipse.virgo.kernel.model.management.RuntimeArtifactModelObjectNameCreator;
import org.eclipse.virgo.nano.serviceability.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * An implementation of {@link ArtifactRepositoryListener} that notices creation and deletion of {@link Artifact}s and
 * adds and removes respectively MBeans from the JMX MBeanServer
 * <p />
 * 
 * <strong>Concurrent Semantics</strong><br />
 * 
 * Threadsafe
 * 
 */
public class JmxArtifactRepositoryListener implements ArtifactRepositoryListener {

    private static final String USER_REGION_NAME = "org.eclipse.virgo.region.user";

    private static final String GLOBAL_REGION_NAME = "global";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final MBeanServer server = ManagementFactory.getPlatformMBeanServer();

    private final RuntimeArtifactModelObjectNameCreator artifactObjectNameCreator;

    public JmxArtifactRepositoryListener(@NonNull RuntimeArtifactModelObjectNameCreator artifactObjectNameCreator) {
        this.artifactObjectNameCreator = artifactObjectNameCreator;
    }

    /**
     * {@inheritDoc}
     */
    public void added(Artifact artifact) {
        String regionName = artifact.getRegion().getName();
        if (USER_REGION_NAME.equals(regionName) || GLOBAL_REGION_NAME.equals(regionName) ) {
            publish(artifact, false);
        }
        publish(artifact, true);
    }

    /**
     * {@inheritDoc}
     */
    public void removed(Artifact artifact) {
        String regionName = artifact.getRegion().getName();
        if (USER_REGION_NAME.equals(regionName) || GLOBAL_REGION_NAME.equals(regionName) ) {
            retract(artifact, false);
        } 
        retract(artifact, true);
    }

    public void destroy() {
        Set<ObjectName> objectNames = this.server.queryNames(this.artifactObjectNameCreator.createAllArtifactsQuery(), null);
        for (ObjectName objectName : objectNames) {
            try {
                this.server.unregisterMBean(objectName);
            } catch (MBeanRegistrationException e) {
                // Swallow exception to allow others to proceed
            } catch (InstanceNotFoundException e) {
                // Swallow exception to allow others to proceed
            }
        }
    }
    
    private void publish(Artifact artifact, boolean newModel){
        ObjectName objectName = getModelObjectName(artifact, newModel);

        try {
            if (artifact instanceof CompositeArtifact) {
                this.server.registerMBean(new DelegatingManageableCompositeArtifact(this.artifactObjectNameCreator, (CompositeArtifact) artifact, newModel), objectName);
            } else if (artifact instanceof BundleArtifact) {
                this.server.registerMBean(new DelegatingManageableBundleArtifact(this.artifactObjectNameCreator, (BundleArtifact) artifact, newModel), objectName);
            } else {
                this.server.registerMBean(new DelegatingManageableArtifact(this.artifactObjectNameCreator, artifact, newModel), objectName);
            }
        } catch (InstanceAlreadyExistsException e) {
            logger.error(String.format("Unable to register '%s'", objectName.toString()), e);
        } catch (MBeanRegistrationException e) {
            logger.error(String.format("Unable to register '%s'", objectName.toString()), e);
        } catch (NotCompliantMBeanException e) {
            logger.error(String.format("Unable to register '%s'", objectName.toString()), e);
        }
    }
    
    
    private void retract(Artifact artifact, boolean newModel){
        ObjectName objectName = getModelObjectName(artifact, newModel);
        
        try {
            this.server.unregisterMBean(objectName);
        } catch (MBeanRegistrationException e) {
            logger.error(String.format("Unable to unregister '%s'", objectName.toString()), e);
        } catch (InstanceNotFoundException e) {
            logger.error(String.format("Unable to unregister '%s'", objectName.toString()), e);
        }
    }
    
    private ObjectName getModelObjectName(Artifact artifact, boolean newModel){
        ObjectName objectName;
        if(newModel){
            objectName = this.artifactObjectNameCreator.createArtifactModel(artifact);
        } else {
            objectName = this.artifactObjectNameCreator.createModel(artifact);
        }
        return objectName;
    }
}
