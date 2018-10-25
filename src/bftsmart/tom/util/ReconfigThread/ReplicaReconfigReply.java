package bftsmart.tom.util.ReconfigThread;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class ReplicaReconfigReply implements Serializable {

	private String message;
	private PartialCertificate partialCertificate;

	public ReplicaReconfigReply(String message, PartialCertificate partialCertificate) {
		this.message = message;
		this.partialCertificate = partialCertificate;
	}

	public String getMessage() {
		return message;
	}

	public PartialCertificate getPartialCertificate() {
		return partialCertificate;
	}

	private static final long serialVersionUID = 8753106719081595831L;

	private void writeObject(ObjectOutputStream oos) throws IOException {
		oos.defaultWriteObject();

	}

	private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
		ois.defaultReadObject();
	}

}
