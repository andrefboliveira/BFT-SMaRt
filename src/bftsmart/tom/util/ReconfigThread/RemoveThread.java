package bftsmart.tom.util.ReconfigThread;

import bftsmart.reconfiguration.VMServices;
import bftsmart.reconfiguration.util.TOMConfiguration;

import java.util.Scanner;

public class RemoveThread implements Runnable{

	private final int id;
	private TOMConfiguration joiningReplicaConfig;

	public RemoveThread(int id, TOMConfiguration joiningReplicaConfig) {
		this.id = id;
		this.joiningReplicaConfig = joiningReplicaConfig;
	}
	@Override
	public void run() {


		while (true){
			System.out.println("Type \"REMOVE\" to remove replica");

			Scanner sc = new Scanner(System.in);
			String userReply = sc.next();
			if (userReply.equalsIgnoreCase("REMOVE")) {

				System.out.println("REMOVE replica " + this.id + "!");
				VMServices reconfigServices = new VMServices();
				reconfigServices.removeServer(this.id, this.joiningReplicaConfig);

				System.exit(0);

				break;


			}
		}


	}
}
