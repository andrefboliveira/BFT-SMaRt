package bftsmart.reconfiguration.util.ReconfigThread.pojo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;

public class CoreCertificate implements Serializable {


	private int toReconfigureReplicaID;
	private boolean acceptedRequest;
	private byte[] inputProof;
	private long consensusTimestamp;
	private int executingReplicaID;

	public CoreCertificate(int toReconfigureReplicaID, boolean acceptedRequest, byte[] inputProof, long consensusTimestamp, int executingReplicaID) {
		this.toReconfigureReplicaID = toReconfigureReplicaID;
		this.acceptedRequest = acceptedRequest;
		this.inputProof = inputProof;
		this.consensusTimestamp = consensusTimestamp;
		this.executingReplicaID = executingReplicaID;
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

	public int getExecutingReplicaID() {
		return executingReplicaID;
	}

	private static final long serialVersionUID = 6515433116046450453L;

	public void serialize(DataOutputStream dos) throws IOException {

		dos.writeInt(this.toReconfigureReplicaID);
		dos.writeBoolean(this.acceptedRequest);

		dos.writeInt(this.inputProof.length);
		dos.write(this.inputProof);

		dos.writeLong(this.consensusTimestamp);
		dos.writeInt(this.executingReplicaID);

		dos.flush();

	}

	public static CoreCertificate desSerialize(DataInputStream dis) throws IOException {
		int toReconfigureReplicaID = dis.readInt();

		boolean acceptedRequest = dis.readBoolean();

		int proof_length = dis.readInt();

		byte[] proof = new byte[proof_length];
		dis.read(proof);

		long consensusTimestamp = dis.readLong();
		int executingReplicaID = dis.readInt();

		return new CoreCertificate(toReconfigureReplicaID, acceptedRequest, proof, consensusTimestamp, executingReplicaID);

	}

	public static CoreCertificate generateCoreCertificate(FullCertificate fullCertificate, PartialCertificate replicaCertificate) {

		return new CoreCertificate(fullCertificate.getToReconfigureReplicaID(),
				fullCertificate.isAcceptedRequest(),
				fullCertificate.getInputProof(),
				fullCertificate.getConsensusTimestamp(),
				replicaCertificate.getSigningReplicaID());

	}

	@Override
	public String toString() {
		return "CoreCertificate{" +
				"toReconfigureReplicaID=" + toReconfigureReplicaID +
				", acceptedRequest=" + acceptedRequest +
				", inputProof=" + Arrays.toString(inputProof) +
				", consensusTimestamp=" + consensusTimestamp +
				", executingReplicaID=" + executingReplicaID +
				'}';
	}

	public static boolean compareCertificates(CoreCertificate c1, CoreCertificate c2) {
		if (c1 == c2) return true;
		if (c1 == null || c2 == null) return false;

		if (c1.getToReconfigureReplicaID() != c2.getToReconfigureReplicaID()) return false;
		if (c1.isAcceptedRequest() != c2.isAcceptedRequest()) return false;
		if (c1.getConsensusTimestamp() != c2.getConsensusTimestamp()) return false;
		return Arrays.equals(c1.getInputProof(), c2.getInputProof());
	}


	@Override
	public int hashCode() {
		int result = toReconfigureReplicaID;
		result = 31 * result + (acceptedRequest ? 1 : 0);
		result = 31 * result + Arrays.hashCode(inputProof);
		result = 31 * result + (int) (consensusTimestamp ^ (consensusTimestamp >>> 32));
		return result;
	}

	/*
	public static CoreCertificate generateCoreCertificate(FullCertificate fullCertificate, int replicaCertificateIndex){

		return new CoreCertificate(fullCertificate.getToReconfigureReplicaID(),
				fullCertificate.isAcceptedRequest(),
				fullCertificate.getInputProof(),
				fullCertificate.getConsensusTimestamp(),
				fullCertificate.getReplicaCertificates().get(replicaCertificateIndex).getSigningReplicaID());

	}

	public static List<CoreCertificate> generateAllCoreCertificates(FullCertificate fullCertificate){
		List<CoreCertificate> coreCertificates = new ArrayList<CoreCertificate>();

		int toReconfigureReplicaID = fullCertificate.getToReconfigureReplicaID();
		boolean acceptedRequest = fullCertificate.isAcceptedRequest();
		byte[] inputProof = fullCertificate.getInputProof();
		long consensusTimestamp = fullCertificate.getConsensusTimestamp();

		for (PartialCertificate replicaCertificate : fullCertificate.getReplicaCertificates()) {
			coreCertificates.add(new CoreCertificate(toReconfigureReplicaID,
					acceptedRequest,
					inputProof,
					consensusTimestamp,
					replicaCertificate.getSigningReplicaID()));
		}


		return coreCertificates;

	}
*/


/*	private void writeObject(ObjectOutputStream oos) throws IOException {
		oos.defaultWriteObject();
	}

	private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
		ois.defaultReadObject();

	}*/

}
