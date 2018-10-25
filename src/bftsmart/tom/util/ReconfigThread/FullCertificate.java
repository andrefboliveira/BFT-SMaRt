package bftsmart.tom.util.ReconfigThread;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;

public class FullCertificate implements Serializable {
	private String message;
	private List<PartialCertificate> replicaCertificates;

	public FullCertificate(String message, List<PartialCertificate> replicaCertificates) {
		this.message = message;
		this.replicaCertificates = replicaCertificates;
	}

	public String getMessage() {
		return message;
	}

	public List<PartialCertificate> getReplicaCertificates() {
		return replicaCertificates;
	}

	private static final long serialVersionUID = 8423132992633434574L;
	private void writeObject(ObjectOutputStream oos) throws IOException {
		oos.defaultWriteObject();

	}

	private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
		ois.defaultReadObject();
	}

}
