/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.demo.microbenchmarks;

import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.tom.util.TOMUtil;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author eduardo
 */
public class BlockTest {

    public BlockTest() {
    }

    public static void main(String[] args) {

        try {
            //body
            int cid = 1;
            byte[][] operations = new byte[512][300];
            byte[][] proofs = new byte[512][100];
            byte[][] results = new byte[512][400];

            //header
            int blocknumber = 1;
            int lastcpt = 0;
            int lastrec = 0;
            byte[] transHash = computeHash(operations);
            byte[] resultsHash = computeHash(results);
            //log.storeHeader(nextNumber, lastCheckpoint, lastReconfig, hashes[0], hashes[1], lastBlockHash);
            byte[] blockhash = computeBlockHash(blocknumber, lastcpt, lastrec, transHash, resultsHash, new byte[100]);

            TOMConfiguration config = new TOMConfiguration(0, null);
            //certificate: 2f+1 = 3
            byte[] mySig1 = TOMUtil.signMessage(config.getPrivateKey(), blockhash);
            byte[] mySig2 = TOMUtil.signMessage(config.getPrivateKey(), blockhash);
            byte[] mySig3 = TOMUtil.signMessage(config.getPrivateKey(), blockhash);

            int[] numbers = new int[args.length];
            for (int i = 0; i < args.length; i++) {
                numbers[i] = Integer.parseInt(args[i]);
            }
            Arrays.sort(numbers);
            //int numbers[] = {500, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000};
            int x = 0;
            //processar os blocos
            long init = System.nanoTime();
            for (int i = 1; i <= numbers[numbers.length - 1]; i++) {

                //block validation
                byte[] transHashCheck = computeHash(operations);
                byte[] resultsHashCheck = computeHash(results);
                boolean sameTransHash = Arrays.equals(transHash, transHashCheck);
                boolean sameResHash = Arrays.equals(resultsHash, resultsHashCheck);

                if (!sameResHash || !sameTransHash) {
                    System.exit(0);
                }
                if (!TOMUtil.verifySignature(config.getPublicKey(0), blockhash, mySig1)) {
                    System.exit(0);
                }

                if (!TOMUtil.verifySignature(config.getPublicKey(0), blockhash, mySig2)) {
                    System.exit(0);
                }
                if (!TOMUtil.verifySignature(config.getPublicKey(0), blockhash, mySig2)) {
                    System.exit(0);
                }

                //txs executions
                for (int j = 0; j < operations.length; j++) {
                    x++;
                    x--;
                }

                for (int z = 0; z < numbers.length; z++) {
                    if (i == numbers[z]) {
                        long tf = (System.nanoTime() - init) / 1000000;
                        System.out.println("Time to validate and execute " + numbers[z] + " blocks: " + tf);
                        break;
                    }
                }
            }

        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(BlockTest.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private static byte[] computeHash(byte[][] data) throws NoSuchAlgorithmException {

        ByteBuffer buff = ByteBuffer.allocate(data[0].length * data.length);

        for (int i = 0; i < data.length; i++) {
            buff.put(data[i]);
        }

        MessageDigest md = TOMUtil.getHashEngine();

        return md.digest(buff.array());
    }

    private static byte[] computeBlockHash(int number, int lastCheckpoint, int lastReconf, byte[] transHash,
                                           byte[] resultsHash, byte[] prevBlock) throws NoSuchAlgorithmException {

        ByteBuffer buff = ByteBuffer
                .allocate(Integer.BYTES * 6 + (prevBlock.length + transHash.length + resultsHash.length));

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

}
