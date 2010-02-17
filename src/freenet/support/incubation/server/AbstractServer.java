/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.support.incubation.server;

import java.io.IOException;
import java.net.Socket;

import org.tanukisoftware.wrapper.WrapperManager;

import freenet.io.NetworkInterface;
import freenet.io.SSLNetworkInterface;
import freenet.support.Executor;
import freenet.support.Logger;
import freenet.support.OOMHandler;
import freenet.support.io.Closer;

public abstract class AbstractServer implements Runnable {

	private static volatile boolean logMINOR;
	private static volatile boolean logDEBUG;

	static {
		Logger.registerClass(AbstractServer.class);
	}

	boolean isRunning;
	private String mAllowedHosts;
	private String mHost;
	private int mPort;
	private NetworkInterface networkInterface;
	private boolean ssl;
	private final Executor eXecutor;
	private final String serverName;

	public class SocketHandler implements Runnable {

		Socket sock;
		
		public SocketHandler(Socket conn) {
			this.sock = conn;
		}

		public void run() {
			freenet.support.Logger.OSThread.logPID(this);
			if(logMINOR)
				Logger.minor(this, "Handling connection");
			try {
				getService().handle(sock);
			} catch (OutOfMemoryError e) {
				OOMHandler.handleOOM(e);
				System.err.println(serverName+" request above failed.");
				Logger.error(this, "OOM in SocketHandler");
			} catch (Throwable t) {
				System.err.println("Caught in "+serverName+": "+t);
				t.printStackTrace();
				Logger.error(this, "Caught in "+serverName+": "+t, t);
			} finally {
				try {
					sock.close();
				} catch (IOException e) {
					// ignore
				}
			}
			if(logMINOR) Logger.minor(this, "Handled connection");
		}
	}

	public AbstractServer(String servername, Executor executor) {
		serverName = servername;
		eXecutor = executor;
	}

	public void kill() {
		stop();
	}

	protected abstract AbstractService getService();

	public void run() {
		try {
			realRun();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void realRun() throws IOException {
		while (isRunning) {
			if (networkInterface == null)
				networkInterface = getNetworkInterface();

			Socket conn = networkInterface.accept();
			if (WrapperManager.hasShutdownHookBeenTriggered())
				return;
			if(conn == null)
				continue; // timeout
			if(logMINOR)
				Logger.minor(this, "Accepted connection");
			SocketHandler sh = new SocketHandler(conn);
			eXecutor.execute(sh, serverName+" socket handler@"+hashCode());
		}
	}

	public void setAdress(String host, int port, String allowedHosts, boolean isSSL) {
		mHost = host;
		mPort = port;
		mAllowedHosts = allowedHosts;
		ssl = isSSL;
		if (networkInterface != null) {
			Closer.close(networkInterface);
			networkInterface = null;
		}
	}

	private NetworkInterface getNetworkInterface() throws IOException {
		if(ssl) {
			return SSLNetworkInterface.create(mPort, mHost, mAllowedHosts, eXecutor, true);
		} else {
			return NetworkInterface.create(mPort, mHost, mAllowedHosts, eXecutor, true);
		}
	}

	public void start() {
		if (logDEBUG)
			Logger.debug(this, "Starting "+serverName);
		isRunning = true;
		eXecutor.execute(this, serverName);
		if (logDEBUG)
			Logger.debug(this, serverName+" started.");
	}

	public void stop() {
		if (logDEBUG)
			Logger.debug(this, "begin stop");
		isRunning = false;
		Closer.close(networkInterface);
		if (logDEBUG)
			Logger.debug(this, "end stop");
	}

	public boolean isRunning() {
		return isRunning;
	}

}
