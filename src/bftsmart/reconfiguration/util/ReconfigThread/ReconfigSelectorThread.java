package bftsmart.reconfiguration.util.ReconfigThread;

import bftsmart.reconfiguration.util.TOMConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class ReconfigSelectorThread implements Runnable {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	private final int id;
	private final TOMConfiguration executingReplicaConfig;

	public ReconfigSelectorThread(int id, TOMConfiguration executingReplicaConfig) {
		this.id = id;
		this.executingReplicaConfig = executingReplicaConfig;
	}

	@Override
	public void run() {

		boolean keep_running = true;
		int attempts = 0;

		Scanner sc = null;
		if (executingReplicaConfig.isStdinFromFile()) {
			try {
				sc = new Scanner(new File(executingReplicaConfig.getConfigHome() + "/stdin"));
			} catch (FileNotFoundException e) {
				logger.error("File stdin not found");
			}
		} else {
			sc = new Scanner(System.in);
		}

		while (keep_running && attempts <= 10) {
			try {

				logger.info("Type: " +
						"\"LEAVE\" (\"L\") to remove THIS replica from view " +
						"or " +
						"\"REMOVE\" (\"R\") to remove a specific replica from view");


				String userReply = sc.next();

				if ("LEAVE".equalsIgnoreCase(userReply) || "L".equalsIgnoreCase(userReply)) {

					try {
						LeaveClass leaveProtocol = new LeaveClass(this.id, this.executingReplicaConfig);
						boolean sucessful = leaveProtocol.init();

						if (sucessful) {
							keep_running = false;
							attempts = 0;
						}

					} catch (Exception e) {
						logger.error("Error while processing Leave request");
						e.printStackTrace();
					}
				} else if ("REMOVE".equalsIgnoreCase(userReply) || "R".equalsIgnoreCase(userReply)) {
					try {
						RemoveClass removeProtocol = new RemoveClass(this.id, this.executingReplicaConfig);
						removeProtocol.init();

					} catch (Exception e) {
						logger.error("Error while processing Remove request");
						e.printStackTrace();
					}
					attempts = 0;
				} else if ("WAIT".equalsIgnoreCase(userReply) || "W".equalsIgnoreCase(userReply)) {
					try {
						logger.info("Input number of seconds to wait: ");
						int waitTime = sc.nextInt();
						logger.info("Waiting {} s ...", waitTime);
						Thread.sleep(waitTime * 1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					attempts = 0;
				} else if ("QUIT".equalsIgnoreCase(userReply) || "Q".equalsIgnoreCase(userReply)) {
					keep_running = false;
					logger.info("Quit selector");
				} else {
					attempts++;
				}

			} catch (Exception e) {
				logger.error("Error while processing Selecting option");
				e.printStackTrace();
				attempts++;
			}
		}

		logger.info("Leaving Selector");


	}
}
