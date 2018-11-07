package bftsmart.reconfiguration.util.ReconfigThread.pojo;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class CoreCertificate implements Serializable {


	private int toReconfigureReplicaID;
	private long consensusTimestamp;
	private int executingReplicaID;
	private String receivedMessage;

	public CoreCertificate(int toReconfigureReplicaID, long consensusTimestamp, String receivedMessage, int executingReplicaID) {
		this.toReconfigureReplicaID = toReconfigureReplicaID;
		this.consensusTimestamp = consensusTimestamp;
		this.executingReplicaID = executingReplicaID;
		this.receivedMessage = receivedMessage;
	}


	public int getToReconfigureReplicaID() {
		return toReconfigureReplicaID;
	}

	public long getConsensusTimestamp() {
		return consensusTimestamp;
	}

	public String getReceivedMessage() {
		return receivedMessage;
	}

	public int getExecutingReplicaID() {
		return executingReplicaID;
	}

	private static final long serialVersionUID = 6515433116046450453L;

	public void serialize(ObjectOutputStream objOut) throws IOException {

		objOut.writeInt(this.toReconfigureReplicaID);
		objOut.writeLong(this.consensusTimestamp);
		objOut.writeInt(this.executingReplicaID);
		objOut.writeUTF(this.receivedMessage);

		objOut.flush();

	}



	private void writeObject(ObjectOutputStream oos) throws IOException {
		oos.defaultWriteObject();
	}

	private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
		ois.defaultReadObject();

	}

}
