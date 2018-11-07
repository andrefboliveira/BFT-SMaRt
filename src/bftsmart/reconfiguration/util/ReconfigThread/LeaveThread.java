package bftsmart.reconfiguration.util.ReconfigThread;

import bftsmart.reconfiguration.VMServices;
import bftsmart.reconfiguration.util.TOMConfiguration;

import java.util.Scanner;

public class LeaveThread implements Runnable {

	private final int id;
	private TOMConfiguration leavingReplicaConfig;

	public LeaveThread(int id, TOMConfiguration leavingReplicaConfig) {
		this.id = id;
		this.leavingReplicaConfig = leavingReplicaConfig;
	}
	@Override
	public void run() {


		while (true){
			System.out.println("Type \"LEAVE\" to remove replica");

			Scanner sc = new Scanner(System.in);
			String userReply = sc.next();
			if (userReply.equalsIgnoreCase("LEAVE")) {

				System.out.println("Removing replica " + this.id + "!");
				VMServices reconfigServices = new VMServices();
				reconfigServices.removeServer(this.id, this.leavingReplicaConfig);

				try {
					Thread.sleep(1500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				System.exit(0);

				break;


			}
		}


	}
}
