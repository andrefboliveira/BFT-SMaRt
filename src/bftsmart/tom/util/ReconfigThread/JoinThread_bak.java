package bftsmart.tom.util.ReconfigThread;

public class JoinThread_bak implements Runnable {
	@Override
	public void run() {

	}
/*

	private final int id;
	private View currentView;

	static String requestString = "ASK_JOIN";
	static String replyString = "YES";


	public JoinThread_bak(int id, View currentView) {
		this.id = id;
		this.currentView = currentView;
	}

	@Override
	public void run() {

		while (true){
			System.out.println("Type \"JOIN\" to add replica");

			Scanner sc = new Scanner(System.in);
//			String userReply = sc.next();
			String userReply = "JOIN";

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
						String reply1 = null;
						String reply2 = null;

						try {
							try (ByteArrayInputStream in = new ByteArrayInputStream(o1);
								 DataInputStream dis = new DataInputStream(in)) {

								byte[] reply1Bytes = new byte[dis.readInt()];
								dis.read(reply1Bytes);

								reply1 = new String(reply1Bytes, StandardCharsets.UTF_8);

							}

							try (ByteArrayInputStream in = new ByteArrayInputStream(o2);
								 DataInputStream dis = new DataInputStream(in)) {
								byte[] reply2Bytes = new byte[dis.readInt()];
								dis.read(reply2Bytes);

								reply2 = new String(reply2Bytes, StandardCharsets.UTF_8);
							}

						} catch (IOException e) {
							e.printStackTrace();
						}

						return reply1.equals(reply2) ? 0 : -1;
					}
				},
				new Extractor() {

					@Override
					public TOMMessage extractResponse(TOMMessage[] replies, int sameContent, int lastReceived) {

						TOMMessage newReply = null;
						byte[] newContent = new byte[0];

						String message = null;
						byte[] certificate = new byte[0];

						int numberOfPartialCertificates = 0;

						try {


							try (ByteArrayOutputStream out = new ByteArrayOutputStream();
								 DataOutputStream dos = new DataOutputStream(out);) {

								for (TOMMessage reply : replies) {
									if (reply != null) {
										byte[] content = reply.getContent();
										byte[] partialCertificate;

										System.out.println(content==null);

										try (ByteArrayInputStream in = new ByteArrayInputStream(content);
											 DataInputStream dis = new DataInputStream(in)) {

											int stringSize = dis.readInt();

											byte[] stringBytes = new byte[stringSize];
											dis.read(stringBytes);

											message = new String(stringBytes, StandardCharsets.UTF_8);

											int partialCertificateLength = content.length - (stringSize + Integer.BYTES);

											System.out.println("message: " + message);

											partialCertificate = new byte[partialCertificateLength];
											dis.read(partialCertificate);

											System.out.println("partialCertificate: " + Arrays.toString(partialCertificate));


										}

										numberOfPartialCertificates++;
										dos.write(partialCertificate);


									}

								}

								dos.flush();
								out.flush();
								certificate = out.toByteArray();


							}

							System.out.println("certificate: " + Arrays.toString(certificate));


							try (ByteArrayOutputStream out = new ByteArrayOutputStream();
								 DataOutputStream dos = new DataOutputStream(out);) {


								dos.writeUTF(message);
								dos.writeInt(numberOfPartialCertificates);
								dos.write(certificate);

								dos.flush();
								out.flush();

								newContent = out.toByteArray();
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

		try (ByteArrayOutputStream out = new ByteArrayOutputStream();
			 DataOutputStream dos = new DataOutputStream(out);) {

			dos.writeInt(MapRequestTypeTest.RECONFIG.ordinal());

			dos.writeUTF(requestString);

			dos.flush();
			out.flush();

			byte[] reply = client.invokeOrdered(out.toByteArray());

			try (ByteArrayInputStream in = new ByteArrayInputStream(reply);
				 DataInputStream dis = new DataInputStream(in)) {
				String stringReply = dis.readUTF();

				int numPartialCertificates = dis.readInt();

				System.out.println(stringReply);
				System.out.println(numPartialCertificates);

				for (int i = 0; i < numPartialCertificates; i++) {

					byte[] pubKey = new byte[dis.readInt()];
					dis.read(pubKey);

					byte[] signature = new byte[dis.readInt()];
					dis.read(signature);

					System.out.println("(Reply) PubKey " + i + ": " + Arrays.toString(pubKey));
					System.out.println("(Reply) Signature " + i + ": " + Arrays.toString(signature));

				}

				if (replyString.equalsIgnoreCase(stringReply)) {
					addReplica(currentView);
				}
			}

			client.close();


		} catch (IOException  e) {
			System.out.println("Exception creating JOIN request: " + e.getMessage());
		}
	}

	public static void serverReconfigRequest(String input, ObjectOutput out, TOMConfiguration replicaConf) throws IOException {
		if(input.equals(requestString)) {

			byte[] message = replyString.getBytes(StandardCharsets.UTF_8);

			byte[] pubKey = replicaConf.getPublicKey().getEncoded();

			byte[] signature = TOMUtil.signMessage(replicaConf.getPrivateKey(),
					message);


			out.writeInt(message.length);
			out.write(message);
			out.writeInt(pubKey.length);
			out.write(pubKey);
			out.writeInt(signature.length);
			out.write(signature);


			System.out.println("(Request) PubKey: " + Arrays.toString(pubKey));
			System.out.println("(Request) Signature: " + Arrays.toString(signature));
		}

	}

*//*

	public static void serverReconfigRequest(String input, ObjectOutput out, ReplicaContext replicaContext) throws IOException {
		if(input.equals(requestString)) {


			byte[] pubKey = replicaContext.getStaticConfiguration().getPublicKey().getEncoded();

			byte[] message;
			try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
				 ObjectOutput objOut = new ObjectOutputStream(byteOut);) {
				objOut.writeInt(pubKey.length);
				objOut.write(pubKey);
				objOut.writeUTF("YES");
				objOut.flush();

				 message = byteOut.toByteArray();

			}


			byte[] signature = TOMUtil.signMessage(replicaContext.getStaticConfiguration().getPrivateKey(),
					message);


			out.writeInt(message.length);
			out.write(message);
			out.writeInt(signature.length);
			out.write(signature);

		}

	}

*//*


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

		reconfigServices.addServer(this.id, newIP, newPort, replicaCertificates, currentView);
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


	}*/
}
