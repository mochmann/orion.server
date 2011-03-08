package org.eclipse.orion.internal.server.hosting;

import java.net.*;
import java.util.*;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.server.core.LogHelper;

/**
 * Stores the list of host names that may be allocated to hosted sites, and the port number that 
 * sites will be accessed on. The server administrator can control the host name configuration by
 * passing a parameter to the Orion server at launch.<p>
 * 
 * The parameter's value is a comma-separated list where each entry is either a valid domain name
 * (which may include wildcards), or a valid IP address. Each entry in the list becomes the name of
 * a virtual host that a site can be served on. Wildcard domains can be used to server many different
 * virtual hosts.
 * 
 * Examples:
 * <dl>
 * <dt><code>site.myorion.net</code></dt>
 * <dd>Makes 1 host name, <code>site.myorion.net</code>, available for allocation as a virtual host name.</dd>
 * 
 * <dt><code>127.0.0.2,127.0.0.3</code></dt>
 * <dd>Makes the 2 IP addresses available as virtual host names. Since they happen to be loopback addresses,
 * any hosted site assigned to them will not be accessible by remote users.</dd>
 * 
 * <dt><code>*.myorion.net</code></dt>
 * <dd>Makes all of <code>*.myorion.net</code> available for allocation. Sites will be given subdomains,
 * for example <code>site1.myorion.net</code>, <code>site2.myorion.net</code>, etc.</dd>
 * 
 * <dt><code>foo.myorion.net,*.myorion.net</code></dt>
 * <dd>The domains will be allocated in the order provided: first <code>foo.myorion.net</code>, then 
 * subdomains of <code>myorion.net</code>.</dd>
 * </dl>
 */
public class SiteHostingConfig {

	private static final int DEFAULT_HOST_COUNT = 16;

	private int hostingPort;
	private List<String> hosts;

	private SiteHostingConfig(int port, List<String> hosts) {
		this.hostingPort = port;
		this.hosts = hosts;
	}

	public int getHostingPort() {
		return this.hostingPort;
	}

	/**
	 * @return An unmodifiable view of all hosts in the configuration, in the same order as the 
	 * user provided them, and not checked for duplicate entries. 
	 */
	public List<String> getHosts() {
		return Collections.unmodifiableList(hosts);
	}

	/**
	 * @param hostInfo String containing the user-supplied site configuration info.
	 * @return A hosting configuration parsed from <code>hostInfo</code>. If <code>vhostInfo</code>
	 * is <code>null</code>, returns a default configuration.
	 */
	static SiteHostingConfig getSiteHostingConfig(int port, String hostInfo) {
		return hostInfo == null ? getDefault(port) : fromString(port, hostInfo);
	}

	/**
	 * Parses a SiteHostingConfig from the <code>hostInfo</code> string.
	 */
	private static SiteHostingConfig fromString(int port, String hostInfo) {
		List<String> hosts = new ArrayList<String>();
		StringTokenizer st = new StringTokenizer(hostInfo, ", "); //$NON-NLS-1$
		if (!st.hasMoreTokens()) {
			LogHelper.log(new Status(IStatus.ERROR, HostingActivator.PI_SERVER_HOSTING, "Empty hosting configuration", null));
		}
		for (; st.hasMoreTokens();) {
			String token = st.nextToken();
			hosts.add(token);
		}
		return new SiteHostingConfig(port, hosts);
	}

	/**
	 * @param port
	 * @return A default SiteHostingConfig based on the platform and network setup. If possible,
	 * the config will include some IP addresses that point to the loopback device.
	 */
	private static SiteHostingConfig getDefault(int port) {
		List<String> aliases = new ArrayList<String>();

		// TODO: this guessing is fragile and only useful for self-hosting. Is it really useful?
		if (System.getProperty("os.name").startsWith("Mac OS X")) { //$NON-NLS-1$ //$NON-NLS-2$
			// In OS X, although all 127.x.x.x addresses report isLoopback() == true, only 127.0.0.1
			// actually routes to localhost. So we can't do much here.
		} else {
			InetAddress loopbackIp = getLoopbackAddress();
			try {
				// Allocate some more loopback IPs
				byte[] address = loopbackIp.getAddress();
				byte lastByte = address[address.length - 1];
				for (int i = 0, b = lastByte + 1; i < DEFAULT_HOST_COUNT && b <= 0xff; i++, b++) {
					byte[] derivedAddress = address.clone();
					derivedAddress[address.length - 1] = (byte) b;
					aliases.add(InetAddress.getByAddress(derivedAddress).getHostAddress());
				}
			} catch (UnknownHostException e) {
				// Should not happen
				LogHelper.log(new Status(IStatus.ERROR, HostingActivator.PI_SERVER_HOSTING, e.getMessage(), e));
			}
		}
		return new SiteHostingConfig(port, aliases);
	}

	/**
	 * @return The IPv4 loopback device, or null if it couldn't be determined.
	 */
	private static InetAddress getLoopbackAddress() {
		try {
			for (NetworkInterface interfaze : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (interfaze.isLoopback()) {
					for (InetAddress address : Collections.list(interfaze.getInetAddresses())) {
						if (address instanceof Inet4Address) {
							return address;
						}
					}
				}
			}
		} catch (SocketException e) {
			LogHelper.log(new Status(IStatus.ERROR, HostingActivator.PI_SERVER_HOSTING, e.getMessage(), e));
		}
		return null;
	}
}