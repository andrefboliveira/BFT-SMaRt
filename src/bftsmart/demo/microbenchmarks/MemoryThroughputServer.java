/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bftsmart.demo.microbenchmarks;

import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.ServerJoiner;
import bftsmart.tom.server.defaultservices.CommandsInfo;
import bftsmart.tom.server.defaultservices.DefaultRecoverable;
import bftsmart.tom.util.Storage;
import bftsmart.tom.util.TOMUtil;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple server that just acknowledge the reception of a request.
 */
public final class MemoryThroughputServer extends DefaultRecoverable implements ServerJoiner {

    private int interval;
    private byte[] reply;
    private float maxTp = -1;
    private boolean context;
    private boolean prettyPrint;
    private int signed;

    private byte[] state;

    private int iterations = 0;
    private long throughputMeasurementStartTime = System.currentTimeMillis();

    private Storage totalLatency = null;
    private Storage consensusLatency = null;
    private Storage preConsLatency = null;
    private Storage posConsLatency = null;
    private Storage proposeLatency = null;
    private Storage writeLatency = null;
    private Storage acceptLatency = null;

    private Storage batchSize = null;

    private ServiceReplica replica;

    private RandomAccessFile randomAccessFile = null;
    private FileChannel channel = null;

    public MemoryThroughputServer(int id, int interval, int replySize, int stateSize, boolean context, boolean prettyPrint, int signed, int write) {

        this.interval = interval;
        this.context = context;
        this.prettyPrint = prettyPrint;
        this.signed = signed;

        this.reply = new byte[replySize];

        for (int i = 0; i < replySize; i++)
            reply[i] = (byte) i;

        this.state = new byte[stateSize];

        for (int i = 0; i < stateSize; i++)
            state[i] = (byte) i;

        totalLatency = new Storage(interval);
        consensusLatency = new Storage(interval);
        preConsLatency = new Storage(interval);
        posConsLatency = new Storage(interval);
        proposeLatency = new Storage(interval);
        writeLatency = new Storage(interval);
        acceptLatency = new Storage(interval);

        batchSize = new Storage(interval);

        if (write > 0) {

            try {
                final File f = File.createTempFile("bft-" + id + "-", Long.toString(System.nanoTime()));
                randomAccessFile = new RandomAccessFile(f, (write > 1 ? "rwd" : "rw"));
                channel = randomAccessFile.getChannel();

                Runtime.getRuntime().addShutdownHook(new Thread() {

                    @Override
                    public void run() {

                        f.delete();
                    }
                });
            } catch (IOException ex) {
                ex.printStackTrace();
                System.exit(0);
            }
        }
        replica = new ServiceReplica(id, this, this, this);
    }

    @Override
    public byte[][] appExecuteBatch(byte[][] commands, MessageContext[] msgCtxs, boolean fromConsensus) {

        batchSize.store(commands.length);

        byte[][] replies = new byte[commands.length][];

        for (int i = 0; i < commands.length; i++) {

            replies[i] = execute(commands[i], msgCtxs[i]);

        }

        if (randomAccessFile != null) {

            ObjectOutputStream oos = null;
            try {
                CommandsInfo cmd = new CommandsInfo(commands, msgCtxs);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                oos = new ObjectOutputStream(bos);
                oos.writeObject(cmd);
                oos.flush();
                byte[] bytes = bos.toByteArray();
                oos.close();
                bos.close();

                ByteBuffer bb = ByteBuffer.allocate(bytes.length);
                bb.put(bytes);
                bb.flip();

                channel.write(bb);
                channel.force(false);
            } catch (IOException ex) {
                Logger.getLogger(MemoryThroughputServer.class.getName()).log(Level.SEVERE, null, ex);

            } finally {
                try {
                    oos.close();
                } catch (IOException ex) {
                    Logger.getLogger(MemoryThroughputServer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        return replies;
    }

    @Override
    public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {
        return execute(command, msgCtx);
    }

    public byte[] execute(byte[] command, MessageContext msgCtx) {

        ByteBuffer buffer = ByteBuffer.wrap(command);
        int l = buffer.getInt();
        byte[] request = new byte[l];
        buffer.get(request);
        l = buffer.getInt();
        byte[] signature = new byte[l];

        buffer.get(signature);
        Signature eng;

        try {

            if (signed > 0) {

                if (signed == 1) {

                    eng = TOMUtil.getSigEngine();
                    eng.initVerify(replica.getReplicaContext().getStaticConfiguration().getPublicKey());
                } else {

                    eng = Signature.getInstance("SHA256withECDSA", "SunEC");
                    Base64.Decoder b64 = Base64.getDecoder();
                    CertificateFactory kf = CertificateFactory.getInstance("X.509");

                    //byte[] cert = b64.decode(ThroughputLatencyClient.pubKey);
                    //InputStream certstream = new ByteArrayInputStream (cert);

                    KeyFactory keyFactory = KeyFactory.getInstance("EC");

                    EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(org.apache.commons.codec.binary.Base64.decodeBase64(ThroughputLatencyClient.pubKey));
                    PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

                    //eng.initVerify(kf.generateCertificate(certstream));
                    eng.initVerify(publicKey);

                }
                eng.update(request);
                if (!eng.verify(signature)) {

                    System.out.println("Client sent invalid signature!");
                    System.exit(0);
                }
            }

        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException | CertificateException | InvalidKeySpecException ex) {
            ex.printStackTrace();
            System.exit(0);
        } catch (NoSuchProviderException ex) {
            ex.printStackTrace();
            System.exit(0);
        }

        boolean readOnly = false;

        iterations++;

        if (msgCtx != null && msgCtx.getFirstInBatch() != null) {


            readOnly = msgCtx.readOnly;

            msgCtx.getFirstInBatch().executedTime = System.nanoTime();

            totalLatency.store(msgCtx.getFirstInBatch().executedTime - msgCtx.getFirstInBatch().receptionTime);

            if (readOnly == false) {

                consensusLatency.store(msgCtx.getFirstInBatch().decisionTime - msgCtx.getFirstInBatch().consensusStartTime);
                long temp = msgCtx.getFirstInBatch().consensusStartTime - msgCtx.getFirstInBatch().receptionTime;
                preConsLatency.store(temp > 0 ? temp : 0);
                posConsLatency.store(msgCtx.getFirstInBatch().executedTime - msgCtx.getFirstInBatch().decisionTime);
                proposeLatency.store(msgCtx.getFirstInBatch().writeSentTime - msgCtx.getFirstInBatch().consensusStartTime);
                writeLatency.store(msgCtx.getFirstInBatch().acceptSentTime - msgCtx.getFirstInBatch().writeSentTime);
                acceptLatency.store(msgCtx.getFirstInBatch().decisionTime - msgCtx.getFirstInBatch().acceptSentTime);


            } else {


                consensusLatency.store(0);
                preConsLatency.store(0);
                posConsLatency.store(0);
                proposeLatency.store(0);
                writeLatency.store(0);
                acceptLatency.store(0);


            }

        } else {


            consensusLatency.store(0);
            preConsLatency.store(0);
            posConsLatency.store(0);
            proposeLatency.store(0);
            writeLatency.store(0);
            acceptLatency.store(0);


        }

        float tp = -1;
        if (iterations % interval == 0) {
            if (context)
                System.out.println("--- (Context)  iterations: " + iterations + " // regency: " + msgCtx.getRegency() + " // consensus: " + msgCtx.getConsensusId() + " ---");

            //System.out.println("--- Measurements after "+ iterations+" ops ("+interval+" samples) ---");

            tp = interval * 1000 / (float) (System.currentTimeMillis() - throughputMeasurementStartTime);

            if (tp > maxTp) maxTp = tp;

            if (prettyPrint) {
                System.out.println("Throughput = " + tp + " operations/sec (Maximum observed: " + maxTp + " ops/sec)");

                System.out.println("Total latency = " + totalLatency.getAverage(false) / 1000 + " (+/- " + (long) totalLatency.getDP(false) / 1000 + ") us ");
                System.out.println("Consensus latency = " + consensusLatency.getAverage(false) / 1000 + " (+/- " + (long) consensusLatency.getDP(false) / 1000 + ") us ");
                System.out.println("Pre-consensus latency = " + preConsLatency.getAverage(false) / 1000 + " (+/- " + (long) preConsLatency.getDP(false) / 1000 + ") us ");
                System.out.println("Pos-consensus latency = " + posConsLatency.getAverage(false) / 1000 + " (+/- " + (long) posConsLatency.getDP(false) / 1000 + ") us ");
                System.out.println("Propose latency = " + proposeLatency.getAverage(false) / 1000 + " (+/- " + (long) proposeLatency.getDP(false) / 1000 + ") us ");
                System.out.println("Write latency = " + writeLatency.getAverage(false) / 1000 + " (+/- " + (long) writeLatency.getDP(false) / 1000 + ") us ");
                System.out.println("Accept latency = " + acceptLatency.getAverage(false) / 1000 + " (+/- " + (long) acceptLatency.getDP(false) / 1000 + ") us ");

                System.out.println("Batch average size = " + batchSize.getAverage(false) + " (+/- " + (long) batchSize.getDP(false) + ") requests");
            } else {
                
                /*System.out.println(System.currentTimeMillis() + "\t" + iterations + "\t" + tp+"\t" + maxTp +
                        "\t" + totalLatency.getAverage(false) + "\t" + totalLatency.getDP(false)+
                        "\t" + consensusLatency.getAverage(false) + "\t" + consensusLatency.getDP(false) + 
                        "\t" + preConsLatency.getAverage(false) + "\t" + preConsLatency.getDP(false) +
                        "\t" + posConsLatency.getAverage(false) + "\t" + posConsLatency.getDP(false) +
                        "\t" + proposeLatency.getAverage(false) + "\t" + proposeLatency.getDP(false) +
                        "\t" + writeLatency.getAverage(false) + "\t" + writeLatency.getDP(false) +
                        "\t" + acceptLatency.getAverage(false) + "\t" + acceptLatency.getDP(false) +
                        "\t" + batchSize.getAverage(false) + "\t" + batchSize.getDP(false));*/
                System.out.println(System.currentTimeMillis() + "\t" + iterations + "\t" + tp + "\t" + maxTp);
            }

            totalLatency.reset();
            consensusLatency.reset();
            preConsLatency.reset();
            posConsLatency.reset();
            proposeLatency.reset();
            writeLatency.reset();
            acceptLatency.reset();
            batchSize.reset();

            throughputMeasurementStartTime = System.currentTimeMillis();
        }

        return reply;
    }

    public static void main(String[] args) {
        if (args.length < 7) {
            System.out.println("Usage: ... ThroughputLatencyServer <processId> <measurement interval> <reply size> <state size> <context?> <pretty print?>  <nosig | default | ecdsa> [rwd | rw]");
            System.exit(-1);
        }

        int processId = Integer.parseInt(args[0]);
        int interval = Integer.parseInt(args[1]);
        int replySize = Integer.parseInt(args[2]);
        int stateSize = Integer.parseInt(args[3]);
        boolean context = Boolean.parseBoolean(args[4]);
        boolean prettyPrint = Boolean.parseBoolean(args[5]);
        String signed = args[6];
        String write = args.length > 7 ? args[7] : "";

        int s = 0;

        if (!signed.equalsIgnoreCase("nosig")) s++;
        if (signed.equalsIgnoreCase("ecdsa")) s++;

        if (s == 2 && Security.getProvider("SunEC") == null) {

            System.out.println("Option 'ecdsa' requires SunEC provider to be available.");
            System.exit(0);
        }

        int w = 0;

        if (!write.equalsIgnoreCase("")) w++;
        if (write.equalsIgnoreCase("rwd")) w++;

        new MemoryThroughputServer(processId, interval, replySize, stateSize, context, prettyPrint, s, w);
    }

    @Override
    public void installSnapshot(byte[] state) {
        //nothing
        this.state = state;
    }

    @Override
    public byte[] getSnapshot() {
        return this.state;
    }

    //@Override
    public byte[] appCreateJoinRequest(byte[] command) {
        return "ASK_JOIN".getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean appVerifyJoinRequest(byte[] command) {
        String input = new String(command, StandardCharsets.UTF_8);

        return "ASK_JOIN".equals(input);
    }

}
