/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.tom.server.defaultservices.blockchain;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.tom.server.defaultservices.blockchain.logger.ParallelBatchLogger;
import bftsmart.tom.server.defaultservices.blockchain.logger.BufferBatchLogger;
import bftsmart.consensus.messages.ConsensusMessage;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.reconfiguration.util.ReconfigThread.pojo.CoreCertificate;
import bftsmart.reconfiguration.util.ReconfigThread.pojo.ReplicaReconfigReply;
import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.statemanagement.ApplicationState;
import bftsmart.statemanagement.SMMessage;
import bftsmart.statemanagement.StateManager;
import bftsmart.statemanagement.standard.StandardStateManager;
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
import bftsmart.tom.server.defaultservices.blockchain.logger.VoidBatchLogger;
import bftsmart.tom.util.BatchBuilder;
import bftsmart.tom.util.TOMUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author joao -- merged with andre!
 */
public abstract class StrongBlockchainRecoverable implements Recoverable, BatchExecutable, ServerJoiner {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    public String batchDir;

    private TOMConfiguration config;
    private ServerViewController controller;
    private StateManager stateManager;
    private ServerCommunicationSystem commSystem;

    private int session;
    private int commitSeq;
    private int timeoutSeq;
    private int requestID;

    private byte[] appState;
    private byte[] appStateHash;

    private BatchLogger log;
    private LinkedList<TOMMessage> results;

    private int nextNumber;
    private int lastCheckpoint;
    private int lastReconfig;
    private byte[] lastBlockHash;

    //private AsynchServiceProxy proxy;
    private Timer timer;

    private ReentrantLock timerLock = new ReentrantLock();
    private ReentrantLock mapLock = new ReentrantLock();
    private Condition gotCertificate = mapLock.newCondition();
    private Map<Integer, Map<Integer, byte[]>> certificates;
    private Map<Integer, Set<Integer>> timeouts;
    private int currentCommit;

    private TOMMessageGenerator TOMgen;

    private Set<SMMessage> stateMsgs;

    public enum commandType {
        APP, JOIN
    }

    public StrongBlockchainRecoverable() {

        nextNumber = 0;
        lastCheckpoint = -1;
        lastReconfig = -1;
        lastBlockHash = new byte[]{-1};

        results = new LinkedList<>();

        currentCommit = -1;
        certificates = new HashMap<>();
        timeouts = new HashMap<>();

        stateMsgs = new HashSet<>();
    }

    @Override
    public void setReplicaContext(ReplicaContext replicaContext) {

        try {

            config = replicaContext.getStaticConfiguration();
            controller = replicaContext.getSVController();

            commSystem = replicaContext.getServerCommunicationSystem();

            appState = getSnapshot();
            appStateHash = TOMUtil.computeHash(appState);

            TOMgen = new TOMMessageGenerator(controller);

            Random rand = new Random(System.nanoTime());
            session = rand.nextInt();
            requestID = 0;
            commitSeq = 0;
            timeoutSeq = 0;

            //proxy = new AsynchServiceProxy(config.getProcessId());
            //batchDir = config.getConfigHome().concat(System.getProperty("file.separator")) +
            batchDir = "files".concat(System.getProperty("file.separator"));

            if (config.getLogBatchType().equalsIgnoreCase("buffer")) {
                log = BufferBatchLogger.getInstance(config.getProcessId(), batchDir);
            } else if (config.getLogBatchType().equalsIgnoreCase("parallel")) {
                log = ParallelBatchLogger.getInstance(config.getProcessId(), batchDir);
            } else if (config.getLogBatchType().equalsIgnoreCase("async")) {
                log = AsyncBatchLogger.getInstance(config.getProcessId(), batchDir);
            } else {
                log = VoidBatchLogger.getInstance(config.getProcessId(), batchDir);
            }

            log.startNewFile(-1, config.getCheckpointPeriod());

            //write genesis block
            byte[][] hashes = log.markEndTransactions();
            log.storeHeader(nextNumber, lastCheckpoint, lastReconfig, hashes[0], hashes[1], lastBlockHash);

            if (config.isToWriteSyncLog()) {
                log.sync();
            }

            lastBlockHash = computeBlockHash(nextNumber, lastCheckpoint, lastReconfig, hashes[0], hashes[1], lastBlockHash);

            nextNumber++;

        } catch (Exception ex) {

            throw new RuntimeException("Could not set replica context", ex);
        }

        ((BlockchainStateManager) getStateManager()).setTOMgen(TOMgen);
        getStateManager().askCurrentConsensusId();

    }

    @Override
    public ApplicationState getState(int cid, boolean sendState) {
        logger.info("CID requested: " + cid + ". Last checkpoint: " + lastCheckpoint + ". Last CID: "
                + log.getLastStoredCID());

        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        //boolean hasCached = log.getFirstCachedCID() != -1 && log.getLastCachedCID() != -1;
        // boolean hasState = cid >= lastCheckpoint && cid <= log.getLastStoredCID();
        CommandsInfo[] batches = null;

        int lastCID = -1;

        BlockchainState ret = new BlockchainState();

        logger.info("Constructing ApplicationState up until CID " + cid);

        lastCID = cid;

        //private int session;
        //private int commitSeq;
        //private int timeoutSeq;
        //private int requestID;
        ret = new BlockchainState(log.getCachedBatches(), log.getCachedResults(), log.getCachedHeaders(),
                log.getCachedCertificates(), lastCheckpoint, lastCID, (sendState ? appState : null), appStateHash,
                config.getProcessId(), nextNumber, lastReconfig, lastBlockHash, session, commitSeq, timeoutSeq, requestID);

        return ret;
    }

    @Override
    public int setState(ApplicationState recvState) {
        int lastCID = -1;
        if (recvState instanceof BlockchainState) {

            BlockchainState state = (BlockchainState) recvState;

            this.session = state.getSession();
            this.requestID = state.getRequestID();
            this.timeoutSeq = state.getTimeoutSeq();
            this.commitSeq = state.getCommitSeq();

            int lastCheckpointCID = state.getLastCheckpointCID();
            lastCID = state.getLastCID();

            logger.info("I'm going to update myself from CID " + lastCheckpointCID + " to CID " + lastCID);
            try {

                if (state.getSerializedState() != null) {
                    logger.info("The state is not null. Will install it");

                    nextNumber = state.getNextNumber();
                    lastCheckpoint = state.getLastCheckpointCID();
                    lastReconfig = state.getLastReconfig();
                    lastBlockHash = state.getLastBlockHash();
                    appState = state.getState();
                    appStateHash = state.getStateHash();

                    batchDir = "files".concat(System.getProperty("file.separator"));
                    if (config.getLogBatchType().equalsIgnoreCase("buffer")) {
                        log = BufferBatchLogger.getInstance(config.getProcessId(), batchDir);
                    } else if (config.getLogBatchType().equalsIgnoreCase("parallel")) {
                        log = ParallelBatchLogger.getInstance(config.getProcessId(), batchDir);
                    } else if (config.getLogBatchType().equalsIgnoreCase("async")) {
                        log = AsyncBatchLogger.getInstance(config.getProcessId(), batchDir);
                    } else {
                        log = VoidBatchLogger.getInstance(config.getProcessId(), batchDir);
                    }

                    log.setCached(state.getLastCheckpointCID(), state.getLastCID(), state.getBatches(),
                            state.getResults(), state.getHeaders(), state.getCertificates());
                    installSnapshot(state.getSerializedState());
                }

                writeCheckpointToDisk(lastCheckpoint, appState);

                //stateManager.fetchBlocks(lastCID);
                if (lastCID % config.getCheckpointPeriod() == 0) {
                    log.startFileFromCache(config.getCheckpointPeriod());
                } else {
                    log.openFile(lastCheckpoint, config.getCheckpointPeriod());
                }

                for (int cid = lastCheckpointCID + 1; cid <= lastCID; cid++) {

                    logger.debug("Processing and verifying batched requests for cid " + cid);

                    CommandsInfo cmdInfo = state.getBatches().get(cid);
                    byte[][] commands = cmdInfo.commands; // take a batch
                    MessageContext[] msgCtxs = cmdInfo.msgCtx;

                    //executeBatch(config.getProcessId(), controller.getCurrentViewId(), commands, msgCtxs, msgCtxs[0].isNoOp(), false);
                    if (commands == null || msgCtxs == null || msgCtxs[0].isNoOp()) {
                        continue;
                    }

                    LinkedList<byte[]> transListApp = new LinkedList<byte[]>();
                    LinkedList<MessageContext> ctxListApp = new LinkedList<MessageContext>();

                    LinkedList<byte[]> joinRequestList = new LinkedList<byte[]>();
                    LinkedList<MessageContext> ctxjoinRequestList = new LinkedList<MessageContext>();

                    for (int i = 0; i < commands.length; i++) {

                        if (!controller.isCurrentViewMember(msgCtxs[i].getSender())) {

                            ByteBuffer buff = ByteBuffer.wrap(commands[i]);

                            int l = buff.getInt();

                            if (l > 0) {
                                byte[] b = new byte[l];
                                buff.get(b);

                                String flag = "JOIN_REQUEST";

                                if (flag.equals((new String(b)))) {

                                   
                                    
                                    int messageSize = buff.getInt();
                                    byte[] messageBytes = new byte[messageSize];
                                    buff.get(messageBytes);

                                    logger.debug("Request {} added to Join Requests list", msgCtxs[i].getOperationId());

                                    joinRequestList.add(messageBytes);
                                    ctxjoinRequestList.add(msgCtxs[i]);

                                } else {
                                   
                                    
                                    transListApp.add(commands[i]);
                                    ctxListApp.add(msgCtxs[i]);

                                    logger.debug("Request {} added to App Requests list", msgCtxs[i].getOperationId());

                                }
                            } else {
                                transListApp.add(commands[i]);
                                ctxListApp.add(msgCtxs[i]);

                                logger.debug("Request {} added to App Requests list", msgCtxs[i].getOperationId());

                            }
                        }
                    }

                    if (transListApp.size() > 0 || joinRequestList.size() > 0) {

                        if (transListApp.size() > 0) {
                            byte[][] transArrayApp = new byte[transListApp.size()][];
                            MessageContext[] ctxArrayApp = new MessageContext[ctxListApp.size()];

                            transListApp.toArray(transArrayApp);
                            ctxListApp.toArray(ctxArrayApp);

                            appExecuteBatch(transArrayApp, ctxArrayApp, false);
                        }

                        if (joinRequestList.size() > 0) {

                            byte[][] commandsJoin = new byte[joinRequestList.size()][];
                            joinRequestList.toArray(commandsJoin);

                            MessageContext[] msgCtxsJoin = new MessageContext[ctxjoinRequestList.size()];
                            ctxjoinRequestList.toArray(msgCtxsJoin);

                            executeJoinRequests(commandsJoin, msgCtxsJoin);

                        }

                    }

                }

            } catch (Exception e) {
                logger.error("Failed to process and verify batched requests", e);
                if (e instanceof ArrayIndexOutOfBoundsException) {
                    logger.info("Last checkpoint, last consensus ID (CID): " + state.getLastCheckpointCID());
                    logger.info("Last CID: " + state.getLastCID());
                    logger.info("number of messages expected to be in the batch: "
                            + (state.getLastCID() - state.getLastCheckpointCID() + 1));
                    logger.info("number of messages in the batch: " + state.getMessageBatches().length);
                }
            }

        }

        return lastCID;
    }

    @Override
    public StateManager getStateManager() {
        if (stateManager == null) {
            //stateManager = new StandardStateManager(); // might need to be other implementation
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

        executeBatch(-1, -1, operations, msgCtxs, true);

    }

    @Override
    public TOMMessage[] executeBatch(int processID, int viewID, byte[][] operations, MessageContext[] msgCtxs) {

        return executeBatch(processID, viewID, operations, msgCtxs, false);
    }

    @Override
    public TOMMessage executeUnordered(int processID, int viewID, boolean isHashedReply, byte[] command, MessageContext msgCtx) {

        if (controller.isCurrentViewMember(msgCtx.getSender())) {

            ByteBuffer buff = ByteBuffer.wrap(command);

            int cid = buff.getInt();
            byte[] sig = new byte[buff.getInt()];
            buff.get(sig);

            logger.debug("Received signature from {}: {}", msgCtx.getSender(), Base64.encodeBase64String(sig));

            if (cid >= currentCommit) {

                mapLock.lock();

                Map<Integer, byte[]> signatures = certificates.get(cid);
                if (signatures == null) {

                    signatures = new HashMap<>();
                    certificates.put(cid, signatures);
                }

                signatures.put(msgCtx.getSender(), sig);

                logger.debug("got {} sigs for CID {}", signatures.size(), cid);

                if (currentCommit == cid && signatures.size() > controller.getQuorum()) {

                    logger.debug("Signaling main thread");
                    gotCertificate.signalAll();
                }

                mapLock.unlock();

            }
            return null;
        } else {

            byte[] result = executeUnordered(command, msgCtx);

            if (isHashedReply) {
                result = TOMUtil.computeHash(result);
            }

            return getTOMMessage(processID, viewID, command, msgCtx, result);
        }
    }

    @Override
    public byte[] takeCheckpointHash(int cid) {
        return TOMUtil.computeHash(getSnapshot());
    }

    private TOMMessage[] executeBatch(int processID, int viewID, byte[][] ops, MessageContext[] ctxs, boolean noop) {

        //int cid = msgCtxs[0].getConsensusId();
        TOMMessage[] replies = new TOMMessage[0];
        boolean timeout = false;

        logger.info("Received batch with {} txs", ops.length);

        Map<Integer, Map.Entry<byte[][], MessageContext[]>> split = splitCIDs(ops, ctxs);

        try {

            Integer[] cids = new Integer[split.keySet().size()];

            split.keySet().toArray(cids);

            Arrays.sort(cids);

            int count = 0;

            for (Integer i : cids) {

                count += split.get(i).getKey().length;
            }

            logger.info("Batch contains {} decisions with a total of {} txs", cids.length, count);

            for (Integer cid : cids) {

                byte[][] operations = split.get(cid).getKey();
                MessageContext[] msgCtxs = split.get(cid).getValue();

               // Map<commandType, List<Integer>> commandIndexes = new HashMap<commandType, List<Integer>>();
                //commandIndexes.put(commandType.APP, new ArrayList<Integer>());
               // commandIndexes.put(commandType.JOIN, new ArrayList<Integer>());

                LinkedList<byte[]> transListApp = new LinkedList<>();
                LinkedList<MessageContext> ctxListApp = new LinkedList<>();

                LinkedList<byte[]> joinRequestList = new LinkedList<byte[]>();
                LinkedList<MessageContext> ctxjoinRequestList = new LinkedList<MessageContext>();

                for (int i = 0; i < operations.length; i++) {

                    if (controller.isCurrentViewMember(msgCtxs[i].getSender())) {

                        ByteBuffer buff = ByteBuffer.wrap(operations[i]);

                        int l = buff.getInt();
                        byte[] b = new byte[l];
                        buff.get(b);

                        if ((new String(b)).equals("TIMEOUT")) {

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
                        } else if ((new String(b)).equals("STATE")) {

                            int id = buff.getInt();

                            BlockchainSMMessage smsg = new BlockchainSMMessage(msgCtxs[i].getSender(),
                                    cid, TOMUtil.SM_REQUEST, id, null, null, -1, -1);

                            stateMsgs.add(smsg);

                        }

                    } else if (!noop) {

                        //transList.add(operations[i]);
                        //ctxList.add(msgCtxs[i]);
                        ByteBuffer buff = ByteBuffer.wrap(operations[i]);

                        int l = buff.getInt();

                        if (l > 0) {
                            byte[] b = new byte[l];
                            buff.get(b);

                            String flag = "JOIN_REQUEST";
                            
                            
                            if (flag.equals((new String(b)))) {

                            
                                
                                //commandIndexes.get(commandType.JOIN).add(i);

                                int messageSize = buff.getInt();
                                byte[] messageBytes = new byte[messageSize];
                                buff.get(messageBytes);

                                logger.debug("Request {} added to Join Requests list", msgCtxs[i].getOperationId());

                                joinRequestList.add(messageBytes);
                                ctxjoinRequestList.add(msgCtxs[i]);

                            } else {
                                
                                
                                
                                //commandIndexes.get(commandType.APP).add(i);

                                transListApp.add(operations[i]);
                                ctxListApp.add(msgCtxs[i]);

                                logger.debug("Request {} added to App Requests list", msgCtxs[i].getOperationId());

                            }
                        } else {
                            //commandIndexes.get(commandType.APP).add(i);
                            
                            transListApp.add(operations[i]);
                            ctxListApp.add(msgCtxs[i]);

                            logger.debug("Request {} added to App Requests list", msgCtxs[i].getOperationId());

                        }

                    }

                }

                if (transListApp.size() > 0 || joinRequestList.size() > 0) {
                    
                    if (transListApp.size() > 0) {
                    
                        byte[][] transApp = new byte[transListApp.size()][];
                        MessageContext[] ctxApp = new MessageContext[ctxListApp.size()];

                        transListApp.toArray(transApp);
                        ctxListApp.toArray(ctxApp);

                        byte[][] resultsApp = executeBatch(transApp, ctxApp);
                        //replies = new TOMMessage[results.length];

                        //TODO: this should be logged in another way, because the number transactions logged may not match the
                        // number of results, because of the timeouts (that still need to be added to the block). This can render
                        //audition impossible. Must implemented a way to match the results to their respective transactions.
                        log.storeResults(resultsApp);

                        for (int i = 0; i < resultsApp.length; i++) {

                            TOMMessage reply = getTOMMessage(processID, viewID, transApp[i], ctxApp[i], resultsApp[i]);

                            this.results.add(reply);
                        }

                        if (timer != null) {
                            timer.cancel();
                        }
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
                    }

                     
                    if (joinRequestList.size() > 0) {
                        
                        
                        byte[][] repliesJoin = new byte[joinRequestList.size()][];
                        
                        byte[][] commandsJoin = new byte[joinRequestList.size()][];
                        joinRequestList.toArray(commandsJoin);

                        MessageContext[] msgCtxsJoin = new MessageContext[ctxjoinRequestList.size()];
                        ctxjoinRequestList.toArray(msgCtxsJoin);

                        
                        
                        
                        repliesJoin = executeJoinRequests(commandsJoin, msgCtxsJoin);
                        
                        
                        
                         for (int i = 0; i < repliesJoin.length; i++) {

                            TOMMessage reply = getTOMMessage(processID, viewID, commandsJoin[i], msgCtxsJoin[i], repliesJoin[i]);

                            this.results.add(reply);
                           
                        }

                    }

                }

                log.storeTransactions(cid, operations, msgCtxs);

                //boolean isCheckpoint = cid > 0 && cid % config.getCheckpointPeriod() == 0;
                boolean isCheckpoint = nextNumber > 0 && nextNumber % config.getCheckpointPeriod() == 0;

                
                
                if (joinRequestList.size() > 0 || (timeout | isCheckpoint
                        || /*(cid % config.getLogBatchLimit() == 0)*/ 
                        (this.results.size() > config.getMaxBatchSize())) /* * config.getLogBatchLimit())*/) {

                    byte[][] hashes = log.markEndTransactions();

                    log.storeHeader(nextNumber, lastCheckpoint, lastReconfig, hashes[0], hashes[1], lastBlockHash);

                    //SYNC AQUI!!!
                    long ts = System.currentTimeMillis();
                    if (config.isToWriteSyncLog()) {

                        logger.info("Synching log at CID {} and Block {}", cid, (nextNumber - 1));
                        log.sync();
                        logger.info("Synched log at CID {} and Block {} (elapsed time was {} ms)", cid, (nextNumber - 1), (System.currentTimeMillis() - ts));
                    }

                    lastBlockHash = computeBlockHash(nextNumber, lastCheckpoint, lastReconfig, hashes[0], hashes[1], lastBlockHash);
                    nextNumber++;

                    TOMMessage[] reps = new TOMMessage[this.results.size()];

                    this.results.toArray(reps);
                    this.results.clear();

                    replies = TOMUtil.concat(replies, reps);

                    if (isCheckpoint) {

                        //logger.info("Performing checkpoint at CID {}", cid);
                        logger.info("Performing checkpoint at block {}", nextNumber-1);

                        long t1 = System.currentTimeMillis();
                        
                        log.clearCached();
                        lastCheckpoint = cid;

                        appState = getSnapshot();
                        appStateHash = TOMUtil.computeHash(appState);

                        logger.info("Storing checkpoint at CID {}", cid);

                        writeCheckpointToDisk(cid, appState);
                        
                        long total = System.currentTimeMillis() - t1;
                        
                        System.out.println("Toral ckpt time : "+total);

                    }

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

                    //TODO: there is a bug that makes the layer freeze in this part. This needsto be figured out.
                    if (!(signatures.size() > controller.getQuorum())) {

                        logger.debug("blocking main thread");
                        gotCertificate.await(2000, TimeUnit.MILLISECONDS);
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

                    /*long ts = System.currentTimeMillis();
                    if (config.isToWriteSyncLog()) {

                        logger.info("Synching log at CID {} and Block {}", cid, (nextNumber - 1));
                        log.sync();
                        logger.info("Synched log at CID {} and Block {} (elapsed time was {} ms)", cid, (nextNumber - 1), (System.currentTimeMillis() - ts));

                    }*/
                    if (isCheckpoint) {
                        log.startNewFile(cid, config.getCheckpointPeriod());
                    }

                    timeouts.remove(nextNumber - 1);

                }
            }

            if (timer != null && this.results.isEmpty()) {
                timer.cancel();
                timer = null;
            }

            for (SMMessage smsg : stateMsgs) {

                stateManager.SMRequestDeliver(smsg, config.isBFT());
            }

            stateMsgs.clear();

            logger.info("Returning {} replies", replies.length);

            return replies;
        } catch (IOException | NoSuchAlgorithmException | InterruptedException ex) {
            logger.error("Error while logging/executing batches", ex);
            return new TOMMessage[0];
        } finally {
            if (mapLock.isHeldByCurrentThread()) {
                mapLock.unlock();
            }
        }
    }

    private Map<Integer, Map.Entry<byte[][], MessageContext[]>> splitCIDs(byte[][] operations, MessageContext[] msgCtxs) {

        Map<Integer, List<Map.Entry<byte[], MessageContext>>> map = new HashMap<>();
        for (int i = 0; i < operations.length; i++) {

            List<Map.Entry<byte[], MessageContext>> list = map.get(msgCtxs[i].getConsensusId());
            if (list == null) {

                list = new LinkedList<>();
                map.put(msgCtxs[i].getConsensusId(), list);
            }

            Map.Entry<byte[], MessageContext> entry = new HashMap.SimpleEntry<>(operations[i], msgCtxs[i]);
            list.add(entry);

        }

        Map<Integer, Map.Entry<byte[][], MessageContext[]>> result = new HashMap<>();

        for (Integer cid : map.keySet()) {

            List<Map.Entry<byte[], MessageContext>> value = map.get(cid);

            byte[][] trans = new byte[value.size()][];
            MessageContext[] ctxs = new MessageContext[value.size()];

            for (int i = 0; i < value.size(); i++) {

                trans[i] = value.get(i).getKey();
                ctxs[i] = value.get(i).getValue();
            }

            result.put(cid, new HashMap.SimpleEntry<>(trans, ctxs));
        }

        return result;
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

    private void writeCheckpointToDisk(int cid, byte[] checkpoint) throws IOException {
        long writeDiskStartTime = System.currentTimeMillis();

        String checkpointPath = batchDir + "checkpoint." + config.getProcessId() + "." + cid + ".log";

        RandomAccessFile log = new RandomAccessFile(checkpointPath, "rwd");

        log.write(checkpoint);

        log.close();

        logger.info("DURATION writing checkpoint to disk: {}", (System.currentTimeMillis() - writeDiskStartTime) / 1000.0 + " s");

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

            ByteArrayOutputStream bOut = new ByteArrayOutputStream(248);
            try {
                new ObjectOutputStream(bOut).writeObject(cm);
            } catch (IOException ex) {
                logger.error("Could not serialize message", ex);
            }

            byte[] data = bOut.toByteArray();

            PublicKey pubKey = config.getPublicKey(consMsg.getSender());

            byte[] signature = (byte[]) consMsg.getProof();

            if (Arrays.equals(consMsg.getValue(), hashedBatch)
                    && TOMUtil.verifySignature(pubKey, data, signature) && !alreadyCounted.contains(consMsg.getSender())) {

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

    @Override
    public byte[][] executeBatch(byte[][] operations, MessageContext[] msgCtxs) {

        return appExecuteBatch(operations, msgCtxs, true);
    }

    @Override
    public byte[] executeUnordered(byte[] command, MessageContext msgCtx) {

        return appExecuteUnordered(command, msgCtx);
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
                return serversMakeCertificate(joiningReplicaID, acceptedRequest, commandToExecute,
                        msgCtx.getTimestamp(), controller.getStaticConf());
            }

        } catch (IOException e) {
            e.printStackTrace();
            logger.error("Exception creating JOIN request: " + e.getMessage());
        }
       
        return new byte[0];
    }

    private byte[] serversMakeCertificate(int joiningReplicaID, boolean acceptedRequest, byte[] input,
            long consensusTimestamp, TOMConfiguration executingReplicaConf) throws IOException {

        CoreCertificate certificateValues = new CoreCertificate(joiningReplicaID, acceptedRequest, input,
                consensusTimestamp, executingReplicaConf.getProcessId());

        byte[] signature;

        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(byteOut)) {

            certificateValues.serialize(dos);

            dos.flush();
            byteOut.flush();

            signature = TOMUtil.signMessage(executingReplicaConf.getPrivateKey(), byteOut.toByteArray());
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(out)) {

            ReplicaReconfigReply reply = new ReplicaReconfigReply(certificateValues, signature);

            reply.serialize(dos);

            dos.flush();
            out.flush();
            return out.toByteArray();
        }
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
     * @param commands The batch of requests
     * @param msgCtxs The context associated to each request
     * @param fromConsensus true if the request arrived from a consensus
     * execution, false if it arrives from the state transfer protocol
     *
     * @return the respective replies for each request
     */
    public abstract byte[][] appExecuteBatch(byte[][] commands, MessageContext[] msgCtxs, boolean fromConsensus);

    /**
     * Execute an unordered request
     *
     * @param command The unordered request
     * @param msgCtx The context associated to the request
     *
     * @return the reply for the request issued by the client
     */
    public abstract byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx);

}
