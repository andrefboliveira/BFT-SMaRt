/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.tom.server.defaultservices.blockchain;

import bftsmart.statemanagement.ApplicationState;
import bftsmart.statemanagement.standard.StandardStateManager;
import bftsmart.tom.core.DeliveryThread;
import bftsmart.tom.core.TOMLayer;
import bftsmart.tom.core.messages.ForwardedMessage;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.server.defaultservices.CommandsInfo;
import bftsmart.tom.util.TOMUtil;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author joao
 */
public class BlockchainStateManager extends StandardStateManager implements Runnable {

    private boolean containsResults;
    private boolean containsCertificate;
    private ServerSocket welcomeSocket = null;
    private String logDir = null;
    private ExecutorService outExec = null;
    private ExecutorService inExec = null;
    private TOMMessageGenerator TOMgen;

    public BlockchainStateManager(boolean containsResults, boolean containsCertificate) {

        this.containsResults = containsResults;
        this.containsCertificate = containsCertificate;

    }

    public void setTOMgen(TOMMessageGenerator TOMgen) {

        this.TOMgen = TOMgen;
    }

    @Override
    public void init(TOMLayer tomLayer, DeliveryThread dt) {

        super.init(tomLayer, dt);

        logDir = "files".concat(System.getProperty("file.separator"));

        try {

            File directory = new File(logDir);
            if (!directory.exists()) directory.mkdir();

            welcomeSocket = new ServerSocket(
                    this.SVController.getStaticConf().getPort(this.SVController.getStaticConf().getProcessId()) + 2);

            int nWorkers = this.SVController.getStaticConf().getNumNettyWorkers();
            nWorkers = nWorkers > 0 ? nWorkers : Runtime.getRuntime().availableProcessors();

            outExec = Executors.newFixedThreadPool(nWorkers);
            inExec = Executors.newFixedThreadPool(nWorkers);

            (new Thread(this)).start();

        } catch (IOException ex) {

            logger.error("Error creating blockchain socket.", ex);
        }
    }

    @Override
    protected boolean enoughReplies() {

        //we override this method so that we can also verify that all other data related to blocks are consistent

        if (senderStates.size() > SVController.getCurrentViewF()) {

            int count = 0;

            for (ApplicationState s : senderStates.values()) {

                int nextNumber = -2;
                int lastReconfig = -2;
                byte[] lastBlockHash = null;

                Map<Integer, CommandsInfo> cachedBatches = null;
                Map<Integer, byte[][]> cachedResults = null;
                Map<Integer, byte[]> cachedHeaders = null;
                Map<Integer, byte[]> cachedCertificates = null;

                BlockchainState state = (BlockchainState) s;

                if (nextNumber == -2) nextNumber = state.nextNumber;
                if (lastReconfig == -2) lastReconfig = state.lastReconfig;
                if (lastBlockHash == null) lastBlockHash = state.lastBlockHash;

                if (cachedBatches == null) cachedBatches = state.cachedBatches;
                if (cachedResults == null) cachedResults = state.cachedResults;
                if (cachedHeaders == null) cachedHeaders = state.cachedHeaders;
                if (cachedCertificates == null) cachedCertificates = state.cachedCertificates;

                if (nextNumber == state.nextNumber && lastReconfig == state.lastReconfig && Arrays.equals(lastBlockHash, state.lastBlockHash)
                        && state.cachedBatches.equals(cachedBatches) && state.cachedResults.equals(cachedResults) && state.cachedHeaders.equals(cachedHeaders)) {


                    //TODO: verify certificates

                    count++;


                }

            }

            return count > SVController.getCurrentViewF();
        } else return false;
    }

    @Override
    protected void requestState() {

        try {

            ordered = true;
            changeReplica(); // always ask the state/ledger to a different replica

            ByteBuffer buff = ByteBuffer.allocate((Integer.BYTES * 2) + "STATE".getBytes().length);
            buff.putInt("STATE".getBytes().length);
            buff.put("STATE".getBytes());
            buff.putInt(replica);

            TOMMessage stateMsg = TOMgen.getNextOrdered(buff.array());

            byte[] data = TOMMessageGenerator.serializeTOMMsg(stateMsg);

            stateMsg.serializedMessage = data;

            if (SVController.getStaticConf().getUseSignatures() == 1) {

                stateMsg.serializedMessageSignature = TOMUtil.signMessage(SVController.getStaticConf().getPrivateKey(), data);
                stateMsg.signed = true;

            }

            tomLayer.getCommunication().send(SVController.getCurrentViewOtherAcceptors(),
                    new ForwardedMessage(SVController.getStaticConf().getProcessId(), stateMsg));
        } catch (IOException ex) {
            logger.error("Error asking for the state", ex);
        }
    }

    private boolean validateBlock(byte[] block) throws NoSuchAlgorithmException {

        ByteBuffer buff = ByteBuffer.wrap(block);
        MessageDigest transDigest = TOMUtil.getHashEngine();
        MessageDigest resultsDigest = TOMUtil.getHashEngine();
        MessageDigest headerDigest = TOMUtil.getHashEngine();

        //body
        while (true) {

            int cid = -1;
            int l = 0;
            byte[] trans = null;

            cid = buff.getInt();

            logger.debug("cid: " + cid);

            if (cid == -1) break;

            l = buff.getInt();
            trans = new byte[l];

            buff.get(trans);

            transDigest.update(trans);

            if (containsResults) {

                int nResults = buff.getInt();

                for (int i = 0; i < nResults; i++) {

                    l = buff.getInt();
                    byte[] res = new byte[l];
                    buff.get(res);

                    resultsDigest.update(res);

                }
            }
        }

        //header
        int number = buff.getInt();
        int lastCheckpoint = buff.getInt();
        int lastReconf = buff.getInt();

        int l = buff.getInt();

        byte[] transHash = new byte[l];
        buff.get(transHash);

        l = buff.getInt();

        byte[] resultsHash = new byte[l];
        buff.get(resultsHash);

        l = buff.getInt();

        byte[] prevBlock = new byte[l];
        buff.get(prevBlock);

        //certificate
        HashMap<Integer, byte[]> sigs = null;

        if (containsCertificate) {

            int nSigs = buff.getInt();
            sigs = new HashMap<>();

            for (int i = 0; i < nSigs; i++) {

                int id = buff.getInt();
                l = buff.getInt();

                byte[] sig = new byte[l];
                buff.get(sig);

                sigs.put(id, sig);
            }
        }

        //calculate hashes
        byte[] myTransHash = transDigest.digest();
        byte[] myResHash = new byte[0];
        if (containsResults) myResHash = resultsDigest.digest();

        boolean sameTransHash = Arrays.equals(transHash, myTransHash);
        boolean sameResHash = Arrays.equals(resultsHash, myResHash);

        //logger.info("[{}] Same trans hash: {}", number, sameTransHash);
        //logger.info("[{}] Same res hash: {}", number, sameResHash);

        if (sigs != null) {

            ByteBuffer header = ByteBuffer.allocate(Integer.BYTES * (containsResults ? 6 : 5)
                    + (prevBlock.length + transHash.length + (containsResults ? resultsHash.length : 0)));

            header.putInt(number);
            header.putInt(lastCheckpoint);
            header.putInt(lastReconf);

            header.putInt(transHash.length);
            header.put(transHash);

            if (containsResults) {

                header.putInt(resultsHash.length);
                header.put(resultsHash);
            }

            header.putInt(prevBlock.length);
            header.put(prevBlock);

            byte[] headerHash = headerDigest.digest(header.array());
            int count = 0;

            for (int id : sigs.keySet()) {

                if (TOMUtil.verifySignature(SVController.getStaticConf().getPublicKey(id), headerHash, sigs.get(id)))
                    count++;

            }

            logger.info("[{}] Number of valid sigs: {}/{}", number, count, sigs.size());

            //TODO: there is an issue related this certificate and hah headers. Hash headers a not deterministic because of the consensus
            //proof contained in the context object. I cannot hack the context object with a transient proof because that will mess with
            //this state transfer manager. So I perform signature verification just to create the overhead necessary for experimental evaluation.
        }

        return true;
    }

    public void fetchBlocks(int lastCID) {
        //TODO: Change new logger.info to logger.debug

        File directory = new File(logDir);

        File[] files = directory.listFiles((File pathname) ->
                pathname.getName().startsWith("" + SVController.getStaticConf().getProcessId()) && pathname.getName().endsWith(".log"));

        int[] cids = new int[files.length];

        for (int i = 0; i < files.length; i++) {

            System.out.println("File Name: " + files[i].getName());
            String[] tokens = files[i].getName().split("[.]");

            logger.info("Got cids {} through {}", tokens[1], tokens[2]);
            cids[i] = new Integer(tokens[1]).intValue();

        }

        Arrays.sort(cids);

        int myLastCID = cids[cids.length - 1];
        if (myLastCID == -1) myLastCID++;


        try {
            logger.info("Fetching blocks from {} to {} (exclusively) from replica {} at port {}",
                    myLastCID, lastCID, SVController.getCurrentView().getAddress(replica).getHostName(), SVController.getStaticConf().getPort(replica) + 2);

            long startFetchingBlocksTimeOuter = System.currentTimeMillis();


            final CountDownLatch latch = new CountDownLatch((lastCID - myLastCID) / SVController.getStaticConf().getCheckpointPeriod());

            for (int i = myLastCID; i < lastCID; i += SVController.getStaticConf().getCheckpointPeriod()) {
                long startFetchingBlocksTimeInner = System.currentTimeMillis();

                final int cid = i;


                Socket clientSocket = new Socket(SVController.getCurrentView().getAddress(replica).getHostName(), SVController.getStaticConf().getPort(replica) + 2);

                int upperRangeCID_attempt = (cid + SVController.getStaticConf().getCheckpointPeriod());
                int upperRangeCID = (upperRangeCID_attempt <= lastCID ? upperRangeCID_attempt : lastCID);

                logger.debug("Created socket for processing CIDs {} through {}", cid, upperRangeCID);

                inExec.submit(new Thread() {

                    @Override
                    public void run() {

                        BufferedOutputStream bos = null;
                        ByteArrayOutputStream baos = null;
                        DataOutputStream outToServer = null;
                        FileOutputStream fos = null;
                        InputStream inFromServer = null;

                        try {
                            int upperRangeCID_attempt = (cid + SVController.getStaticConf().getCheckpointPeriod());
                            int upperRangeCID = (upperRangeCID_attempt <= lastCID ? upperRangeCID_attempt : lastCID);


                            outToServer = new DataOutputStream(clientSocket.getOutputStream());
                            inFromServer = clientSocket.getInputStream();
                            logger.debug("Get input stream of socket for processing CIDs {} through {}", cid, upperRangeCID);


                            outToServer.writeInt(cid);

                            String blockPath = logDir + SVController.getStaticConf().getProcessId() +
                                    "." + cid + "." + (cid + SVController.getStaticConf().getCheckpointPeriod()) + ".log";

                            baos = new ByteArrayOutputStream();

                            File file = new File(blockPath);
                            fos = new FileOutputStream(file);
                            bos = new BufferedOutputStream(fos);

                            logger.info("Start writing of CIDs {} through {}", cid, upperRangeCID);

//                            bytesRead = inFromServer.read(aByte, 0, aByte.length);
//
//                            do {
//                                baos.write(aByte);
//                                bytesRead = inFromServer.read(aByte);
//                                logger.debug("Bytes of block from CIDs {} through {} read: {}", cid, upperRangeCID, bytesRead);
//                            } while (bytesRead > 0);

                            //                            int BUFFER_SIZE = 65536;
                            int BUFFER_SIZE = 1500;

//                            int BUFFER_SIZE = clientSocket.getReceiveBufferSize();

                            byte[] buffer = new byte[BUFFER_SIZE];

                            int count;
                            while ((count = inFromServer.read(buffer)) > -1) {
                                baos.write(buffer, 0, count);
                                logger.debug("Bytes of block from CIDs {} through {} read: {}", cid, upperRangeCID, count);

                            }


                      /*      inFromServer.read(buffer);
                            long fileSize = ByteBuffer.wrap(buffer, 0, Long.BYTES).getLong();


                            baos.write(buffer, Long.BYTES, buffer.length);
                            long total_read = buffer.length;
                            while (total_read < fileSize) {
                                int count_read = inFromServer.read(buffer);
                                total_read += count_read;
                                baos.write(buffer, 0, count_read);
                            }
*/


                            baos.flush();

//                            logger.info("finished cids {} through {}", cid, upperRangeCID);

                            byte[] block = baos.toByteArray();
                            logger.info("Block size of cids {} through {}: {} bytes", cid, upperRangeCID, block.length);

                            validateBlock(block);

                            bos.write(block);
                            logger.debug("Block of cids {} through {} written", cid, upperRangeCID);

                            bos.flush();
                            fos.flush();
                            fos.getChannel().force(false);


                            logger.info("DURATION fetching blocks {} through {} since socket: {} s.", cid, upperRangeCID, (System.currentTimeMillis() - startFetchingBlocksTimeInner) / 1000.0);

                        } catch (NoSuchAlgorithmException | IOException ex) {
                            logger.error("Error fetching blocks", ex);
                            Logger.getLogger(BlockchainStateManager.class.getName()).log(Level.SEVERE, null, ex);

                        } finally {
                            try {
                                if (bos != null) bos.close();
                                if (baos != null) baos.close();
                                if (fos != null) fos.close();
                                if (outToServer != null) outToServer.close();
                                if (inFromServer != null) inFromServer.close();

                                if (clientSocket != null) clientSocket.close();

                            } catch (IOException e) {
                                logger.error("Error closing streams of fetching blocks");
                            }

                            latch.countDown();

                        }

                    }
                });

            }
            latch.await();
            //System.exit(0);
            logger.info("DURATION fetching blocks since beginning: {} s.", (System.currentTimeMillis() - startFetchingBlocksTimeOuter) / 1000.0);

        } catch (IOException | InterruptedException ex) {

            logger.error("Interruption error", ex);
        }
    }

    @Override
    public void run() {

        try {

            logger.info("Waiting for block requests at port {}", welcomeSocket.getLocalPort());

            while (true) {

                Socket connectionSocket = welcomeSocket.accept();

                outExec.submit(new Thread() {

                    @Override
                    public void run() {

                        DataInputStream inToClient = null;
                        BufferedOutputStream outFromClient = null;

                        try {

                            inToClient = new DataInputStream(connectionSocket.getInputStream());
                            OutputStream outStream = connectionSocket.getOutputStream();
                            outFromClient = new BufferedOutputStream(outStream);

                            int blockNumber = inToClient.readInt();

                            String blockPath = logDir +
                                    SVController.getStaticConf().getProcessId() + "." +
                                    blockNumber + "." + (blockNumber + SVController.getStaticConf().getCheckpointPeriod()) + ".log";

//                            File blockFile = new File(blockPath);
//                            int fileSize = (int) blockFile.length();
//                            byte[] filearray = new byte[fileSize];
//
//                            FileInputStream fis = new FileInputStream(blockFile);
//                            BufferedInputStream bis = new BufferedInputStream(fis);
//
//
//                            bis.read(filearray, 0, fileSize);
//                            outFromClient.write(filearray, 0, fileSize);


                            File blockFile = new File(blockPath);
                            long fileSize = blockFile.length();

                            byte[] readFileBuffer = new byte[16 * 1024];

                            FileInputStream fis = new FileInputStream(blockFile);

                            int count;
                            while ((count = fis.read(readFileBuffer)) > -1) {
                                outFromClient.write(readFileBuffer, 0, count);
                            }

                       /*     byte[] fileSizeBytes = ByteBuffer.allocate(Long.BYTES).putLong(fileSize).array();
                            outFromClient.write(fileSizeBytes);

                            long total_read = 0;
                            while (total_read < fileSize) {
                                int count_read = fis.read(readFileBuffer);
                                total_read += count_read;
                                outFromClient.write(readFileBuffer, 0, count_read);
                            }*/


                            outFromClient.flush();
                            outStream.flush();
                            outFromClient.close();
                            outStream.close();
                            inToClient.close();
                            fis.close();

                            connectionSocket.close();


                        } catch (IOException ex) {

                            logger.error("Socket error.", ex);
                        }

                    }
                });
            }

        } catch (IOException ex) {
            logger.error("Socket error.", ex);
        }
    }


}
