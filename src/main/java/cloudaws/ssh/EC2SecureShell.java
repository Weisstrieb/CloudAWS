package cloudaws.ssh;

import cloudaws.Main;
import cloudaws.concurrent.Promise;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

public class EC2SecureShell {

	private static final String USER = "ec2-user";
	private static final int SECOND = 1000;

	private final String keyPath;
	private final String address;

	private final JSch jsch;
	private Session session;

	private byte[] buffer = new byte[4096];

	public EC2SecureShell(String address, String keyPath) throws JSchException {
		jsch = new JSch();

		this.keyPath = keyPath;
		this.address = address;

		jsch.addIdentity(this.keyPath);
		session = jsch.getSession(USER, this.address, 22);

		session.setConfig("StrictHostKeyChecking", "no");
		session.setConfig("GSSAPIAuthentication", "no");
		session.setConfig("TCPKeepAlive", "yes");

		session.setServerAliveInterval(60 * SECOND);
		session.setServerAliveCountMax(30);
	}

	public void connect() throws JSchException {
		connect(30);
	}

	public void connect(int sec) throws JSchException {
		session.connect(sec * SECOND);
	}

	public CompletableFuture<List<String>> getSSHResponse(String command) {
		return this.getSSHResponse("", command, 5 * SECOND);
	}

	public CompletableFuture<List<String>> getSSHResponse(String srcDir, String command) {
		return this.getSSHResponse(srcDir, command, 5 * SECOND);
	}

	public CompletableFuture<List<String>> getSSHResponse(String command, long timeout) {
		return this.getSSHResponse("", command, timeout);
	}

	public CompletableFuture<List<String>> getSSHResponse(String srcDir, String command, long timeout) {
		CompletableFuture<List<String>> future = new CompletableFuture<>();
		Main.PROMISE_POOL.submit(() -> {
			ChannelExec channel = null;
			InputStream stream = null;

			try {
				this.connect();
				if (srcDir.length() > 0) {
					channel = (ChannelExec) session.openChannel("exec");
					channel.setCommand("cd " + srcDir);

					channel.connect();
					channel.disconnect();
					channel = null;
				}

				channel = (ChannelExec) session.openChannel("exec");
				channel.setCommand(command);

				stream = channel.getInputStream();
				channel.connect();

				Thread.sleep(timeout);
				if (stream.available() == 0) {
					future.completeExceptionally(new TimeoutException("Failed to fetch HTCondor status from server."));
					return false;
				}

				StringBuilder response = new StringBuilder();
				int len;
				while ((len = stream.read(buffer, 0, buffer.length)) > 0) {
					response.append(new String(buffer, 0, len));
				}

				future.complete(Arrays.asList(response.toString().split("\n")));
				return true;
			} catch (JSchException | IOException ex) {
				System.err.println("SSH Connection error occurred.");
				future.completeExceptionally(ex);
			} finally {
				if (channel != null) channel.disconnect();
				if (stream != null) stream.close();
				this.disconnect();
			}
			return false;
		});

		return new Promise<>(future, 5 * SECOND);
	}

	public void disconnect() {
		if (session != null) session.disconnect();
	}
}
