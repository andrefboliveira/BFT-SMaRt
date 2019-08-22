package bftsmart.reconfiguration.util.ReconfigThread;

import bftsmart.reconfiguration.VMServices;
import bftsmart.reconfiguration.util.TOMConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoveClass {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	private final int requesterID;
	private int toRemoveReplicaID;
	private TOMConfiguration initiatingReplicaConfig;


	public RemoveClass(int requesterID, int toRemoveReplicaID, TOMConfiguration initiatingReplicaConfig) {
		this.requesterID = requesterID;
		this.toRemoveReplicaID = toRemoveReplicaID;
		this.initiatingReplicaConfig = initiatingReplicaConfig;
	}

	public boolean init() {
		try {
			logger.info("\n Attempting to REMOVE replica " + toRemoveReplicaID + "!");

			forceRemoveReplica(toRemoveReplicaID);

			logger.info("The request from replica {} to remove replica {} was executed.", this.requesterID, this.toRemoveReplicaID);


			return true;
		} catch (Exception e) {
			logger.error("Error while removing replica " + this.toRemoveReplicaID + ".");
			e.printStackTrace();
		}
		return false;
	}


	private void forceRemoveReplica(int replicaToRemoveID) {
		VMServices reconfigServices = new VMServices();
		reconfigServices.forceRemoveServer(replicaToRemoveID, this.initiatingReplicaConfig);
	}

}
