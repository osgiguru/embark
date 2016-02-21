package guru.osgi.embark;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.Constants;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.wiring.BundleRevision;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

public class Main {
	
	private static final String DEFAULT_STORAGE = "embark-cache";
	
	private static final String[] BUNDLE_LOCATIONS = {
//			"scr.jar",
			"gogo-runtime.jar",
			"gogo-command.jar",
			"guru.osgi.embark.shell.jar"
	};
	
	private static final String[] ACTIVATORS = {
			"org.apache.felix.scr.impl.Activator"
	};
	
	public static void main(String[] args) throws Exception {
		ClassLoader loader = Main.class.getClassLoader();

		// Parse command line options
		OptionParser optParser = new OptionParser();
		OptionSpec<String> storageOpt = optParser.accepts("storage", "Set the storage folder").withRequiredArg().ofType(String.class).defaultsTo(DEFAULT_STORAGE);
		OptionSpec<Void> cleanOpt = optParser.accepts("clean", "Clean the storage folder before starting");
		OptionSet options = optParser.parse(args);
		
		// Optionally clean OSGi storage folder
		String storageDir = options.valueOf(storageOpt);
		boolean cleanStorage = options.has(cleanOpt);
		if (cleanStorage)
			recurseDelete(new File(storageDir));
		
		// Find an OSGi Framework on the classpath
		Iterator<FrameworkFactory> fwkFactIter = ServiceLoader.load(FrameworkFactory.class).iterator();
		if (!fwkFactIter.hasNext())
			throw new Exception("No OSGi Framework found on the classpath.");
		FrameworkFactory fwkFact = fwkFactIter.next();
		if (fwkFactIter.hasNext())
			throw new Exception("Multiple OSGi Frameworks found on the classpath.");
		
		// Load framework config
		Properties tmpProps = new Properties();
		tmpProps.load(loader.getResourceAsStream("framework.properties"));
		String systemPackages = tmpProps.getProperty("system-packages", "");
		String systemCaps = tmpProps.getProperty("system-capabilities", "");
		
		// Configure and create the Framework
		Map<String, String> fwkProps = new HashMap<>();
		fwkProps.put(Constants.FRAMEWORK_STORAGE, storageDir);
		fwkProps.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA, systemPackages);
		fwkProps.put(Constants.FRAMEWORK_SYSTEMCAPABILITIES_EXTRA, systemCaps);
		Framework framework = fwkFact.newFramework(fwkProps);
		
		// Install the built-in Embark bundles 
		framework.init();
		BundleContext systemBundleContext = framework.getBundleContext();
		loadBundles(systemBundleContext, BUNDLE_LOCATIONS);
		
		// Start OSGi
		framework.start();
		
		// Start built-in activators
		List<BundleActivator> startedActivators = new LinkedList<>();
		for (String activatorName : ACTIVATORS) {
			Class<?> activatorClass = loader.loadClass(activatorName);
			BundleActivator activator = (BundleActivator) activatorClass.newInstance();
			activator.start(systemBundleContext);
			startedActivators.add(activator);
		}
		// We cannot call the activator's stop methods after framework.waitForStop() because the context
		// will no longer be valid. Instead, use a SynchronousBundleListener to catch the system bundle
		// stopping event.
		systemBundleContext.addBundleListener(new SynchronousBundleListener() {
			@Override
			public void bundleChanged(BundleEvent event) {
				if (event.getBundle().getBundleId() == 0 && event.getType() == BundleEvent.STOPPING) {
					for (BundleActivator activator : startedActivators) {
						try {
							activator.stop(systemBundleContext);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			}
		});
		
		// Wait for OSGi to end
		try {
			framework.waitForStop(0);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			System.exit(0);
		}
	}

	private static void recurseDelete(File file) throws IOException {
		if (!file.exists())
			return;
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			if (files != null) for (File child : files) {
				recurseDelete(child);
			}
		}
		Files.delete(file.toPath());
	}

	private static void loadBundles(BundleContext bc, String[] paths) throws Exception {
		// Find existing bundles and construct map of location -> bundle
		Map<String, Bundle> bundleMap = new HashMap<>();
		Bundle[] existingBundles = bc.getBundles();
		if (existingBundles != null) for (Bundle b : existingBundles)
			bundleMap.put(b.getLocation(), b);
		
		// Install or update built-ins
		List<Bundle> bundlesToStart = new ArrayList<>(4);
		for (String path : paths) {
			URL resource = Main.class.getResource("/" + path);
			if (resource == null)
				throw new IOException("Required resource not found in classpath: " + path);
			String location = "urn:embark-builtin:" + path;
			
			Bundle existingBundle = bundleMap.get(location);
			if (existingBundle != null) {
				existingBundle.update(resource.openStream());
				bundlesToStart.add(existingBundle);
			} else {
				Bundle bundle = bc.installBundle(location, resource.openStream());
				bundlesToStart.add(bundle);
			}
		}
		
		for (Bundle bundle : bundlesToStart) {
			BundleRevision revision = bundle.adapt(BundleRevision.class);
			boolean fragment = (BundleRevision.TYPE_FRAGMENT & revision.getTypes()) > 0;
			if (!fragment)
				bundle.start(Bundle.START_ACTIVATION_POLICY);
		}
	}

}
