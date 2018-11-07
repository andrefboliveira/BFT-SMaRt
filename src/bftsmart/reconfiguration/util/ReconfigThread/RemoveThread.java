package bftsmart.reconfiguration.util.ReconfigThread;

import bftsmart.demo.test.MapRequestTypeTest;
import bftsmart.reconfiguration.VMServices;
import bftsmart.reconfiguration.util.ReconfigThread.pojo.CoreCertificate;
import bftsmart.reconfiguration.util.ReconfigThread.pojo.FullCertificate;
import bftsmart.reconfiguration.util.ReconfigThread.pojo.PartialCertificate;
import bftsmart.reconfiguration.util.ReconfigThread.pojo.ReplicaReconfigReply;
import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.tom.ServiceProxy;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.util.Extractor;
import bftsmart.tom.util.TOMUtil;

import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

				makeRemoveRequest(toRemoveReplicaID);
//				removeReplica(toRemoveReplicaID, null);

				break;


			}
		}


	}

	private void makeRemoveRequest(int toRemoveReplicaID) {

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
								&& reply1.getToReconfigureReplicaID() == reply2.getToReconfigureReplicaID()
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
							int toRemoveReplicaID = -1;
							long consensusTimestamp = -1;


							for (TOMMessage reply : replies) {
								if (reply != null) {
									byte[] content = reply.getContent();

									try (ByteArrayInputStream byteIn = new ByteArrayInputStream(content);
									     ObjectInputStream objIn = new ObjectInputStream(byteIn)) {

										ReplicaReconfigReply replicaReply = (ReplicaReconfigReply) objIn.readObject();


										CoreCertificate certificateValues = replicaReply.getCertificateValues();

										toRemoveReplicaID = certificateValues.getToReconfigureReplicaID();
										consensusTimestamp = certificateValues.getConsensusTimestamp();
										message = certificateValues.getReceivedMessage();

										PartialCertificate partialCertificate = new PartialCertificate(certificateValues.getExecutingReplicaID(), replicaReply.getSignature());


										certificate.add(partialCertificate);


									}

								}

							}


							try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
							     ObjectOutputStream objOut = new ObjectOutputStream(byteOut);) {

								FullCertificate fullCertificate = new FullCertificate(message, toRemoveReplicaID,
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

			objOut.writeObject(MapRequestTypeTest.RECONFIG_REMOVE);

			objOut.writeUTF(requestString);
			objOut.writeInt(toRemoveReplicaID);

			objOut.flush();
			byteOut.flush();

			byte[] reply = client.invokeOrdered(byteOut.toByteArray());

			try (ByteArrayInputStream byteIn = new ByteArrayInputStream(reply);
			     ObjectInputStream objIn = new ObjectInputStream(byteIn)) {

				FullCertificate fullCertificate = (FullCertificate) objIn.readObject();
				String stringReply = fullCertificate.getReceivedMessage();


				if (replyString.equalsIgnoreCase(stringReply)) {
					removeReplica(toRemoveReplicaID, fullCertificate);

				}
			}

			client.close();


		} catch (IOException | ClassNotFoundException e) {
			System.out.println("Exception creating JOIN request: " + e.getMessage());
		}
	}

	public static void serverReconfigRequest(String input, ObjectOutput out, int toRemoveReplicaID, long consensusTimestamp, TOMConfiguration executingReplicaConf) throws IOException {
		if (input.equals(requestString)) {


			CoreCertificate certificateValues = new CoreCertificate(toRemoveReplicaID, consensusTimestamp, replyString, executingReplicaConf.getProcessId());

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

	private void removeReplica(int replicaToRemoveID, FullCertificate fullCertificate) {
		VMServices reconfigServices = new VMServices();
		reconfigServices.removeServer(replicaToRemoveID, this.initiatingReplicaConfig, fullCertificate);
	}

}
