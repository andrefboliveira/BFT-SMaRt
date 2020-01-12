package bftsmart.reconfiguration.util.ReconfigThread;

import bftsmart.reconfiguration.VMServices;
import bftsmart.reconfiguration.util.ReconfigThread.pojo.CoreCertificate;
import bftsmart.reconfiguration.util.ReconfigThread.pojo.FullCertificate;
import bftsmart.reconfiguration.util.ReconfigThread.pojo.PartialCertificate;
import bftsmart.reconfiguration.util.ReconfigThread.pojo.ReplicaReconfigReply;
import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.reconfiguration.views.View;
import bftsmart.tom.ServiceProxy;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.server.ServerJoiner;
import bftsmart.tom.util.Extractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;


public class JoinThread implements Runnable {

	private Logger logger = LoggerFactory.getLogger(this.getClass());


	private final int id;
	private TOMConfiguration joiningReplicaConfig;
	private final Scanner sc;
	//private View currentView;
	private ServerJoiner joiner;
    public ServiceProxy client;


    public JoinThread(int id, TOMConfiguration joiningReplicaConfig, View currentView, Scanner sc, ServerJoiner joiner) {
        this.id = id;
        this.joiningReplicaConfig = joiningReplicaConfig;
        //this.currentView = currentView;
        this.sc = sc;
        this.joiner = joiner;

        this.client = new ServiceProxy(joiningReplicaConfig.getTTPId(), joiningReplicaConfig.getConfigHome(),
                new Comparator<byte[]>() {
                    @Override
                    public int compare(byte[] o1, byte[] o2) {
                        CoreCertificate reply1 = null;
                        CoreCertificate reply2 = null;

                        try {
                            try (ByteArrayInputStream byteIn = new ByteArrayInputStream(o1);
                                 DataInputStream dis = new DataInputStream(byteIn)) {
                                ReplicaReconfigReply replicaReply1 = ReplicaReconfigReply.desSerialize(dis);

                                reply1 = replicaReply1.getCertificateValues();

                            }

                            try (ByteArrayInputStream byteIn = new ByteArrayInputStream(o2);
                                 DataInputStream dis = new DataInputStream(byteIn)) {

                                ReplicaReconfigReply replicaReply2 = ReplicaReconfigReply.desSerialize(dis);

                                reply2 = replicaReply2.getCertificateValues();
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        return CoreCertificate.compareCertificates(reply1, reply2) ? 0 : -1;
                    }
                },
                new Extractor() {

                    @Override
                    public TOMMessage extractResponse(TOMMessage[] replies, int sameContent, int lastReceived) {

                        TOMMessage newReply = null;
                        byte[] newContent = new byte[0];


                        try {

                            List<PartialCertificate> certificate = new ArrayList<PartialCertificate>();

                            byte[] proof = null;
                            boolean accepted = false;
                            int joiningReplicaID = -1;
                            long consensusTimestamp = -1;


                            for (TOMMessage reply : replies) {
                                if (reply != null) {
                                    byte[] content = reply.getContent();

                                    try (ByteArrayInputStream byteIn = new ByteArrayInputStream(content);
                                         DataInputStream dis = new DataInputStream(byteIn)) {

                                        ReplicaReconfigReply replicaReply = ReplicaReconfigReply.desSerialize(dis);

                                        CoreCertificate certificateValues = replicaReply.getCertificateValues();

                                        joiningReplicaID = certificateValues.getToReconfigureReplicaID();
                                        consensusTimestamp = certificateValues.getConsensusTimestamp();
                                        proof = certificateValues.getInputProof();
                                        accepted = certificateValues.isAcceptedRequest();

                                        PartialCertificate partialCertificate = new PartialCertificate(certificateValues.getExecutingReplicaID(), replicaReply.getSignature());


                                        certificate.add(partialCertificate);


                                    }

                                }

                            }


                            try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                                 DataOutputStream dos = new DataOutputStream(byteOut)) {

                                FullCertificate fullCertificate = new FullCertificate(joiningReplicaID, accepted, proof,
                                        consensusTimestamp, certificate);


                                fullCertificate.serialize(dos);

                                dos.flush();
                                byteOut.flush();

                                newContent = byteOut.toByteArray();
                            }

                        } catch (IOException e) {
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

    }


    @Override
    public void run() {

        boolean keep_running = true;
        int attempts = 0;


        while (keep_running && attempts <= 10) {
            try {
                logger.info("Type: \"JOIN\" (\"J\") to add THIS replica to view.");


                String userReply = sc.next();
                logger.info("You typed {}", userReply);

                if ("JOIN".equalsIgnoreCase(userReply) || "J".equalsIgnoreCase(userReply)) {

                    logger.info("Attempting to JOIN.");

                    byte[] reply = makeInitialJoinRequest();

                    //System.out.println("CHEGOU AQUI 1");

                    FullCertificate fullCertificate = extractCertificateFromJoinRequest(reply);

                    // System.out.println("CHEGOU AQUI 2");

                    if (fullCertificate != null) {
                        boolean success = addReplicaProtocol(fullCertificate);

                        if (success) {
                            keep_running = false;
                        }
                    }
                    attempts = 0;

                } else if ("WAIT".equalsIgnoreCase(userReply) || "W".equalsIgnoreCase(userReply)) {
                    logger.info("Input number of seconds to wait: ");
                    int waitTime = sc.nextInt();
                    logger.info("Waiting {} s ...", waitTime);
                    Thread.sleep(waitTime * 1000);
                    attempts = 0;

                    logger.info("Attempting to JOIN.");

                    //System.out.println("Join start time: "+  System.currentTimeMillis());

                    byte[] reply = makeInitialJoinRequest();

                    FullCertificate fullCertificate = extractCertificateFromJoinRequest(reply);

                    if (fullCertificate != null) {
                        boolean success = addReplicaProtocol(fullCertificate);

                        if (success) {
                            keep_running = false;
                        }
                    }

                } else if ("QUIT".equalsIgnoreCase(userReply) || "Q".equalsIgnoreCase(userReply)) {
                    keep_running = false;
                    logger.info("Quit join selector");
                } else {
                    attempts++;
                }
            } catch (Exception e) {
                logger.error("Error while processing Join request");
                e.printStackTrace();
                attempts++;
            }

        }

        logger.info("Exit join selector");


    }

    private byte[] makeInitialJoinRequest() {
//		ServiceProxy client = new ServiceProxy(7003);

//		ServiceProxy client = new ServiceProxy(7003, null,


        byte[] request = createJoinRequest();

        byte[] reply = client.invokeOrdered(request);
        client.close();

        return reply;

    }

	private byte[] createJoinRequest() {

		try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		     DataOutputStream dos = new DataOutputStream(byteOut)) {

			dos.writeInt(this.id);

			byte[] request = joiner.appCreateJoinRequest(null);
			dos.writeInt(request.length);
			dos.write(request);


			dos.flush();
			byteOut.flush();

			byte[] messageBytes = byteOut.toByteArray();

			String flag = "JOIN_REQUEST";

			ByteBuffer buff = ByteBuffer.allocate(flag.getBytes().length + (Integer.BYTES * 2)
					+ messageBytes.length + (Integer.BYTES * 2));
			buff.putInt(flag.getBytes().length);
			buff.put(flag.getBytes());

			buff.putInt(messageBytes.length);
			buff.put(messageBytes);


			return buff.array();


		} catch (IOException e) {
			logger.error("Exception creating JOIN request: " + e.getMessage());
		}

		return null;
	}

	private FullCertificate extractCertificateFromJoinRequest(byte[] reply) {
		if (reply != null) {

			try (ByteArrayInputStream byteIn = new ByteArrayInputStream(reply);
			     DataInputStream dis = new DataInputStream(byteIn)) {

				FullCertificate fullCertificate = FullCertificate.desSerialize(dis);

				if (fullCertificate.isAcceptedRequest()) {
					return fullCertificate;
				}

			} catch (IOException e) {
				logger.error("Exception creating JOIN request: " + e.getMessage());
			}
		}

		return null;
	}


	private boolean addReplicaProtocol(FullCertificate fullCertificate) {
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


		String newIP = joiningReplicaConfig.getRemoteAddress(this.id).getAddress().getHostAddress();

		int newPort = joiningReplicaConfig.getPort(this.id);

		logger.info("Adding Server: " + this.id + "(/" + newIP + ":" + newPort + ")");

		VMServices reconfigServices = new VMServices();

		reconfigServices.addServer(this.id, newIP, newPort, this.joiningReplicaConfig, fullCertificate);

		return true;


	}
/*
	private InetAddress suggestNewIP() {
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
	*/
}
