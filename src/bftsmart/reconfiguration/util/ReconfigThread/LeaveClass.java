package bftsmart.reconfiguration.util.ReconfigThread;

import bftsmart.reconfiguration.VMServices;
import bftsmart.reconfiguration.util.TOMConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LeaveClass {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	private final int leavingReplicaID;
	private TOMConfiguration leavingReplicaConfig;

	public LeaveClass(int leavingReplicaID, TOMConfiguration leavingReplicaConfig) {
		this.leavingReplicaID = leavingReplicaID;
		this.leavingReplicaConfig = leavingReplicaConfig;
	}


	public boolean init() {
		try {
			logger.info("Attempting to leave (replica " + this.leavingReplicaID + ").");

			VMServices reconfigServices = new VMServices();
			reconfigServices.removeServer(this.leavingReplicaID, this.leavingReplicaConfig);

			logger.info("Replica {} left the view.", this.leavingReplicaID);
			return true;

		} catch (Exception e) {
			logger.error("Error while removing replica " + this.leavingReplicaID + ".");
			e.printStackTrace();
		}
		return false;
	}

}
