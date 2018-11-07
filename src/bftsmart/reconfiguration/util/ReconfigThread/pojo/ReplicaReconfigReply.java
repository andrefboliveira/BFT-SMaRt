package bftsmart.reconfiguration.util.ReconfigThread.pojo;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class ReplicaReconfigReply implements Serializable {


	private final CoreCertificate certificateValues;
	private final byte[] signature;

	public ReplicaReconfigReply(CoreCertificate certificateValues, byte[] signature) {
		this.certificateValues = certificateValues;
		this.signature = signature;

	}

	public CoreCertificate getCertificateValues() {
		return certificateValues;
	}

	public byte[] getSignature() {
		return signature;
	}

	private static final long serialVersionUID = -155287492936914604L;

	private void writeObject(ObjectOutputStream oos) throws IOException {
		oos.defaultWriteObject();

	}

	private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
		ois.defaultReadObject();
	}

}
