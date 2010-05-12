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
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

public class Main implements Runnable {

	private static final String DEFAULT_PROPS_FILE = "launch.properties";

	private Logger logger;

	public static void main(String[] args) {
		try {
			Main main = new Main();
			main.init(args);
			main.run();
		} catch (Throwable t) {
			t.printStackTrace();
		} finally {
			System.exit(0);
		}
	}

	File propsFile;
	boolean enableDebug = false;

	public void init(String[] args) throws IllegalArgumentException {
		String fileName = DEFAULT_PROPS_FILE;
		if(args != null) {
			for (String arg : args) {
				if("--debug".equalsIgnoreCase(arg))
					enableDebug = true;
				else if("--help".equalsIgnoreCase(arg)) {
					printHelp();
					System.exit(0);
				}
				else
					fileName = arg;
			}
		}
		propsFile = new File(fileName);
	}

	public void run() {
		// LOAD PROPERTIES
		Properties props = new Properties();
		try {
			if(propsFile.isFile()) {
				debug(MessageFormat.format("Loading launcher properties from {0}.", propsFile.getAbsoluteFile()));
				props.load(new FileInputStream(propsFile));
			} else {
				debug(MessageFormat.format("Launcher properties file {0} not found. Using default properties.", propsFile.getAbsoluteFile()));
			}
		} catch (IOException e) {
			System.err.println("Error loading launch properties.");
			e.printStackTrace();
			return;
		}

		// LOGGING
		Handler logHandler = initialiseLogging(props);

		// STORAGE
		File storageDir = initialiseStorage(props);

		try {
			// LOAD RUNTIME PROPERTIES
			Properties config = new Properties();
			config.put(Constants.FRAMEWORK_STORAGE, storageDir.getAbsolutePath());
			copyFrameworkConfig(props, config);

			// CREATE FRAMEWORK AND SYNC BUNDLES
			Framework framework = createAndRunFramework(config);
			if(framework == null) return;
			BundleContext fwContext = framework.getBundleContext();

			// CREATE INSTALLER
			Thread installerThread = createInstaller(fwContext, props);
			
			// MAIN THREAD EXECUTOR
			if(framework.getState() == Bundle.ACTIVE) // Check the framework hasn't already shutdown
			    createAndRunMainThreadExecutor(fwContext, "main");

			// SHUTDOWN
			try {
				logger.info("Waiting for the framework to stop.");
				framework.waitForStop(0);
				if(installerThread != null)
					installerThread.interrupt();
				logger.info("Framework stopped.");
			} catch (InterruptedException e) {
				// Ignore
			}
			logger.info("Main thread finishing.");
		} finally {
			if(logHandler != null) {
				logHandler.close();
			}
		}
	}

	Handler initialiseLogging(Properties props) {
		// Initialise logging
		String logLevelStr = props.getProperty(LauncherConstants.PROP_LOG_LEVEL, LauncherConstants.DEFAULT_LOG_LEVEL);
		if(enableDebug)
			System.err.println(MessageFormat.format("Setting main log level to {0}.", logLevelStr));

		Logger rootLogger = Logger.getLogger("");
		Handler[] handlers = rootLogger.getHandlers();
		for (Handler handler : handlers) {
			rootLogger.removeHandler(handler);
		}

		Handler handler = null;
		String logOutput = props.getProperty(LauncherConstants.PROP_LOG_OUTPUT, LauncherConstants.DEFAULT_LOG_OUTPUT);
		if(logOutput.startsWith("file:")) {
			logOutput = logOutput.substring("file:".length());
			try {
				FileOutputStream stream = new FileOutputStream(logOutput, true);
				handler = new StreamHandler(stream, new SimpleFormatter());
				if(enableDebug)
					System.err.println(MessageFormat.format("Logging to file {0}.", logOutput));
			} catch (FileNotFoundException e) {
				System.err.println(MessageFormat.format("Could not write to specified log file {0}. Falling back to the console.", logOutput));
				handler = new ConsoleHandler();
			}
		} else {
			handler = new ConsoleHandler();
			if(enableDebug)
				System.err.println("Logging to the console.");
		}
		handler.setLevel(Level.ALL); // Ensure the handler does not filter out any messages from the loggers
		rootLogger.addHandler(handler);
		rootLogger.setLevel(Level.parse(logLevelStr));
		logger = Logger.getLogger("bndtools.launcher");

		return handler;
	}

	File initialiseStorage(Properties props) {
		// Check the storage dir path
		String storagePathStr = props.getProperty(LauncherConstants.PROP_STORAGE_DIR, LauncherConstants.DEFAULT_STORAGE_DIR);

		File storagePath = new File(storagePathStr);
		if(!storagePath.isAbsolute()) {
			File workingDir = new File(System.getProperty("user.dir"));
			storagePath = new File(workingDir, storagePathStr);
		}
		logger.log(Level.FINE, "Using storage dir: {0}.", storagePath.getAbsolutePath());

		// Clean it if requested
		boolean clean = "true".equalsIgnoreCase(props.getProperty(LauncherConstants.PROP_STORAGE_CLEAN));
		if(clean) {
			try {
				logger.log(Level.INFO, "Cleaning storage directory {0}.", storagePath.getAbsolutePath());
				FileUtil.deleteDirectory(storagePath);
			} catch (IOException e) {
				logger.log(Level.SEVERE, "Error while cleaning framework storage directory {0}.", storagePath.getAbsolutePath());
			}
		}
		return storagePath;
	}

	void copyFrameworkConfig(Properties props, Properties frameworkConfig) {
		for(Enumeration<?> names = props.propertyNames(); names.hasMoreElements(); ) {
			String name = (String) names.nextElement();
			String value = props.getProperty(name);
			if(value != null)
				frameworkConfig.setProperty(name, value);
		}
	}

	Framework createAndRunFramework(Properties config) {
		ServiceFinder<FrameworkFactory> finder = ServiceFinder.create(FrameworkFactory.class, Main.class.getClassLoader());
		FrameworkFactory fwkFactory = finder.loadOneInstance();
		if (fwkFactory == null) {
			logger.severe("No FrameworkFactory service providers available.");
			return null;
		}

		Framework framework = fwkFactory.newFramework(config);
		logger.info("Created framework");
		try {
			framework.start();
			logger.info("Started framework");
		} catch (BundleException e) {
			logger.log(Level.SEVERE, "Error starting framework.", e);
			return null;
		}

		return framework;
	}

	Thread createInstaller(BundleContext framework, Properties props) {
		boolean dynamic = "true".equalsIgnoreCase(props.getProperty(LauncherConstants.PROP_DYNAMIC_BUNDLES, LauncherConstants.DEFAULT_DYNAMIC_BUNDLES));
		boolean killOnError = "true".equalsIgnoreCase(props.getProperty(LauncherConstants.PROP_SHUTDOWN_ON_BUNDLE_ERROR, LauncherConstants.DEFAULT_SHUTDOWN_ON_BUNDLE_ERROR));

		// Start the framework and synchronize the bundles; either once or continuously
		Thread installerThread = null;
		BundleInstaller installer = new BundleInstaller(propsFile, framework, killOnError);

		if(dynamic) {
			installerThread = new Thread(installer);
			installerThread.start();
		} else {
			installer.synchronizeBundles();
		}

		return installerThread;
	}

	/**
	 * This method creates and registers an {@link Executor} service, then
	 * performs work received by that executor on the calling thread, continuing
	 * until the system bundle is shutdown. This method <b>will not return</b>
	 * until the framework is shutting down.
	 *
	 * @param framework
	 *            The OSGi framework
	 * @param threadName
	 *            The name of the calling thread; this will be used to set the
	 *            {@code thread} property of the Executor service.
	 */
	public void createAndRunMainThreadExecutor(BundleContext framework, String threadName) {
		// Create work queue
		final BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(1);

		// Register executor as a service
		Properties mainThreadExecutorProps = new Properties();
		mainThreadExecutorProps.put("thread", threadName);
		mainThreadExecutorProps.put(Constants.SERVICE_RANKING, Integer.valueOf(-1000));
		Executor mainThreadExecutor = new Executor() {
			public void execute(Runnable command) {
				logger.info("Main-thread executor enqueuing a new task");
				// add() will throw an exception if the queue is full, which is what we want
				workQueue.add(command);
			}
		};
		framework.registerService(Executor.class.getName(), mainThreadExecutor, mainThreadExecutorProps);

		// Create a bundle listener that will pull us out of the queue polling loop
		// when the system bundle starts to shutdown
		final AtomicBoolean shutdown = new AtomicBoolean(false);
		final Thread mainThread = Thread.currentThread();
		framework.addBundleListener(new SynchronousBundleListener() {
			public void bundleChanged(BundleEvent event) {
				if(event.getBundle().getBundleId() == 0 && event.getType() == BundleEvent.STOPPING) {
					shutdown.set(true);
					mainThread.interrupt();
				}
			}
		});

		// Enter a loop to poll on the work queue
		while(!shutdown.get()) {
			try {
				logger.fine("Main thread polling for work.");
				Runnable work = workQueue.take();
				if(work != null) {
					logger.fine("Main thread received a work task, executing.");
					work.run();
				}
			} catch (InterruptedException e) {
				logger.fine("Main thread work queue polling loop was interrupted.");
			}
		}
		// Clear the interrupted state if it was uncaught during the above loop
		Thread.interrupted();
	}

	void debug(String message) {
		if(enableDebug) System.err.println(message);
	}

	void printHelp() {
		System.out.println("java -cp org.eclipse.osgi-3.5.2.jar:bndtools.launcher.jar bndtools.launcher.Main [--debug] [launch.properties]");
	}
}
