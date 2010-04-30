package bndtools.launcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServiceFinder<T> {

	private final Logger log = Logger.getLogger("bndtools.launcher");

	private final Class<T> clazz;
	private final ClassLoader loader;

	private ServiceFinder(Class<T> clazz, ClassLoader loader) {
		this.clazz = clazz;
		this.loader = loader;
	}
	
	public static <T> ServiceFinder<T> create(Class<T> clazz, ClassLoader loader) {
		return new ServiceFinder<T>(clazz, loader);
	}

	public T loadOneInstance() {
		String implementation = null;
		try {
			Collection<String> implementations = getMetaInfServiceNames();
			implementation = (String) implementations.iterator().next();
			if (implementations.size() != 1) {
				log.log(Level.WARNING, "Found multiple framework implementations! Selected \"{0}\".", implementation);
			}
			log.log(Level.FINE, "Discovered OSGi FrameworkFactory implementation class: {0}.", implementation);

			Class<?> clazz = Class.forName(implementation, true, loader);
			
			@SuppressWarnings("unchecked")
			T instance = (T) clazz.newInstance();
			return instance;
		} catch (IOException e) {
			System.err.println("An error occurred while searching for a Framework Factory implementation.");
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			log.log(Level.SEVERE, "Framework Factory implementation class ({0}) does not exist.", implementation);
		} catch (InstantiationException e) {
			log.log(Level.SEVERE, MessageFormat.format("An error occurred instantiating the Framework Factory ({0}).", implementation), e);
		} catch (IllegalAccessException e) {
			log.log(Level.SEVERE, MessageFormat.format("An error occurred instantiating the Framework Factory ({0}).", implementation), e);
		}
		return null;
	}

	private Collection<String> getMetaInfServiceNames() throws IOException {
		Enumeration<URL> e = loader.getResources("META-INF/services/" + clazz.getName());
		List<String> names = new ArrayList<String>();

		while (e.hasMoreElements()) {
			URL url = (URL) e.nextElement();
			BufferedReader rdr = null;
			try {
				rdr = new BufferedReader(new InputStreamReader(url.openStream()));
				String line;
				while ((line = rdr.readLine()) != null) {
					line = line.trim();
					if (!line.startsWith("#") && line.length() > 0) {
						names.add(line);
					}
				}
			} finally {
				if(rdr != null)
					rdr.close();
			}
		}
		return names;
	}
}