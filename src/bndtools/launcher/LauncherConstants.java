/*******************************************************************************
 * Copyright (c) 2010 Neil Bartlett.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Neil Bartlett - initial API and implementation
 ******************************************************************************/
package bndtools.launcher;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

import java.util.logging.Level;

public final class LauncherConstants {

	// NAMESPACE
	public static final String NAMESPACE = "bndtools.launcher";

	// LOGGING
	public static final String PROP_LOG_LEVEL = NAMESPACE + ".logLevel";
	public static final String PROP_LOG_OUTPUT = NAMESPACE + ".logOutput";

	public static final String DEFAULT_LOG_LEVEL = Level.WARNING.toString();
	public static final String DEFAULT_LOG_OUTPUT = "console";

	// STORAGE
	public static final String PROP_STORAGE_DIR = NAMESPACE + ".storageDir";
	public static final String PROP_STORAGE_CLEAN = NAMESPACE + ".clean";

	public static final String DEFAULT_STORAGE_DIR = "runtimefw";

	// LAUNCH
	public static final String PROP_RUN_BUNDLES = NAMESPACE + ".runBundles";
	public static final String PROP_DYNAMIC_BUNDLES = NAMESPACE + ".dynamicBundles";
	public static final String PROP_SHUTDOWN_ON_BUNDLE_ERROR = NAMESPACE + ".shutdownOnError";

	public static final String DEFAULT_DYNAMIC_BUNDLES = TRUE.toString();
	public static final String DEFAULT_SHUTDOWN_ON_BUNDLE_ERROR = FALSE.toString();

	// BUNDLE STARTING
	public static final String PROP_DEFAULT_START_OPTIONS = NAMESPACE + ".defaultStart";
	public static final String VALUE_NOSTART = "none";
    public static final String VALUE_START = "start";
    public static final String VALUE_START_TRANSIENT = "transient";
    public static final String VALUE_START_ACTIVATION_POLICY = "activationPolicy";
    public static final String VALUE_START_TRANSIENT_ACTIVATION_POLICY = "transient+activationPolicy";


	private LauncherConstants() {} // Prevents instantiation
}
