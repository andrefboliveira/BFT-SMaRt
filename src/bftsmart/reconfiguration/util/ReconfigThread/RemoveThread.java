package bftsmart.reconfiguration.util.ReconfigThread;

import bftsmart.reconfiguration.VMServices;
import bftsmart.reconfiguration.util.TOMConfiguration;

import java.util.Scanner;

public class RemoveThread implements Runnable {

	private TOMConfiguration initiatingReplicaConfig;

	static String requestString = "REMOVE_REPLICA";
	static String replyString = "OK";

	public RemoveThread(TOMConfiguration initiatingReplicaConfig) {
		this.initiatingReplicaConfig = initiatingReplicaConfig;
	}


	@Override
	public void run() {


		while (true) {
			System.out.println("Type \"REMOVE\" to remove a replica");

			Scanner sc = new Scanner(System.in);
			String userReply = sc.next();
			if (userReply.equalsIgnoreCase("REMOVE")) {

				System.out.println("ID of replica to remove: ");

				int toRemoveReplicaID = sc.nextInt();

				System.out.println("REMOVE replica " + toRemoveReplicaID + "!");

				removeReplica(toRemoveReplicaID);


				break;


			}
		}


	}


	private void removeReplica(int replicaToRemoveID) {
		VMServices reconfigServices = new VMServices();
		reconfigServices.forceRemoveServer(replicaToRemoveID, this.initiatingReplicaConfig);
	}

}
