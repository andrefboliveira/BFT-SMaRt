package bftsmart.tom.util.ReconfigThread;

import bftsmart.demo.test.MapRequestTypeTest;
import bftsmart.reconfiguration.VMServices;
import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.reconfiguration.views.View;
import bftsmart.tom.ServiceProxy;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.util.Extractor;
import bftsmart.tom.util.TOMUtil;

import java.io.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.*;


public class JoinThread implements Runnable {


	private final int id;
	private TOMConfiguration joiningReplicaConfig;
	private View currentView;

	static String requestString = "ASK_JOIN";
	static String replyString = "YES";


	public JoinThread(int id, TOMConfiguration joiningReplicaConfig, View currentView) {
		this.id = id;
		this.joiningReplicaConfig = joiningReplicaConfig;
		this.currentView = currentView;
	}


	@Override
	public void run() {

		while (true) {
			System.out.println("Type \"JOIN\" to add replica");

			Scanner sc = new Scanner(System.in);
//			String userReply = sc.next();
			String userReply = "JOIN";
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

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

//		ServiceProxy client = new ServiceProxy(joiningReplicaConfig.getTTPId(), joiningReplicaConfig.getConfigHome(),
				new Comparator<byte[]>() {
					@Override
					public int compare(byte[] o1, byte[] o2) {
						CoreCertificate reply1 = null;
						CoreCertificate reply2 = null;

						try {
							try (ByteArrayInputStream byteIn = new ByteArrayInputStream(o1);
							     ObjectInputStream objIn = new ObjectInputStream(byteIn)) {
								ReplicaReconfigReply replicaReply1 = (ReplicaReconfigReply) objIn.readObject();

								reply1 = replicaReply1.getCertificateValues();

							}

							try (ByteArrayInputStream byteIn = new ByteArrayInputStream(o2);
							     ObjectInputStream objIn = new ObjectInputStream(byteIn)) {

								ReplicaReconfigReply replicaReply2 = (ReplicaReconfigReply) objIn.readObject();

								reply2 = replicaReply2.getCertificateValues();
							}

						} catch (IOException | ClassNotFoundException e) {
							e.printStackTrace();
						}


						return reply1.getReceivedMessage().equals(reply2.getReceivedMessage())
								&& reply1.getJoiningReplicaID() == reply2.getJoiningReplicaID()
								&& reply1.getConsensusTimestamp() == reply2.getConsensusTimestamp()
								? 0 : -1;
					}
				},
				new Extractor() {

					@Override
					public TOMMessage extractResponse(TOMMessage[] replies, int sameContent, int lastReceived) {

						TOMMessage newReply = null;
						byte[] newContent = new byte[0];


						try {

							List<PartialCertificate> certificate = new ArrayList<PartialCertificate>();

							String message = null;
							int joiningReplicaID = -1;
							long consensusTimestamp = -1;


							for (TOMMessage reply : replies) {
								if (reply != null) {
									byte[] content = reply.getContent();

									try (ByteArrayInputStream byteIn = new ByteArrayInputStream(content);
									     ObjectInputStream objIn = new ObjectInputStream(byteIn)) {

										ReplicaReconfigReply replicaReply = (ReplicaReconfigReply) objIn.readObject();


										CoreCertificate certificateValues = replicaReply.getCertificateValues();

										joiningReplicaID = certificateValues.getJoiningReplicaID();
										consensusTimestamp = certificateValues.getConsensusTimestamp();
										message = certificateValues.getReceivedMessage();

										PartialCertificate partialCertificate = new PartialCertificate(certificateValues.getExecutingReplicaID(), replicaReply.getSignature());


										certificate.add(partialCertificate);


									}

								}

							}


							try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
							     ObjectOutputStream objOut = new ObjectOutputStream(byteOut);) {

								FullCertificate fullCertificate = new FullCertificate(message, joiningReplicaID,
										consensusTimestamp, certificate);

								objOut.writeObject(fullCertificate);

								objOut.flush();
								byteOut.flush();

								newContent = byteOut.toByteArray();
							}

						} catch (IOException | ClassNotFoundException e) {
							e.printStackTrace();

						}

						try {
							newReply = (TOMMessage) replies[lastReceived].clone();
							newReply.setContent(newContent);
						} catch (CloneNotSupportedException e) {
							e.printStackTrace();
						}

						return newReply;
					}
				}, null);

		try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		     ObjectOutputStream objOut = new ObjectOutputStream(byteOut);) {

			objOut.writeObject(MapRequestTypeTest.RECONFIG);

			objOut.writeUTF(requestString);
			objOut.writeInt(this.id);

			objOut.flush();
			byteOut.flush();

			byte[] reply = client.invokeOrdered(byteOut.toByteArray());

			try (ByteArrayInputStream byteIn = new ByteArrayInputStream(reply);
			     ObjectInputStream objIn = new ObjectInputStream(byteIn)) {

				FullCertificate fullCertificate = (FullCertificate) objIn.readObject();
				String stringReply = fullCertificate.getReceivedMessage();


				if (replyString.equalsIgnoreCase(stringReply)) {
					addReplica(fullCertificate);

				}
			}

			client.close();


		} catch (IOException | ClassNotFoundException e) {
			System.out.println("Exception creating JOIN request: " + e.getMessage());
		}
	}

	public static void serverReconfigRequest(String input, ObjectOutput out, int joiningReplicaID, long consensusTimestamp, TOMConfiguration executingReplicaConf) throws IOException {
		if (input.equals(requestString)) {


			CoreCertificate certificateValues = new CoreCertificate(joiningReplicaID, consensusTimestamp, replyString, executingReplicaConf.getProcessId());

			byte[] signature;

			try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
			     ObjectOutputStream objOut = new ObjectOutputStream(byteOut);) {

				certificateValues.serialize(objOut);


				objOut.flush();
				byteOut.flush();


				signature = TOMUtil.signMessage(executingReplicaConf.getPrivateKey(),
						byteOut.toByteArray());
			}


			ReplicaReconfigReply reply = new ReplicaReconfigReply(certificateValues, signature);

			out.writeObject(reply);


		}

	}


	private void addReplica(FullCertificate fullCertificate) {
//		int suggestedID = Arrays.stream(currentView.getProcesses()).max().getAsInt() + 1;
//		System.out.println("Enter ID (ID suggested " + suggestedID + "): ");
////                            int newID = sc.nextInt();
//		int newID = suggestedID;

/*

		String suggestedIP = suggestNewIP().getHostAddress();
		System.out.println("Enter IP (IP suggested " + suggestedIP + "): ");
//                            String newIP = sc.next();
		String newIP = suggestedIP;

		int suggestedPort = suggestPort();
		System.out.println("Enter Port (Port suggested " + suggestedPort + "): ");
//                            int newPort = sc.nextInt();
		int newPort = suggestedPort;
*/

//		int newID = suggestedID;

		String newIP = joiningReplicaConfig.getLocalAddress(this.id).getHostName();
		int newPort = joiningReplicaConfig.getPort(this.id);

		System.out.println("Adding Server: " + this.id + "(/" + newIP + ":" + newPort + ")");

		VMServices reconfigServices = new VMServices();

		reconfigServices.addServer(this.id, newIP, newPort, this.joiningReplicaConfig, fullCertificate);


	}

	private InetAddress suggestNewIP() {
//		JaroWinklerDistance measureDistance = new JaroWinklerDistance();

		double maxSimilarity = Double.NEGATIVE_INFINITY;
		InetAddress foundAddress = null;

		try {
			Enumeration<NetworkInterface> iterList = NetworkInterface.getNetworkInterfaces();
			while (iterList.hasMoreElements()) {
				NetworkInterface ifc = iterList.nextElement();
				if (ifc.isUp()) {
					Enumeration<InetAddress> addrRawList = ifc.getInetAddresses();
					while (addrRawList.hasMoreElements()) {
						InetAddress addr = addrRawList.nextElement();

						for (int id : currentView.getProcesses()) {
							String replicaAddr = currentView.getAddress(id).getAddress().getHostAddress();

							if (similarIP(replicaAddr, addr.getHostAddress())) {
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

	private int suggestPort() {
		int[] ports = Arrays.stream(currentView.getProcesses()).map(id -> currentView.getAddress(id).getPort()).toArray();

		return ports[ports.length - 1] + (ports[1] - ports[0]);


	}
}
