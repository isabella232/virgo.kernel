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
package org.eclipse.virgo.kernel.userregion.internal.management;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.virgo.kernel.osgi.quasi.QuasiBundle;
import org.eclipse.virgo.kernel.osgi.quasi.QuasiExportPackage;
import org.eclipse.virgo.kernel.osgi.quasi.QuasiImportPackage;
import org.eclipse.virgo.kernel.osgi.quasi.QuasiRequiredBundle;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

/**
 * 
 *
 */
public class StubQuasiBundle implements QuasiBundle {

	private final Version version;
	private final String name;

	public StubQuasiBundle(String name, Version version) {
		this.name = name;
		this.version = version;
	}
	
	@Override
	public String getSymbolicName() {
		return this.name;
	}

	@Override
	public Version getVersion() {
		return this.version;
	}

	@Override
	public boolean isResolved() {
		return false;
	}

	@Override
	public void uninstall() {
	}

	@Override
	public Bundle getBundle() {
		return null;
	}

	@Override
	public long getBundleId() {
		return 0;
	}

	@Override
	public List<QuasiBundle> getFragments() {
		return new ArrayList<QuasiBundle>();
	}

	@Override
	public List<QuasiBundle> getHosts() {
		return new ArrayList<QuasiBundle>();
	}

	@Override
	public List<QuasiExportPackage> getExportPackages() {
		return new ArrayList<QuasiExportPackage>();
	}

	@Override
	public List<QuasiImportPackage> getImportPackages() {
		return new ArrayList<QuasiImportPackage>();
	}

	@Override
	public List<QuasiRequiredBundle> getRequiredBundles() {
		return new ArrayList<QuasiRequiredBundle>();
	}

	@Override
	public List<QuasiBundle> getDependents() {
		return new ArrayList<QuasiBundle>();
	}

	@Override
	public File getBundleFile() {
		return null;
	}

}
