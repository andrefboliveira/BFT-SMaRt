package bftsmart.tom.util.ReconfigThread;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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


	private void writeObject(ObjectOutputStream oos) throws IOException {
		oos.defaultWriteObject();

	}

	private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
		ois.defaultReadObject();

	}
}
