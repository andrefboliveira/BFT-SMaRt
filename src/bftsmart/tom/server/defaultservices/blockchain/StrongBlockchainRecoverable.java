/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.tom.server.defaultservices.blockchain;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.consensus.messages.ConsensusMessage;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.reconfiguration.util.ReconfigThread.pojo.CoreCertificate;
import bftsmart.reconfiguration.util.ReconfigThread.pojo.ReplicaReconfigReply;
import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.statemanagement.ApplicationState;
import bftsmart.statemanagement.StateManager;
import bftsmart.tom.MessageContext;
import bftsmart.tom.ReplicaContext;
import bftsmart.tom.core.messages.ForwardedMessage;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.server.BatchExecutable;
import bftsmart.tom.server.Recoverable;
import bftsmart.tom.server.ServerJoiner;
import bftsmart.tom.server.defaultservices.CommandsInfo;
import bftsmart.tom.server.defaultservices.blockchain.logger.AsyncBatchLogger;
import bftsmart.tom.server.defaultservices.blockchain.logger.BufferBatchLogger;
import bftsmart.tom.server.defaultservices.blockchain.logger.ParallelBatchLogger;
import bftsmart.tom.server.defaultservices.blockchain.logger.VoidBatchLogger;
import bftsmart.tom.server.defaultservices.blockchain.strategy.BlockchainState;
import bftsmart.tom.server.defaultservices.blockchain.strategy.BlockchainStateManager;
import bftsmart.tom.util.BatchBuilder;
import bftsmart.tom.util.TOMUtil;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author joao
 */

public abstract class StrongBlockchainRecoverable implements Recoverable, BatchExecutable, ServerJoiner {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	public String batchDir;

	private TOMConfiguration config;
	private ServerViewController controller;
	private BlockchainStateManager stateManager;
	private ServerCommunicationSystem commSystem;

	private int session;
	private int commitSeq;
	private int timeoutSeq;
	private int requestID;

	private BatchLogger log;
	private LinkedList<TOMMessage> results;

	private int nextNumber;
	private int lastCheckpoint;
	private int lastReconfig;
	private byte[] lastBlockHash;

	private byte[] appState;
	private byte[] appStateHash;

	// private AsynchServiceProxy proxy;
	private Timer timer;

	private ReentrantLock timerLock = new ReentrantLock();
	private ReentrantLock mapLock = new ReentrantLock();
	private Condition gotCertificate = mapLock.newCondition();
	private Map<Integer, Map<Integer, byte[]>> certificates;
	private Map<Integer, Set<Integer>> timeouts;
	private int currentCommit;

	public enum commandType {
		TIMEOUT, JOIN, APP, NOOP
	}

	;

	public StrongBlockchainRecoverable() {

		nextNumber = 0;
		lastCheckpoint = -1;
		lastReconfig = -1;
		lastBlockHash = new byte[] { -1 };

		results = new LinkedList<>();

		currentCommit = -1;
		certificates = new HashMap<>();
		timeouts = new HashMap<>();
	}

	@Override
	public void setReplicaContext(ReplicaContext replicaContext) {

		try {

			config = replicaContext.getStaticConfiguration();
			controller = replicaContext.getSVController();

			commSystem = replicaContext.getServerCommunicationSystem();

			appState = getSnapshot();
			appStateHash = TOMUtil.computeHash(appState);

			Random rand = new Random(System.nanoTime());
			session = rand.nextInt();
			requestID = 0;
			commitSeq = 0;
			timeoutSeq = 0;

			// proxy = new AsynchServiceProxy(config.getProcessId());

			// batchDir =
			// config.getConfigHome().concat(System.getProperty("file.separator")) +
			batchDir = "files".concat(System.getProperty("file.separator"));

			initLog();

			log.startNewFile(nextNumber);

			// write genesis block
			byte[][] hashes = log.markEndTransactions();
			log.storeHeader(nextNumber, lastCheckpoint, lastReconfig, hashes[0], hashes[1], lastBlockHash);

			log.sync();

			lastBlockHash = computeBlockHash(nextNumber, lastCheckpoint, lastReconfig, hashes[0], hashes[1],
					lastBlockHash);

			nextNumber++;

			log.startNewFile(nextNumber);

		} catch (Exception ex) {

			throw new RuntimeException("Could not set replica context", ex);
		}
		getStateManager().askCurrentConsensusId();
	}

	@Override
    public ApplicationState getState(int cid, boolean sendState) {

        logger.info("CID requested: " + cid + ". Last checkpoint: " + lastCheckpoint + ". Last CID: " + log.getLastStoredCID());

        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        boolean hasCached = log.getFirstCachedCID() != -1 && log.getLastCachedCID() != -1;
        boolean hasState = cid >= lastCheckpoint && cid <= log.getLastStoredCID();

        CommandsInfo[] batches = null;

        int lastCID = -1;

        BlockchainState ret = new BlockchainState();

        if (hasState) {

            logger.info("Constructing ApplicationState up until CID " + cid);

            int size = cid - lastCheckpoint;

            if (size > 0 && hasCached) {

                CommandsInfo[] cached = log.getCached();
                batches = new CommandsInfo[size];

                for (int i = 0; i < size; i++)
                    batches[i] = cached[i];
            }
            lastCID = cid;

            ret = new BlockchainState(batches, lastCheckpoint, lastCID, (sendState ? appState : null),
                    appStateHash, config.getProcessId(), nextNumber, lastReconfig, lastBlockHash);
        }

        return ret;
    }

	@Override
    public int setState(ApplicationState recvState) {
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.

        int lastCID = -1;
        if (recvState instanceof BlockchainState) {

            BlockchainState state = (BlockchainState) recvState;

            int lastCheckpointCID = state.getLastCheckpointCID();
            lastCID = state.getLastCID();

            logger.info("I'm going to update myself from CID "
                    + lastCheckpointCID + " to CID " + lastCID);
            try {

                if (state.getSerializedState() != null) {
                    logger.info("The state is not null. Will install it");

                    nextNumber = state.getNextNumber();
                    lastCheckpoint = state.getLastCheckpointCID();
                    lastReconfig = state.getLastReconfig();
                    lastBlockHash = state.getLastBlockHash();
                    appState = state.getState();
                    appStateHash = state.getStateHash();

                    initLog();
                    log.setCached(state.getMessageBatches(), state.getLastCheckpointCID(), state.getLastCID());
                    installSnapshot(state.getSerializedState());
                }
                
                stateManager.fetchBlocks(nextNumber - 1);
                
                log.startNewFile(nextNumber);

                for (int cid = lastCheckpointCID + 1; cid <= lastCID; cid++) {

                    logger.debug("Processing and verifying batched requests for cid " + cid);
                    if (state.getMessageBatch(cid) == null) {
                        logger.warn("Consensus " + cid + " is null!");
                    }

                    CommandsInfo cmdInfo = state.getMessageBatch(cid);
                    byte[][] commands = cmdInfo.commands; // take a batch
                    MessageContext[] msgCtxs = cmdInfo.msgCtx;

                    executeBatch(config.getProcessId(), controller.getCurrentViewId(), commands, msgCtxs, msgCtxs[0].isNoOp(), false);
                    
                    /*if (commands == null || msgCtxs == null || msgCtxs[0].isNoOp()) {
                        continue;
                    }*/

                }

            } catch (Exception e) {
                logger.error("Failed to process and verify batched requests", e);
                if (e instanceof ArrayIndexOutOfBoundsException) {
                    logger.info("Last checkpoint, last consensus ID (CID): " + state.getLastCheckpointCID());
                    logger.info("Last CID: " + state.getLastCID());
                    logger.info("number of messages expected to be in the batch: " + (state.getLastCID() - state.getLastCheckpointCID() + 1));
                    logger.info("number of messages in the batch: " + state.getMessageBatches().length);
                }
            }

        }

        return lastCID;
    }

    @Override
    public StateManager getStateManager() {
        if (stateManager == null) {
            stateManager = new BlockchainStateManager(true, true); // might need to be other implementation
        }
        return stateManager;
    }

    @Override
    public void Op(int CID, byte[] requests, MessageContext msgCtx) {

        // Since we are logging entire batches, we do not use this
    }

    @Override
    public void noOp(int CID, byte[][] operations, MessageContext[] msgCtxs) {

        executeBatch(-1, -1, operations, msgCtxs, true, true);
    }

    @Override
    public TOMMessage[] executeBatch(int processID, int viewID, byte[][] operations, MessageContext[] msgCtxs) {

        return executeBatch(processID, viewID, operations, msgCtxs, false, true);
    }

    @Override
    public TOMMessage executeUnordered(int processID, int viewID, boolean isHashedReply, byte[] command, MessageContext msgCtx) {

        if (controller.isCurrentViewMember(msgCtx.getSender())) {

            ByteBuffer buff = ByteBuffer.wrap(command);

            int cid = buff.getInt();
            byte[] sig = new byte[buff.getInt()];
            buff.get(sig);

            if (currentCommit <= cid
                /*&& TOMUtil.verifySignature(controller.getStaticConf().getPublicKey(msgCtx.getSender()), lastBlockHash, sig*)*/) {

                //TODO: there are two bug that will cause the system to block if signature validation is performed. One is due to the fact
                // that hash headers are not deterministic due to the consensus proof contained in the context object. The other I think
                //it is a rece condition, but I don't know where yet.

                logger.debug("Received valid signature from {}: {}", msgCtx.getSender(), Base64.encodeBase64String(sig));

                mapLock.lock();

                Map<Integer, byte[]> signatures = certificates.get(cid);
                if (signatures == null) {

                    signatures = new HashMap<>();
                    certificates.put(cid, signatures);
                }

                signatures.put(msgCtx.getSender(), sig);

                logger.debug("got {} sigs for CID {}", signatures.size(), cid);

                if (currentCommit == cid && signatures.size() > controller.getQuorum()) {

                    logger.info("Signaling main thread");
                    gotCertificate.signalAll();
                }

                mapLock.unlock();

            }
            return null;
        } else {

            byte[] result = executeUnordered(command, msgCtx);

            if (isHashedReply) result = TOMUtil.computeHash(result);

            return getTOMMessage(processID, viewID, command, msgCtx, result);
        }
    }

    @Override
    public byte[] takeCheckpointHash(int cid) {
        return TOMUtil.computeHash(getSnapshot());
    }

    private TOMMessage[] executeBatch(int processID, int viewID, byte[][] operations, MessageContext[] msgCtxs, boolean noop, boolean fromConsensus) {

        int cid = msgCtxs[0].getConsensusId();
//        TOMMessage[] replies = new TOMMessage[0];
        boolean timeout = false;

        try {

            Map<commandType, List<Integer>> commandIndexes = new HashMap<commandType, List<Integer>>();
            commandIndexes.put(commandType.TIMEOUT, new ArrayList<Integer>());
            commandIndexes.put(commandType.JOIN, new ArrayList<Integer>());
            commandIndexes.put(commandType.APP, new ArrayList<Integer>());
            commandIndexes.put(commandType.NOOP, new ArrayList<Integer>());


            LinkedList<byte[]> transListApp = new LinkedList<byte[]>();
            LinkedList<MessageContext> ctxListApp = new LinkedList<MessageContext>();

            LinkedList<byte[]> joinRequestList = new LinkedList<byte[]>();
            LinkedList<MessageContext> ctxjoinRequestList = new LinkedList<MessageContext>();

            for (int i = 0; i < operations.length; i++) {

                if (controller.isCurrentViewMember(msgCtxs[i].getSender())) {

                    ByteBuffer buff = ByteBuffer.wrap(operations[i]);

                    int l = buff.getInt();
                    byte[] b = new byte[l];
                    buff.get(b);

                    if ((new String(b)).equals("TIMEOUT")) {

                        commandIndexes.get(commandType.TIMEOUT).add(i);

                        int n = buff.getInt();

                        if (n == nextNumber) {

                            logger.info("Got timeout for current block from replica {}!", msgCtxs[i].getSender());

                            Set<Integer> t = timeouts.get(nextNumber);
                            if (t == null) {

                                t = new HashSet<>();
                                timeouts.put(nextNumber, t);

                            }

                            t.add(msgCtxs[i].getSender());

                            if (t.size() >= (controller.getCurrentViewF() + 1)) {

                                timeout = true;
                            }
                        }
                    }

                } else if (!noop) {

                    ByteBuffer buff = ByteBuffer.wrap(operations[i]);

                    int l = buff.getInt();

                    if (l > 0) {
                        byte[] b = new byte[l];
                        buff.get(b);

                        String flag = "JOIN_REQUEST";

                        if (flag.equals((new String(b)))) {

                            commandIndexes.get(commandType.JOIN).add(i);


                            int messageSize = buff.getInt();
                            byte[] messageBytes = new byte[messageSize];
                            buff.get(messageBytes);

                            logger.debug("Request {} added to Join Requests list", msgCtxs[i].getOperationId());

                            joinRequestList.add(messageBytes);
                            ctxjoinRequestList.add(msgCtxs[i]);

                        } else {
                            commandIndexes.get(commandType.APP).add(i);

                            transListApp.add(operations[i]);
                            ctxListApp.add(msgCtxs[i]);

                            logger.debug("Request {} added to App Requests list", msgCtxs[i].getOperationId());

                        }
                    } else {
                        commandIndexes.get(commandType.APP).add(i);

                        transListApp.add(operations[i]);
                        ctxListApp.add(msgCtxs[i]);

                        logger.debug("Request {} added to App Requests list", msgCtxs[i].getOperationId());

                    }

                } else {
                    commandIndexes.get(commandType.NOOP).add(i);

                }

            }

            CommandContextPair fullCommandsAndContexts = orderCommands(operations, msgCtxs, commandIndexes, true);
            byte[][] fullCommands = fullCommandsAndContexts.getCommands();
            MessageContext[] fullContexts = fullCommandsAndContexts.getMsgCtxs();


            log.storeTransactions(cid, fullCommands, fullContexts);
//            log.storeTransactions(cid, operations, msgCtxs);

            if (transListApp.size() > 0 || joinRequestList.size() > 0) {

                byte[][] repliesApp = new byte[transListApp.size()][];

                if (transListApp.size() > 0) {
                    byte[][] transArrayApp = new byte[transListApp.size()][];
                    MessageContext[] ctxArrayApp = new MessageContext[ctxListApp.size()];

                    transListApp.toArray(transArrayApp);
                    ctxListApp.toArray(ctxArrayApp);

                    //byte[][] resultsApp = executeBatch(transArrayApp, ctxArrayApp);
                    repliesApp = appExecuteBatch(transArrayApp, ctxArrayApp, fromConsensus);
                }


                byte[][] repliesJoin = new byte[joinRequestList.size()][];


                if (joinRequestList.size() > 0) {

                    byte[][] commandsJoin = new byte[joinRequestList.size()][];
                    joinRequestList.toArray(commandsJoin);


                    MessageContext[] msgCtxsJoin = new MessageContext[ctxjoinRequestList.size()];
                    ctxjoinRequestList.toArray(msgCtxsJoin);

                    repliesJoin = executeJoinRequests(commandsJoin, msgCtxsJoin);

                }

                byte[][] fullResults = concatArray(repliesApp, repliesJoin);


                //TODO: this should be logged in another way, because the number transactions logged may not match the
                // number of results, because of the timeouts (that still need to be added to the block). This can render
                //audition impossible. Must implemented a way to match the results to their respective transactions.
                log.storeResults(fullResults);

                CommandContextPair executedCommandsAndContexts = orderCommands(operations, msgCtxs, commandIndexes, false);
                byte[][] executedCommands = executedCommandsAndContexts.getCommands();
                MessageContext[] executedContexts = executedCommandsAndContexts.getMsgCtxs();


                for (int i = 0; i < fullResults.length; i++) {

                    TOMMessage reply = getTOMMessage(processID, viewID, executedCommands[i], executedContexts[i], fullResults[i]);

                    this.results.add(reply);
                }

                if (timer != null) timer.cancel();
                timer = new Timer();

                timer.schedule(new TimerTask() {

                    @Override
                    public void run() {

                        logger.info("Timeout for block {}, asking to close it", nextNumber);

                        ByteBuffer buff = ByteBuffer.allocate("TIMEOUT".getBytes().length + (Integer.BYTES * 2));
                        buff.putInt("TIMEOUT".getBytes().length);
                        buff.put("TIMEOUT".getBytes());
                        buff.putInt(nextNumber);

                        sendTimeout(buff.array());
                    }

                }, config.getLogBatchTimeout());
            } else {

                log.storeResults(new byte[0][]);
            }

            return checkpoint(cid, timeout, fromConsensus);

//            return replies;
        } catch (IOException | NoSuchAlgorithmException | InterruptedException ex) {
            logger.error("Error while logging/executing batch for CID " + cid, ex);
            return new TOMMessage[0];
        } finally {
            if (mapLock.isHeldByCurrentThread()) mapLock.unlock();
        }
    }

    private CommandContextPair orderCommands(byte[][] commands, MessageContext[] msgCtxs, Map<commandType, List<Integer>> indexes, boolean allCommands) {
        byte[][] mergedCommands;
        MessageContext[] mergedContexts;


        int i = 0;

        if (allCommands) {
            int mergedSize = indexes.get(commandType.TIMEOUT).size() + indexes.get(commandType.NOOP).size() +
                    indexes.get(commandType.APP).size() + indexes.get(commandType.JOIN).size();

            mergedCommands = new byte[mergedSize][];
            mergedContexts = new MessageContext[mergedSize];

            for (Integer timeoutIndex : indexes.get(commandType.TIMEOUT)) {
                mergedCommands[i] = commands[timeoutIndex];
                mergedContexts[i] = msgCtxs[timeoutIndex];
                i++;
            }

            for (Integer noopIndex : indexes.get(commandType.NOOP)) {
                mergedCommands[i] = commands[noopIndex];
                mergedContexts[i] = msgCtxs[noopIndex];
                i++;
            }

        } else {
            int mergedSize = indexes.get(commandType.APP).size() + indexes.get(commandType.JOIN).size();

            mergedCommands = new byte[mergedSize][];
            mergedContexts = new MessageContext[mergedSize];

        }


        for (Integer appIndex : indexes.get(commandType.APP)) {
            mergedCommands[i] = commands[appIndex];
            mergedContexts[i] = msgCtxs[appIndex];

            i++;
        }

        for (Integer joinIndex : indexes.get(commandType.JOIN)) {
            mergedCommands[i] = commands[joinIndex];
            mergedContexts[i] = msgCtxs[joinIndex];
            i++;
        }

        return new CommandContextPair(mergedCommands, mergedContexts);
    }

    private class CommandContextPair {
        byte[][] commands;
        MessageContext[] msgCtxs;

        public CommandContextPair(byte[][] commands, MessageContext[] msgCtxs) {
            this.commands = commands;
            this.msgCtxs = msgCtxs;
        }

        public byte[][] getCommands() {
            return this.commands;
        }

        public MessageContext[] getMsgCtxs() {
            return this.msgCtxs;
        }
    }

    private byte[][] concatArray(byte[][]... arrays) {
        // Determine the length of the result array
        int totalLength = 0;
        for (int i = 0; i < arrays.length; i++) {
            totalLength += arrays[i].length;
        }

        // create the result array
        byte[][] result = new byte[totalLength][];

        // copy the source arrays into the result array
        int currentIndex = 0;
        for (int i = 0; i < arrays.length; i++) {
            System.arraycopy(arrays[i], 0, result, currentIndex, arrays[i].length);
            currentIndex += arrays[i].length;
        }

        return result;
    }

    private byte[][] executeJoinRequests(byte[][] commands, MessageContext[] msgCtxs) {
        byte[][] replies = new byte[commands.length][];

        for (int i = 0; i < commands.length; i++) {
            replies[i] = processEachJoinRequest(commands[i], msgCtxs[i]);
        }

        return replies;
    }

    private byte[] processEachJoinRequest(byte[] command, MessageContext msgCtx) {
        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(command);
             DataInputStream dis = new DataInputStream(byteIn)) {

            int joiningReplicaID = dis.readInt();

            int commandSize = dis.readInt();
            byte[] commandToExecute = new byte[commandSize];
            dis.read(commandToExecute);


            boolean acceptedRequest = appVerifyJoinRequest(commandToExecute);

            if (acceptedRequest) {
                return serversMakeCertificate(joiningReplicaID, acceptedRequest, commandToExecute, msgCtx.getTimestamp(), controller.getStaticConf());
            }

        } catch (IOException e) {
            logger.error("Exception creating JOIN request: " + e.getMessage());
        }
        return new byte[0];
    }

    private byte[] serversMakeCertificate(int joiningReplicaID, boolean acceptedRequest, byte[] input, long consensusTimestamp, TOMConfiguration executingReplicaConf) throws IOException {

        CoreCertificate certificateValues = new CoreCertificate(joiningReplicaID, acceptedRequest, input, consensusTimestamp, executingReplicaConf.getProcessId());

        byte[] signature;

        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(byteOut);) {

            certificateValues.serialize(dos);

            dos.flush();
            byteOut.flush();

            signature = TOMUtil.signMessage(executingReplicaConf.getPrivateKey(),
                    byteOut.toByteArray());
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(out);) {

            ReplicaReconfigReply reply = new ReplicaReconfigReply(certificateValues, signature);

            reply.serialize(dos);

            dos.flush();
            out.flush();
            return out.toByteArray();
        }
    }

    private TOMMessage[] checkpoint(int cid, boolean timeout, boolean fromConsensus) throws IOException, InterruptedException, NoSuchAlgorithmException {
        TOMMessage[] replies = new TOMMessage[0];

        boolean isCheckpoint = cid % config.getCheckpointPeriod() == 0;

        if (timeout || isCheckpoint || (cid % config.getLogBatchLimit() == 0)) {

            byte[][] hashes = log.markEndTransactions();

            if (isCheckpoint) {

                logger.info("Performing checkpoint at CID {}", cid);

                log.clearCached();
                lastCheckpoint = cid;

                appState = getSnapshot();
                appStateHash = TOMUtil.computeHash(appState);

                logger.info("Storing checkpoint at CID {}", cid);

                writeCheckpointToDisk(cid, appState);
            }

            log.storeHeader(nextNumber, lastCheckpoint, lastReconfig, hashes[0], hashes[1], lastBlockHash);

            lastBlockHash = computeBlockHash(nextNumber, lastCheckpoint, lastReconfig, hashes[0], hashes[1], lastBlockHash);
            nextNumber++;

            logger.info("Created new block with hash header " + Base64.encodeBase64String(lastBlockHash));

            replies = new TOMMessage[this.results.size()];

            this.results.toArray(replies);
            this.results.clear();

            //TODO: This is if clause just a quick fix to avoid the thread from getting permanently block with the state transfer.
            //It happens because the replica will never received the commit messages from the other replicas. I need to think of a
            //way to solve this
            if (fromConsensus) {

                logger.info("Executing COMMIT phase at CID {} for block number {}", cid, (nextNumber - 1));

                byte[] mySig = TOMUtil.signMessage(config.getPrivateKey(), lastBlockHash);

                ByteBuffer buff = ByteBuffer.allocate((Integer.BYTES * 2) + mySig.length);

                buff.putInt(cid);
                buff.putInt(mySig.length);
                buff.put(mySig);

                //int context = proxy.invokeAsynchRequest(buff.array(), null, TOMMessageType.UNORDERED_REQUEST);
                //proxy.cleanAsynchRequest(context);
                sendCommit(buff.array());

                mapLock.lock();

                certificates.remove(currentCommit);

                currentCommit = cid;

                Map<Integer, byte[]> signatures = certificates.get(cid);
                if (signatures == null) {

                    signatures = new HashMap<>();
                    certificates.put(cid, signatures);
                }

                while (!(signatures.size() > controller.getQuorum())) {

                    logger.debug("blocking main thread");
                    gotCertificate.await(200, TimeUnit.MILLISECONDS);
                    //gotCertificate.await();

                    //signatures = certificates.get(cid);
                }

                signatures = certificates.get(cid);

                Map<Integer, byte[]> copy = new HashMap<>();

                signatures.forEach((id, sig) -> {

                    copy.put(id, sig);
                });

                mapLock.unlock();

                log.storeCertificate(copy);
            }
            logger.info("Synching log at CID {} and Block {}", cid, (nextNumber - 1));

            log.sync();

            log.startNewFile(nextNumber);

            timeouts.remove(nextNumber - 1);
        }

        return replies;
    }

    private void sendCommit(byte[] payload) throws IOException {

        try {

            timerLock.lock();

            TOMMessage commitMsg = new TOMMessage(config.getProcessId(),
                    session, commitSeq, requestID, payload, controller.getCurrentViewId(), TOMMessageType.UNORDERED_REQUEST);

            byte[] data = serializeTOMMsg(commitMsg);

            commitMsg.serializedMessage = data;
            commitMsg.serializedMessageSignature = TOMUtil.signMessage(controller.getStaticConf().getPrivateKey(), data);
            commitMsg.signed = true;

            commSystem.send(controller.getCurrentViewAcceptors(),
                    new ForwardedMessage(this.controller.getStaticConf().getProcessId(), commitMsg));

            requestID++;
            commitSeq++;

        } finally {

            timerLock.unlock();
        }
    }

    private void sendTimeout(byte[] payload) {

        try {

            timerLock.lock();

            TOMMessage timeoutMsg = new TOMMessage(config.getProcessId(),
                    session, timeoutSeq, requestID, payload, controller.getCurrentViewId(), TOMMessageType.ORDERED_REQUEST);

            byte[] data = serializeTOMMsg(timeoutMsg);

            timeoutMsg.serializedMessage = data;

            if (config.getUseSignatures() == 1) {

                timeoutMsg.serializedMessageSignature = TOMUtil.signMessage(controller.getStaticConf().getPrivateKey(), data);
                timeoutMsg.signed = true;

            }

            commSystem.send(controller.getCurrentViewAcceptors(),
                    new ForwardedMessage(this.controller.getStaticConf().getProcessId(), timeoutMsg));

            requestID++;
            timeoutSeq++;

        } catch (IOException ex) {
            logger.error("Error while sending timeout message.", ex);
        } finally {

            timerLock.unlock();
        }

    }

    private byte[] serializeTOMMsg(TOMMessage msg) throws IOException {

        DataOutputStream dos = null;
        byte[] data = null;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        dos = new DataOutputStream(baos);
        msg.wExternal(dos);
        dos.flush();
        return baos.toByteArray();
    }

    private byte[] computeBlockHash(int number, int lastCheckpoint, int lastReconf, byte[] transHash, byte[] resultsHash, byte[] prevBlock) throws NoSuchAlgorithmException {

        ByteBuffer buff = ByteBuffer.allocate(Integer.BYTES * 6 + (prevBlock.length + transHash.length + resultsHash.length));

        buff.putInt(number);
        buff.putInt(lastCheckpoint);
        buff.putInt(lastReconf);

        buff.putInt(transHash.length);
        buff.put(transHash);

        buff.putInt(resultsHash.length);
        buff.put(resultsHash);

        buff.putInt(prevBlock.length);
        buff.put(prevBlock);

        MessageDigest md = TOMUtil.getHashEngine();

        return md.digest(buff.array());
    }

    private void initLog() throws FileNotFoundException, NoSuchAlgorithmException {

        if (config.getLogBatchType().equalsIgnoreCase("buffer")) {
            log = BufferBatchLogger.getInstance(config.getProcessId(), batchDir);
        } else if (config.getLogBatchType().equalsIgnoreCase("parallel")) {
            log = ParallelBatchLogger.getInstance(config.getProcessId(), batchDir);
        } else if (config.getLogBatchType().equalsIgnoreCase("async")) {
            log = AsyncBatchLogger.getInstance(config.getProcessId(), batchDir);
        } else {
            log = VoidBatchLogger.getInstance(config.getProcessId(), batchDir);
        }

    }

    public boolean verifyBatch(byte[][] commands, MessageContext[] msgCtxs) {

        //first off, re-create the batch received in the PROPOSE message of the consensus instance
        int totalMsgsSize = 0;
        byte[][] messages = new byte[commands.length][];
        byte[][] signatures = new byte[commands.length][];

        for (int i = 0; i < commands.length; i++) {

            TOMMessage msg = msgCtxs[i].recreateTOMMessage(commands[i]);
            messages[i] = msg.serializedMessage;
            signatures[i] = msg.serializedMessageSignature;
            totalMsgsSize += msg.serializedMessage.length;
        }

        BatchBuilder builder = new BatchBuilder(0);
        byte[] serializeddBatch = builder.createBatch(msgCtxs[0].getTimestamp(), msgCtxs[0].getNumOfNonces(), msgCtxs[0].getSeed(),
                commands.length, totalMsgsSize, true, messages, signatures);

        // now we can obtain the hash contained in the ACCEPT messages from the proposed value
        byte[] hashedBatch = TOMUtil.computeHash(serializeddBatch);

        //we are now ready to verify each message that comprises the proof
        int countValid = 0;
        int certificate = (2 * controller.getCurrentViewF()) + 1;

        HashSet<Integer> alreadyCounted = new HashSet<>();

        for (ConsensusMessage consMsg : msgCtxs[0].getProof()) {

            ConsensusMessage cm = new ConsensusMessage(consMsg.getType(), consMsg.getNumber(),
                    consMsg.getEpoch(), consMsg.getSender(), consMsg.getValue());

            cm.setCheckpointHash(consMsg.getCheckpointHash());

            ByteArrayOutputStream bOut = new ByteArrayOutputStream(248);
            try {
                new ObjectOutputStream(bOut).writeObject(cm);
            } catch (IOException ex) {
                logger.error("Could not serialize message", ex);
            }

            byte[] data = bOut.toByteArray();

            PublicKey pubKey = config.getPublicKey(consMsg.getSender());

            byte[] signature = (byte[]) consMsg.getProof();

            if (Arrays.equals(consMsg.getValue(), hashedBatch) &&
                    TOMUtil.verifySignature(pubKey, data, signature) && !alreadyCounted.contains(consMsg.getSender())) {

                alreadyCounted.add(consMsg.getSender());
                countValid++;
            } else {
                logger.error("Invalid signature in message from " + consMsg.getSender());
            }
        }

        boolean ret = countValid >= certificate;
        logger.info("Proof for CID {} is {} ({} valid messages, needed {})",
                msgCtxs[0].getConsensusId(), (ret ? "valid" : "invalid"), countValid, certificate);
        return ret;
    }

    private void writeCheckpointToDisk(int cid, byte[] checkpoint) throws IOException {

        String checkpointPath = batchDir + "checkpoint." + config.getProcessId() + "." + String.valueOf(cid) + ".log";

        RandomAccessFile log = new RandomAccessFile(checkpointPath, "rwd");

        log.write(checkpoint);

        log.close();
    }

    @Override
    public byte[][] executeBatch(byte[][] operations, MessageContext[] msgCtxs) {

        //not used
        return null;
    }

    @Override
    public byte[] executeUnordered(byte[] command, MessageContext msgCtx) {

        return appExecuteUnordered(command, msgCtx);
    }

    /**
     * Given a snapshot received from the state transfer protocol, install it
     *
     * @param state The serialized snapshot
     */
    public abstract void installSnapshot(byte[] state);

    /**
     * Returns a serialized snapshot of the application state
     *
     * @return A serialized snapshot of the application state
     */
    public abstract byte[] getSnapshot();

    /**
     * Execute a batch of ordered requests
     *
     * @param commands      The batch of requests
     * @param msgCtxs       The context associated to each request
     * @param fromConsensus true if the request arrived from a consensus execution, false if it arrives from the state transfer protocol
     * @return the respective replies for each request
     */
    public abstract byte[][] appExecuteBatch(byte[][] commands, MessageContext[] msgCtxs, boolean fromConsensus);

    /**
     * Execute an unordered request
     *
     * @param command The unordered request
     * @param msgCtx  The context associated to the request
     * @return the reply for the request issued by the client
     */
    public abstract byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx);


}
