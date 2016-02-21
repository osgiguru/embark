package guru.osgi.simple.framework;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

public final class BundleOperation {

	private final Map<String, InputStream> installs = new HashMap<>();
	
	private boolean open = true;

	private BundleContext context;
	
	public BundleOperation(BundleContext context) {
		this.context = context;
		// TODO Auto-generated constructor stub
	}
	
	public void installBundle(String location) {
		installBundle(location, null);
	}
	
	public synchronized void installBundle(String location, InputStream stream) {
		checkOpen();
		installs.put(location, stream);
	}
	
	public void commit() throws BundleException {
		List<Bundle> installed = new LinkedList<>();
		
		// Installs
		for (Entry<String, InputStream> installEntry : installs.entrySet()) {
			String location = installEntry.getKey();
			InputStream stream = installEntry.getValue();
			
			Bundle bundle;
			if (stream == null) {}
				
		}
	}

	private synchronized void checkOpen() {
		if (!open) throw new IllegalStateException("Operation already closed.");
	}
	
}
