package bftsmart.reconfiguration.util.ReconfigThread.pojo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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

	public void serialize(DataOutputStream dos) throws IOException {
		this.certificateValues.serialize(dos);

		dos.writeInt(this.signature.length);
		dos.write(this.signature);

		dos.flush();

	}


	public static ReplicaReconfigReply desSerialize(DataInputStream dis) throws IOException {
		CoreCertificate certificate = CoreCertificate.desSerialize(dis);

		int signature_length = dis.readInt();
		byte[] signature = new byte[signature_length];
		dis.read(signature);

		return new ReplicaReconfigReply(certificate, signature);

	}


/*	private void writeObject(ObjectOutputStream oos) throws IOException {
		oos.defaultWriteObject();

	}

	private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
		ois.defaultReadObject();
	}*/

}
