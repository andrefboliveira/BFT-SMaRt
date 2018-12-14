package bftsmart.reconfiguration.util.ReconfigThread;

import bftsmart.reconfiguration.util.TOMConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

		while (keep_running) {
			logger.info("Type: " +
					"\"LEAVE\" (\"L\") to remove THIS replica from view " +
					"or " +
					"\"REMOVE\" (\"R\") to remove a specific replica from view");

			Scanner sc = new Scanner(System.in);
			String userReply = sc.next();

			if ("LEAVE".equalsIgnoreCase(userReply) || "L".equalsIgnoreCase(userReply)) {

				try {
					LeaveClass leaveProtocol = new LeaveClass(this.id, this.executingReplicaConfig);
					boolean sucessful = leaveProtocol.init();

					if (sucessful) {
						keep_running = false;
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
			}
		}


	}
}
