package bftsmart.tom.util.ReconfigThread;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class CoreCertificate implements Serializable {


	private int joiningReplicaID;
	private long consensusTimestamp;
	private int executingReplicaID;
	private String receivedMessage;

	public CoreCertificate(int joiningReplicaID, long consensusTimestamp, String receivedMessage, int executingReplicaID) {
		this.joiningReplicaID = joiningReplicaID;
		this.consensusTimestamp = consensusTimestamp;
		this.executingReplicaID = executingReplicaID;
		this.receivedMessage = receivedMessage;
	}


	public int getJoiningReplicaID() {
		return joiningReplicaID;
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

	void serialize(ObjectOutputStream objOut) throws IOException {

		objOut.writeInt(this.joiningReplicaID);
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
