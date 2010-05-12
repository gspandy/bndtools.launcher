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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.packageadmin.PackageAdmin;

class BundleInstaller implements Runnable {

	private static final String FILE_URI_PREFIX = "file:";

	private static final int NO_START = -1;
    private static final int START = 0;

	private final Logger log = Logger.getLogger("bndtools.launcher");

	private final File propsFile;
	private final BundleContext framework;
	private final boolean shutdownOnError;

	private final Map<String, Bundle> locationsMap = new HashMap<String, Bundle>();
	private final Set<Long> startAttempted = new HashSet<Long>();
	private long propsLastUpdated = 0L;


	BundleInstaller(File propsFile, BundleContext framework, boolean shutdownOnError) {
		this.propsFile = propsFile;
		this.framework = framework;
		this.shutdownOnError = shutdownOnError;

		init();
	}

	private void init() {
		// Get initial bundles
		Bundle[] allBundles = framework.getBundles();
		for (Bundle bundle : allBundles) {
			// Skip the system bundle
			String location = bundle.getLocation();
			if(bundle.getBundleId() != 0 && location != null && location.startsWith(FILE_URI_PREFIX)) {
				locationsMap.put(location, bundle);
			}
		}
		log.log(Level.INFO, "BundleInstaller detected {0} pre-installed bundles with \"file:\" locations.", locationsMap.size());
	}

	public void run() {
		log.info("Bundle installer thread starting...");

		// Enter the main loop
		try {
			while(!Thread.interrupted()) {
				synchronizeBundles();

				// Sleep until next cycle
				Thread.sleep(2000);
			}
		} catch (InterruptedException e) {
			// Allow thread to end
		}
		log.info("Bundle installer thread terminating.");
	}

	void synchronizeBundles() {
		long lastModified = propsFile.lastModified();

		Map<String, Integer> toInstall = new HashMap<String, Integer>();
		List<Bundle> toRemove = new LinkedList<Bundle>();
		int defaultStart = START;

		// Reread bundle list if it has changed;
		if(lastModified > propsLastUpdated) {
		    log.fine("Launch properties file has changed");
		    propsLastUpdated = lastModified;
			defaultStart = loadBundles(toInstall);

			// Find bundles to uninstall
			for (Iterator<Entry<String, Bundle>> iterator = locationsMap.entrySet().iterator(); iterator.hasNext(); ) {
				Entry<String, Bundle> entry = iterator.next();
				if(!toInstall.containsKey(entry.getKey())) {
					iterator.remove();
					toRemove.add(entry.getValue());
				}
			}

			// Find bundles to install
			for (Iterator<Entry<String, Integer>> iterator = toInstall.entrySet().iterator(); iterator.hasNext(); ) {
				Entry<String, Integer> entry = iterator.next();
				if(locationsMap.containsKey(entry.getKey())) {
					iterator.remove(); // Remaining paths are the new ones
				}
			}
		}

		// Perform the changes
		List<BundleOperationException> errors = new LinkedList<BundleOperationException>();
		performAllChanges(toInstall, toRemove, defaultStart, errors);

		// Report errors
		if(!errors.isEmpty()) {
			log.log(Level.SEVERE, "{0} ERROR(S) OCCURRED", errors.size());
			int i = 0;
			for (BundleOperationException error : errors) {
				String message = MessageFormat.format("{0} BUNDLE {1}: {2}", i++, error.getBundleLocation(), error.getMessage());
				log.log(Level.SEVERE, message, error.getCause());
			}

			if(shutdownOnError) {
				log.severe("SHUTTING DOWN due to errors.");
				try {
					framework.getBundle(0).stop();
				} catch (BundleException e) {
					log.log(Level.SEVERE, "Failed to shutdown OSGi Framework.", e);
				}
			}
		}
	}

	int loadBundles(Map<String, Integer> toInstall) {
	    int defaultStart = START;
		try {
			Properties props = new Properties();
			props.load(new FileInputStream(propsFile));

			// Read the default start option
            defaultStart = readStartOptionString(props.getProperty(LauncherConstants.PROP_DEFAULT_START_OPTIONS), START);

			// Add the run bundles
			String bundlesStr = props.getProperty(LauncherConstants.PROP_RUN_BUNDLES);
			if(bundlesStr != null) {
				String[] bundles = bundlesStr.split(",");
				for (String bundleSpec : bundles) {
					String trimmedSpec = bundleSpec.trim();
					if(trimmedSpec.length() > 0) {
					    String[] options = trimmedSpec.split(";");
					    String bundlePath = options[0];
						String location = FILE_URI_PREFIX + bundlePath;

						int startOption = defaultStart;
						for(int i = 1; i < options.length; i++) {
						    if(options[i].startsWith("start=")) {
						        startOption = readStartOptionString(options[i].substring("start=".length()), defaultStart);
						    }
						}

						toInstall.put(location, startOption);
					}
				}
			}
		} catch (IOException e) {
			log.log(Level.WARNING, "Error reading launcher properties file {0}.", propsFile.getAbsolutePath());
		}
		return defaultStart;
	}

	int readStartOptionString(String option, int defaultValue) {
	    if(LauncherConstants.VALUE_NOSTART.equals(option))
	        return NO_START;
        if(LauncherConstants.VALUE_START.equals(option))
            return START;
        if(LauncherConstants.VALUE_START_TRANSIENT.equals(option))
            return Bundle.START_TRANSIENT;
        if(LauncherConstants.VALUE_START_ACTIVATION_POLICY.equals(option))
            return Bundle.START_ACTIVATION_POLICY;
        if(LauncherConstants.VALUE_START_TRANSIENT_ACTIVATION_POLICY.equals(option))
            return Bundle.START_TRANSIENT & Bundle.START_ACTIVATION_POLICY;

        return defaultValue;
	}

	private static class BundleOperationException extends Exception {
		private static final long serialVersionUID = 1L;
		final String bundleLocation;

		public BundleOperationException(String bundleLocation, String message, Throwable cause) {
			super(message, cause);
			this.bundleLocation = bundleLocation;
		}
		public String getBundleLocation() {
			return bundleLocation;
		}
	}

	void performAllChanges(Map<String, Integer> toInstall, Collection<? extends Bundle> toRemove, int defaultStart, Collection<? super BundleOperationException> errors) {
		assert errors != null : "errors must not be null";

		// Uninstall
		if(toRemove != null)
			performUninstalls(toRemove, errors);

		// Updates
		performUpdates(errors);

		// Install
		if(toInstall != null) {
			performInstalls(toInstall.keySet(), errors);
		}

		// Resolve bundles
		ServiceReference pkgAdmRef = framework.getServiceReference(PackageAdmin.class.getName());
		if(pkgAdmRef != null) {
			PackageAdmin pkgAdm = (PackageAdmin) framework.getService(pkgAdmRef);
			if(pkgAdm != null) {
				try {
					pkgAdm.resolveBundles(null);
				} finally {
					framework.ungetService(pkgAdmRef);
				}
			}
		}

		// Start
		performStarts(defaultStart, toInstall, errors);
	}

	void performUninstalls(Collection<? extends Bundle> toRemove, Collection<? super BundleOperationException> errors) {
		assert toRemove != null : "toRemove must not be null";
		assert errors != null : "errors must not be null";
		for (Bundle bundle : toRemove) {
			try {
				if(bundle.getState() != Bundle.UNINSTALLED) {
					log.log(Level.FINE, "Uninstalling bundle {0}", bundle.getLocation());
					startAttempted.remove(bundle.getBundleId());
					bundle.uninstall();
				} else {
					errors.add(new BundleOperationException(bundle.getLocation(), "Bundle is already uninstalled", null));
				}
			} catch (BundleException e) {
				errors.add(new BundleOperationException(bundle.getLocation(), "Error uninstalling bundle", e));
			}
		}
	}

	void performUpdates(Collection<? super BundleOperationException> errors) {
		for(Iterator<Entry<String, Bundle>> iterator = locationsMap.entrySet().iterator(); iterator.hasNext(); ) {
			Entry<String, Bundle> entry = iterator.next();

			String location = entry.getKey();
			Bundle bundle = entry.getValue();

			File bundleFile;
			if(location.startsWith(FILE_URI_PREFIX)) {
				bundleFile = new File(location.substring(FILE_URI_PREFIX.length()));
			} else {
				bundleFile = new File(location);
			}

			if(!bundleFile.isFile() || bundleFile.isDirectory()) {
				// Bundle file has been deleted => uninstall it
				try {
					log.log(Level.FINE, "Uninstalling bundle {0}.", bundle.getLocation());
					startAttempted.remove(bundle.getBundleId());
					bundle.uninstall();
				} catch (BundleException e) {
					errors.add(new BundleOperationException(bundle.getLocation(), "Error uninstalling bundle.", e));
				}
			} else {
				if(bundle.getLastModified() < bundleFile.lastModified()) {
					try {
						log.log(Level.FINE, "Updating bundle {0}.", bundle.getLocation());
						startAttempted.remove(bundle.getBundleId());
						bundle.update(new FileInputStream(bundleFile));
					} catch (FileNotFoundException e) {
						errors.add(new BundleOperationException(bundle.getLocation(), "Error updating bundle, its bundle file may have been deleted.", e));
					} catch (BundleException e) {
						errors.add(new BundleOperationException(bundle.getLocation(), "Error updating bundle.", e));
					}
				}
			}
		}
	}

	Collection<Bundle> performInstalls(Collection<? extends String> toInstall, Collection<? super BundleOperationException> errors) {
		assert toInstall != null : "toInstall must not be null";
		assert errors != null : "errors must not be null";

		Collection<Bundle> installed = new LinkedList<Bundle>();
		for (String location : toInstall) {
			Bundle bundle = null;

			// Install it
			try {
				log.log(Level.FINE, "Installing bundle {0}", location);
				bundle = framework.installBundle(location);
				startAttempted.remove(bundle.getBundleId());

				locationsMap.put(location, bundle);
				installed.add(bundle);
			} catch (BundleException e) {
				errors.add(new BundleOperationException(location, "Error installing bundle.", e));
			}
		}

		return installed;
	}

	void performStarts(int defaultStart, Map<String, Integer> startOptions, Collection<? super BundleOperationException> errors) {
		assert errors != null : "errors must not be null";
		for (Bundle bundle : locationsMap.values()) {
			// Don't keep trying to start a bundle that have already attempted to start
			if(startAttempted.contains(bundle.getBundleId()))
				continue;

			// Skip fragments
			if(bundle.getHeaders().get(org.osgi.framework.Constants.FRAGMENT_HOST) != null)
				continue;

			try {
			    Integer startOptionObj = startOptions.get(bundle.getLocation());
			    int startOption = startOptionObj != null
			        ? startOptionObj.intValue()
			        : defaultStart;

			    if(startOption != -1) {
			        log.log(Level.FINE, "Starting bundle {0}", bundle.getLocation());
			        startAttempted.add(bundle.getBundleId());
			        bundle.start(startOption);
			    }
			} catch (BundleException e) {
				errors.add(new BundleOperationException(bundle.getLocation(), "Error starting bundle.", e));
			} catch (IllegalStateException e) {
			    errors.add(new BundleOperationException(bundle.getLocation(), "Error starting bundle", e));
			}
		}
	}
}
