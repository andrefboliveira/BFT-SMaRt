package bftsmart.reconfiguration.util.ReconfigThread.pojo;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;

public class FullCertificate implements Serializable {
	private String receivedMessage;
	private int toReconfigureReplicaID;
	private long consensusTimestamp;

	private List<PartialCertificate> replicaCertificates;


	public FullCertificate(String receivedMessage, int toReconfigureReplicaID, long consensusTimestamp, List<PartialCertificate> replicaCertificates) {
		this.receivedMessage = receivedMessage;
		this.toReconfigureReplicaID = toReconfigureReplicaID;
		this.consensusTimestamp = consensusTimestamp;
		this.replicaCertificates = replicaCertificates;
	}

	public String getReceivedMessage() {
		return receivedMessage;
	}

	public int getToReconfigureReplicaID() {
		return toReconfigureReplicaID;
	}

	public long getConsensusTimestamp() {
		return consensusTimestamp;
	}

	public List<PartialCertificate> getReplicaCertificates() {
		return replicaCertificates;
	}

	private static final long serialVersionUID = -4518009693757393043L;


	private void writeObject(ObjectOutputStream oos) throws IOException {
		oos.defaultWriteObject();

	}

	private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
		ois.defaultReadObject();
	}

}
