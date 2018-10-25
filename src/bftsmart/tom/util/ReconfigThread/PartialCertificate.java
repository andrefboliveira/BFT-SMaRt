package bftsmart.tom.util.ReconfigThread;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.PublicKey;

public class PartialCertificate implements Serializable{

	private PublicKey pubKey;
	private byte[] signature;

	public PartialCertificate(PublicKey pubKey, byte[] signature) {
		this.pubKey = pubKey;
		this.signature = signature;
	}

	public PublicKey getPubKey() {
		return pubKey;
	}

	public byte[] getSignature() {
		return signature;
	}

	private static final long serialVersionUID = -7538880909110693295L;


	private void writeObject(ObjectOutputStream oos) throws IOException {
		oos.defaultWriteObject();

//		oos.writeObject(pubKey);
//		oos.writeObject(signature);

	}

	private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
		ois.defaultReadObject();

//		PublicKey pubKey = (PublicKey) ois.readObject();
//		byte[] signature = (byte[]) ois.readObject();

	}
}
