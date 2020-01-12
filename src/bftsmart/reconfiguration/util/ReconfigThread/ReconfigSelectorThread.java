package bftsmart.reconfiguration.util.ReconfigThread;

import bftsmart.reconfiguration.util.TOMConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

public class ReconfigSelectorThread implements Runnable {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	private final int id;
	private final TOMConfiguration executingReplicaConfig;
	private final Scanner sc;

	public ReconfigSelectorThread(int id, TOMConfiguration executingReplicaConfig, Scanner sc) {
		this.id = id;
		this.executingReplicaConfig = executingReplicaConfig;
		this.sc = sc;
	}

	@Override
	public void run() {

		boolean keep_running = true;
		int attempts = 0;

		while (keep_running && attempts <= 10) {
			try {

				logger.info("Type: " +
						"\"LEAVE\" (\"L\") to remove THIS replica from view " +
						"or " +
						"\"REMOVE\" (\"R\") to remove a specific replica from view");


                String userReply = "";
                                
                                /*if(id == 4){
                                
                                    userReply = "WAIT";
                                }else{*/
                userReply = sc.next();
                /* }*/
				logger.info("You typed {}", userReply);

				if ("LEAVE".equalsIgnoreCase(userReply) || "L".equalsIgnoreCase(userReply)) {
					try {
						LeaveClass leaveProtocol = new LeaveClass(this.id, this.executingReplicaConfig);
						boolean successful = leaveProtocol.init();

						if (successful) {
							keep_running = false;
						}
					} catch (Exception e) {
						logger.error("Error while processing Leave request");
						e.printStackTrace();
					}
					attempts = 0;

				} else if ("REMOVE".equalsIgnoreCase(userReply) || "R".equalsIgnoreCase(userReply)) {
					logger.info("Type the ID of replica to remove: ");
					int toRemoveReplicaID = sc.nextInt();

					logger.info("You typed {}", toRemoveReplicaID);

					try {
						RemoveClass removeProtocol = new RemoveClass(this.id, toRemoveReplicaID, this.executingReplicaConfig);
						removeProtocol.init();

					} catch (Exception e) {
						logger.error("Error while processing Remove request");
						e.printStackTrace();
					}
					attempts = 0;
				} else if ("WAIT".equalsIgnoreCase(userReply) || "W".equalsIgnoreCase(userReply)) {
					try {
						logger.info("Input number of seconds to wait: ");
                        //int waitTime = sc.nextInt();
                        int waitTime = 360;
						logger.info("Waiting {} s ...", waitTime);
						Thread.sleep(waitTime * 1000);

                        try {
                            LeaveClass leaveProtocol = new LeaveClass(this.id, this.executingReplicaConfig);
                            boolean successful = leaveProtocol.init();

                            if (successful) {
                                keep_running = false;
                            }
                        } catch (Exception e) {
                            logger.error("Error while processing Leave request");
                            e.printStackTrace();
                        }
                                                
                                                
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					attempts = 0;
				} else if ("QUIT".equalsIgnoreCase(userReply) || "Q".equalsIgnoreCase(userReply)) {
					keep_running = false;
					logger.info("Quit  Reconfig selector");
				} else {
					attempts++;
				}

			} catch (Exception e) {
				logger.error("Error while processing Selecting option");
				e.printStackTrace();
				attempts++;
			}
		}

		logger.info("Exit Reconfig selector");


	}
}
