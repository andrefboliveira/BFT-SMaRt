package bftsmart.tom.util.ReconfigThread;

import bftsmart.demo.test.MapRequestTypeTest;
import bftsmart.reconfiguration.VMServices;
import bftsmart.reconfiguration.views.View;
import bftsmart.tom.ServiceProxy;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.util.Extractor;

import java.io.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Scanner;

public class JoinThread implements Runnable {


	private final int id;
	private View currentView;

	public JoinThread(int id, View currentView) {
		this.id = id;
		this.currentView = currentView;
	}

	@Override
	public void run() {

		while (true){
			System.out.println("Type \"JOIN\" to add replica");

			Scanner sc = new Scanner(System.in);
			String userReply = sc.next();
//			String userReply = "JOIN";

			if (userReply.equalsIgnoreCase("JOIN")) {

				System.out.println("JOIN!");
				makeJoinRequest();

				break;


			}

		}



	}

	private void makeJoinRequest() {
//		ServiceProxy client = new ServiceProxy(7003);

		ServiceProxy client = new ServiceProxy(7003, null,
				new Comparator<byte[]>() {
					@Override
					public int compare(byte[] o1, byte[] o2) {
						return Arrays.equals(o1, o2) ? 0 : -1;
					}
				},
				new Extractor() {

					@Override
					public TOMMessage extractResponse(TOMMessage[] replies, int sameContent, int lastReceived) {
						return replies[lastReceived];
					}
				}, null);

		try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
			 ObjectOutput objOut = new ObjectOutputStream(byteOut);) {

			objOut.writeObject(MapRequestTypeTest.RECONFIG);
			objOut.writeUTF("ASK_JOIN");

			objOut.flush();
			byteOut.flush();

			byte[] reply = client.invokeOrdered(byteOut.toByteArray());

			try (ByteArrayInputStream byteIn = new ByteArrayInputStream(reply);
				 ObjectInput objIn = new ObjectInputStream(byteIn)) {
				String stringReply = objIn.readUTF();
				if ("Y".equalsIgnoreCase(stringReply)) {
					addReplica(currentView);
				}
			}

			client.close();


		} catch (IOException  e) {
			System.out.println("Exception creating JOIN request: " + e.getMessage());
		}
	}




	public static void serverReconfigRequest(String input, ObjectOutput out) throws IOException {
		if(input.equals("ASK_JOIN")) {

//
//			replicaContext.getStaticConfiguration().getPublicKey();
//			replicaContext.getStaticConfiguration().getPrivateKey();
//
//			replicaContext.getStaticConfiguration();

			out.writeUTF("Y");
		}

	}

	private void addReplica(View currentView) {
//		int suggestedID = Arrays.stream(currentView.getProcesses()).max().getAsInt() + 1;
//		System.out.println("Enter ID (ID suggested " + suggestedID + "): ");
////                            int newID = sc.nextInt();
//		int newID = suggestedID;


		String suggestedIP = suggestNewIP(currentView).getHostAddress();
		System.out.println("Enter IP (IP suggested " + suggestedIP + "): ");
//                            String newIP = sc.next();
		String newIP = suggestedIP;

		int suggestedPort = suggestPort(currentView);
		System.out.println("Enter Port (Port suggested " + suggestedPort + "): ");
//                            int newPort = sc.nextInt();
		int newPort = suggestedPort;


		System.out.println("Adding Server: " + this.id + "(/" + newIP + ":" +  newPort +")");

		VMServices reconfigServices = new VMServices();

		reconfigServices.addServer(this.id, newIP, newPort);
	}

	private InetAddress suggestNewIP(View currentView){
//		JaroWinklerDistance measureDistance = new JaroWinklerDistance();

		double maxSimilarity = Double.NEGATIVE_INFINITY;
		InetAddress foundAddress = null;

		try {
			Enumeration<NetworkInterface> iterList = NetworkInterface.getNetworkInterfaces();
			while(iterList.hasMoreElements()) {
				NetworkInterface ifc = iterList.nextElement();
				if(ifc.isUp()) {
					Enumeration<InetAddress> addrRawList = ifc.getInetAddresses();
					while (addrRawList.hasMoreElements()){
						InetAddress addr = addrRawList.nextElement();

						for (int id : currentView.getProcesses()) {
							String replicaAddr = currentView.getAddress(id).getAddress().getHostAddress();

							if (similarIP(replicaAddr, addr.getHostAddress())){
								return addr;

							}
//							double distance = measureDistance.apply(replicaAddr, addr.getHostAddress());
//
//
//							if (distance > maxSimilarity) {
//								maxSimilarity = distance;
//								foundAddress = addr;
//							}
//
//							if(distance >= 1) {
//								return foundAddress;
//							}

						}

					}

				}
			}

		} catch (SocketException e) {
			e.printStackTrace();
		}
		return foundAddress;
	}

	private boolean similarIP(String sourceIP, String targetIP) {
		String[] splitSourceIP = sourceIP.split("\\.");
		String[] splitTargetIP = targetIP.split("\\.");


		return splitSourceIP[0].equals(splitTargetIP[0])
				&& splitSourceIP[1].equals(splitTargetIP[1])
				&& splitSourceIP[2].equals(splitTargetIP[2]);
	}

	private int suggestPort(View currentView) {
		int[] ports = Arrays.stream(currentView.getProcesses()).map(id -> currentView.getAddress(id).getPort()).toArray();

		return ports[ports.length - 1] + (ports[1] - ports[0]);


	}
}
