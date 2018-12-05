package bftsmart.reconfiguration.util.ReconfigThread.pojo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;

public class PartialCertificate implements Serializable {

	private int signingReplicaID;
	private byte[] signature;

	public PartialCertificate(int signingReplicaID, byte[] signature) {
		this.signingReplicaID = signingReplicaID;
		this.signature = signature;
	}

	public int getSigningReplicaID() {
		return signingReplicaID;
	}

	public byte[] getSignature() {
		return signature;
	}

	private static final long serialVersionUID = 6992126703443504960L;

	public void serialize(DataOutputStream dos) throws IOException {

		dos.writeInt(this.signingReplicaID);

		dos.writeInt(this.signature.length);
		dos.write(this.signature);

		dos.flush();

	}

	public static PartialCertificate desSerialize(DataInputStream dis) throws IOException {
		int signingReplicaID = dis.readInt();

		int signature_length = dis.readInt();

		byte[] signature = new byte[signature_length];
		dis.read(signature);

		return new PartialCertificate(signingReplicaID, signature);

	}


	/*
	private void writeObject(ObjectOutputStream oos) throws IOException {
		oos.defaultWriteObject();

	}

	private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
		ois.defaultReadObject();

	}

	*/
}
