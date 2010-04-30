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


	
	private LauncherConstants() {} // Prevents instantiation
}