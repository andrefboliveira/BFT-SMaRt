package bftsmart.reconfiguration.util.ReconfigThread.pojo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FullCertificate implements Serializable {
	private int toReconfigureReplicaID;
	private boolean acceptedRequest;
	private byte[] inputProof;
	private long consensusTimestamp;
	private List<PartialCertificate> replicaCertificates;


	public FullCertificate(int toReconfigureReplicaID, boolean acceptedRequest, byte[] inputProof, long consensusTimestamp, List<PartialCertificate> replicaCertificates) {
		this.toReconfigureReplicaID = toReconfigureReplicaID;
		this.acceptedRequest = acceptedRequest;
		this.inputProof = inputProof;
		this.consensusTimestamp = consensusTimestamp;
		this.replicaCertificates = replicaCertificates;

	}

	public int getToReconfigureReplicaID() {
		return toReconfigureReplicaID;
	}

	public boolean isAcceptedRequest() {
		return acceptedRequest;
	}

	public byte[] getInputProof() {
		return inputProof;
	}

	public long getConsensusTimestamp() {
		return consensusTimestamp;
	}

	public List<PartialCertificate> getReplicaCertificates() {
		return replicaCertificates;
	}

	private static final long serialVersionUID = -4518009693757393043L;

	public void serialize(DataOutputStream dos) throws IOException {

		dos.writeInt(this.toReconfigureReplicaID);
		dos.writeBoolean(this.acceptedRequest);

		dos.writeInt(this.inputProof.length);
		dos.write(this.inputProof);

		dos.writeLong(this.consensusTimestamp);

		dos.writeInt(this.replicaCertificates.size());

		for (PartialCertificate replicaCertificate : this.replicaCertificates) {
			replicaCertificate.serialize(dos);
		}

		dos.flush();

	}

	public static FullCertificate desSerialize(DataInputStream dis) throws IOException {
		int toReconfigureReplicaID = dis.readInt();

		boolean acceptedRequest = dis.readBoolean();

		int proof_length = dis.readInt();
		byte[] proof = new byte[proof_length];
		dis.read(proof);

		long consensusTimestamp = dis.readLong();

		List<PartialCertificate> replicaCertifList = new ArrayList<PartialCertificate>();
		int numberOfCertificates = dis.readInt();
		for (int i = 0; i < numberOfCertificates; i++) {
			PartialCertificate certificate = PartialCertificate.desSerialize(dis);
			replicaCertifList.add(certificate);
		}

		return new FullCertificate(toReconfigureReplicaID, acceptedRequest, proof, consensusTimestamp, replicaCertifList);

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
