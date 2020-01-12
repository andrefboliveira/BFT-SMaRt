/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.tom.server.defaultservices.blockchain.logger;

import bftsmart.tom.MessageContext;
import bftsmart.tom.server.defaultservices.CommandsInfo;
import bftsmart.tom.server.defaultservices.blockchain.BatchLogger;
import bftsmart.tom.util.TOMUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 *
 * @author joao
 */
public class AsyncBatchLogger implements BatchLogger {
    
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    
    private int id;
    private int lastCachedCID = -1;
    private int firstCachedCID = -1;
    private int lastStoredCID = -1;
    private TreeMap<Integer,CommandsInfo> cachedBatches;
    private TreeMap<Integer,byte[][]> cachedResults;
    private TreeMap<Integer,byte[]> cachedHeaders;
    private TreeMap<Integer,byte[]> cachedCertificates;
    private RandomAccessFile log;
    private FileChannel channel;
    private String logDir;
    private String logPath;
    private MessageDigest transDigest;
    private MessageDigest resultsDigest;
        
    private AsyncBatchLogger() {
        //not to be used
        
    }
    
    private AsyncBatchLogger(int id, String logDir) throws NoSuchAlgorithmException {
        this.id = id;
        
        cachedBatches = new TreeMap<>();
        cachedResults = new TreeMap<>();
        cachedHeaders = new TreeMap<>();
        cachedCertificates = new TreeMap<>();
         
        transDigest = TOMUtil.getHashEngine();
        resultsDigest = TOMUtil.getHashEngine();
                
        File directory = new File(logDir);
        if (!directory.exists()) directory.mkdir();
        
        this.logDir = logDir;
        
        logger.info("Asynchronous batch logger instantiated");


    }
    
    public void startNewFile(int cid, int period) throws IOException {
        
        if (log != null) log.close();
        if (channel != null) channel.close();

        logPath = logDir + this.id + "." + cid + "." + (cid + period) + ".log";
        
        logger.debug("Logging to file " + logPath);
        log = new RandomAccessFile(logPath, "rw");
        channel = log.getChannel();
        
    }
    
    public void openFile(int cid, int period) throws IOException {
        
        if (log != null) log.close();
        if (channel != null) channel.close();

        logPath = logDir + this.id + "." + cid + "." + (cid + period) + ".log";
        
        logger.debug("Opening file " + logPath);
        
        File f = new File(logPath);
        long fileLength = f.length();
        log = new RandomAccessFile(f, "rw");
        log.seek(fileLength);
    
        channel = log.getChannel();
        
    }
    
    public static BatchLogger getInstance(int id, String logDir) throws FileNotFoundException, NoSuchAlgorithmException {
        AsyncBatchLogger ret = new AsyncBatchLogger(id, logDir);
        return ret;
    }
    
    public void storeTransactions(int cid, byte[][] requests, MessageContext[] contexts) throws IOException {
        
        if (firstCachedCID == -1) firstCachedCID = cid;
        lastCachedCID = cid;
        lastStoredCID = cid;
        CommandsInfo cmds = new CommandsInfo(requests, contexts);
        cachedBatches.put(cid, cmds);
        writeTransactionsToDisk(cid, cmds);
        
    }
    
    public void storeResults(byte[][] results) throws IOException{
     
        for (int i = 0; i < results.length ; i++) {
            
            if (results[i] == null) results[i] = new byte[0];
        } 
        
        cachedResults.put(lastStoredCID, results);
        writeResultsToDisk(results);
    }
    
    public byte[][] markEndTransactions() throws IOException {
        
        ByteBuffer buff = getEOT();
        
        channel.write(buff);
        return new byte[][] {transDigest.digest(), resultsDigest.digest()};
    }
    
    public void storeHeader(int number, int lastCheckpoint, int lastReconf,  byte[] transHash,  byte[] resultsHash,  byte[] prevBlock) throws IOException {
     
        logger.debug("writting header for block #{} to disk", number);
        
        ByteBuffer buff = prepareHeader(number, lastCheckpoint, lastReconf, transHash, resultsHash, prevBlock);
        
        cachedHeaders.put(lastStoredCID, buff.array());
                
        channel.write(buff);
        
        logger.debug("wrote header for block #{} to disk", number);
    }
    
    public void storeCertificate(Map<Integer, byte[]> sigs) throws IOException {
        
        logger.debug("writting certificate to disk");
        
        ByteBuffer buff = prepareCertificate(sigs);
        
        cachedCertificates.put(lastStoredCID, buff.array());
        
        channel.write(buff);
        
        logger.debug("wrote certificate to disk");
    }
    
    public int getLastCachedCID() {
        return lastCachedCID;
    }

    public int getFirstCachedCID() {
        return firstCachedCID;
    }
    
    public int getLastStoredCID() {
        return lastStoredCID;
    }
    
    public Map<Integer, CommandsInfo> getCachedBatches() {
        
        return cachedBatches;
        
    }
    
    public Map<Integer, byte[][]> getCachedResults() {
        
        return cachedResults;
        
    }
    
    public Map<Integer, byte[]> getCachedHeaders() {
        
        return cachedHeaders;
        
    }
    
    public Map<Integer, byte[]> getCachedCertificates() {
        
        return cachedCertificates;
        
    }
    
    public void clearCached() {
        
        cachedBatches.clear();
        cachedResults.clear();
        cachedHeaders.clear();
        cachedCertificates.clear();
        firstCachedCID = -1;
        lastCachedCID = -1;
    }
    
    public void setCached(int firstCID, int lastCID, Map<Integer, CommandsInfo> batches,
            Map<Integer, byte[][]> results, Map<Integer, byte[]> headers, Map<Integer, byte[]> certificates) {
        
        clearCached();
        
        
        cachedBatches.putAll(batches);
        cachedResults.putAll(results);
        cachedHeaders.putAll(headers);
        cachedCertificates.putAll(certificates);
        
        lastStoredCID = firstCID;
        firstCachedCID = firstCID;
        lastCachedCID = lastCID;
    }
    
    public void startFileFromCache(int period) throws IOException {
        
        Integer[] cids = new Integer[cachedBatches.keySet().size()];
        
        cachedBatches.keySet().toArray(cids);
        
        Arrays.sort(cids);
        
        startNewFile(cids[0],period);
        
        for (int cid : cids) {
            
            writeTransactionsToDisk(cid, cachedBatches.get(cid));
            markEndTransactions();
            writeResultsToDisk(cachedResults.get(cid));
            channel.write(ByteBuffer.wrap(cachedHeaders.get(cid)));
            channel.write(ByteBuffer.wrap(cachedCertificates.get(cid)));
            
        }
        
        sync();
    }
    
    private void writeTransactionsToDisk(int cid, CommandsInfo commandsInfo) throws IOException {
        
        logger.debug("writting transactios to disk");
        
        byte[] transBytes = serializeTransactions(commandsInfo);
        
        //update the transactions hash for the entire block
        transDigest.update(transBytes);

        ByteBuffer buff = prepareTransactions(cid, transBytes);
        
        channel.write(buff);
        
        logger.debug("wrote transactions to disk");

    }
    
    private void writeResultsToDisk(byte[][] results) throws IOException {
        
        logger.debug("writting results to disk");
        
        for (byte[] result : results) { //update the results hash for the entire block
        
            resultsDigest.update(result);

        }
        
        ByteBuffer buff = prepareResults(results);
        
        channel.write(buff);
        
        logger.debug("wrote results to disk");

    }
    
    public void sync() throws IOException {
        
        logger.debug("synching log to disk");

        //log.getFD().sync();
        channel.force(false);
        
        logger.debug("synced log to disk");
    }
    

}

