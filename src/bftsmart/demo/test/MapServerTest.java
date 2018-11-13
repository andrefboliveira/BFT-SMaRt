package bftsmart.demo.test;

import bftsmart.reconfiguration.util.ReconfigThread.JoinThread;
import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultRecoverable;

import java.io.*;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MapServerTest<K, V> extends DefaultRecoverable {

	private final ServiceReplica replica;
	//	private final TOMConfiguration config;
	private Map<K, V> replicaMap;
	private Logger logger;

	public MapServerTest(int id) {
		replicaMap = new TreeMap<>();
		logger = Logger.getLogger(MapServerTest.class.getName());
//		config = new TOMConfiguration(id, null);
		replica = new ServiceReplica(id, this, this);
	}

	public static void main(String[] args) {
		if (args.length < 1) {
			System.out.println("Usage: demo.map.MapServer <server id>");
			System.exit(-1);
		}
		new MapServerTest<String, String>(Integer.parseInt(args[0]));
	}

	@Override
	public byte[][] appExecuteBatch(byte[][] commands, MessageContext[] msgCtxs, boolean fromConsensus) {

		byte[][] replies = new byte[commands.length][];

		for (int i = 0; i < commands.length; i++) {

			replies[i] = appExecuteSingle(commands[i], msgCtxs[i], fromConsensus);

		}

		return replies;
	}

	@SuppressWarnings("unchecked")
	public byte[] appExecuteSingle(byte[] command, MessageContext msgCtx, boolean fromConsensus) {
		byte[] reply = null;
		K key = null;
		V value = null;
		boolean hasReply = false;
		try (ByteArrayInputStream byteIn = new ByteArrayInputStream(command);
		     ObjectInput objIn = new ObjectInputStream(byteIn);
		     ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		     ObjectOutput objOut = new ObjectOutputStream(byteOut);) {
			MapRequestTypeTest reqType = (MapRequestTypeTest) objIn.readObject();
			switch (reqType) {
				case PUT:
					key = (K) objIn.readObject();
					value = (V) objIn.readObject();

					V oldValue = replicaMap.put(key, value);
					if (oldValue != null) {
						objOut.writeObject(oldValue);
						hasReply = true;
					}
					break;
				case GET:
					key = (K) objIn.readObject();
					value = replicaMap.get(key);
					if (value != null) {
						objOut.writeObject(value);
						hasReply = true;
					}
					break;
				case REMOVE:
					key = (K) objIn.readObject();
					value = replicaMap.remove(key);
					if (value != null) {
						objOut.writeObject(value);
						hasReply = true;
					}
					break;
				case SIZE:
					int size = replicaMap.size();
					objOut.writeInt(size);
					hasReply = true;
					break;
				case KEYSET:
					keySet(objOut);
					hasReply = true;
					break;
				case RECONFIG_ADD:
					if (fromConsensus) {
						String input = (String) objIn.readUTF();
						int joiningReplicaID = objIn.readInt();
						JoinThread.serverReconfigRequest(input, objOut, joiningReplicaID, msgCtx.getTimestamp(), replica.getReplicaContext().getStaticConfiguration());
						hasReply = true;
					} else {
						hasReply = false;

					}

					break;

			}
			if (hasReply) {
				objOut.flush();
				byteOut.flush();
				reply = byteOut.toByteArray();
			} else {
				reply = new byte[0];
			}

		} catch (IOException | ClassNotFoundException e) {
			logger.log(Level.SEVERE, "Ocurred during map operation execution", e);
		}
		return reply;
	}


	@SuppressWarnings("unchecked")
	@Override
	public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {
		byte[] reply = null;
		K key = null;
		V value = null;
		boolean hasReply = false;

		try (ByteArrayInputStream byteIn = new ByteArrayInputStream(command);
		     ObjectInput objIn = new ObjectInputStream(byteIn);
		     ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		     ObjectOutput objOut = new ObjectOutputStream(byteOut);) {
			MapRequestTypeTest reqType = (MapRequestTypeTest) objIn.readObject();
			switch (reqType) {
				case GET:
					key = (K) objIn.readObject();
					value = replicaMap.get(key);
					if (value != null) {
						objOut.writeObject(value);
						hasReply = true;
					}
					break;
				case SIZE:
					int size = replicaMap.size();
					objOut.writeInt(size);
					hasReply = true;
					break;
				case KEYSET:
					keySet(objOut);
					hasReply = true;
					break;
				default:
					logger.log(Level.WARNING, "in appExecuteUnordered only read operations are supported");
			}
			if (hasReply) {
				objOut.flush();
				byteOut.flush();
				reply = byteOut.toByteArray();
			} else {
				reply = new byte[0];
			}
		} catch (IOException | ClassNotFoundException e) {
			logger.log(Level.SEVERE, "Ocurred during map operation execution", e);
		}

		return reply;
	}

	private void keySet(ObjectOutput out) throws IOException, ClassNotFoundException {
		Set<K> keySet = replicaMap.keySet();
		int size = replicaMap.size();
		out.writeInt(size);
		for (K key : keySet)
			out.writeObject(key);
	}


	@Override
	public byte[] getSnapshot() {
		try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		     ObjectOutput objOut = new ObjectOutputStream(byteOut)) {
			objOut.writeObject(replicaMap);
			return byteOut.toByteArray();
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Error while taking snapshot", e);
		}
		return new byte[0];
	}


	@SuppressWarnings("unchecked")
	@Override
	public void installSnapshot(byte[] state) {
		try (ByteArrayInputStream byteIn = new ByteArrayInputStream(state);
		     ObjectInput objIn = new ObjectInputStream(byteIn)) {
			replicaMap = (Map<K, V>) objIn.readObject();
		} catch (IOException | ClassNotFoundException e) {
			logger.log(Level.SEVERE, "Error while installing snapshot", e);
		}
	}
}