/**
 * Copyright (c) 2007-2009 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 * 
 * This file is part of SMaRt.
 * 
 * SMaRt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SMaRt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */

package navigators.smart.tom.demo.microbenchmarks;

import navigators.smart.tom.MessageContext;
import navigators.smart.tom.ServiceReplica;
import navigators.smart.tom.util.Storage;

/**
 * Simple server that just acknowledge the reception of a request.
 */
public final class ThroughputLatencyServer extends ServiceReplica {
    
    private int interval;
    private int replySize;
    private boolean context;
    
    private byte[] state;
    
    private int iterations = 0;
    private long throughputMeasurementStartTime = System.currentTimeMillis();
            
    private Storage totalLatency = null;
    private Storage consensusLatency = null;
    private Storage preConsLatency = null;
    private Storage posConsLatency = null;
    private Storage proposeLatency = null;
    private Storage weakLatency = null;
    private Storage strongLatency = null;

    public ThroughputLatencyServer(int id, int interval, int replySize, int stateSize, boolean context) {
        super(id);

        this.interval = interval;
        this.replySize = replySize;
        this.context = context;
        
        this.state = new byte[stateSize];
        
        for (int i = 0; i < stateSize ;i++)
            state[i] = (byte) i;

        totalLatency = new Storage(interval);
        consensusLatency = new Storage(interval);
        preConsLatency = new Storage(interval);
        posConsLatency = new Storage(interval);
        proposeLatency = new Storage(interval);
        weakLatency = new Storage(interval);
        strongLatency = new Storage(interval);
    }
    
    @Override
    public byte[] executeOrdered(byte[] command, MessageContext msgCtx) {
        return execute(command,msgCtx);
    }
    
    @Override
    public byte[] executeUnordered(byte[] command, MessageContext msgCtx) {
        return execute(command,msgCtx);
    }
    
    public byte[] execute(byte[] command, MessageContext msgCtx) {        
        iterations++;
        
        if(msgCtx.getConsensusId() == -1) {
            return new byte[replySize];
        }
     
        totalLatency.store(msgCtx.getFirstInBatch().executedTime - msgCtx.getFirstInBatch().receptionTime);
        consensusLatency.store(msgCtx.getFirstInBatch().decisionTime - msgCtx.getFirstInBatch().consensusStartTime);
        preConsLatency.store(msgCtx.getFirstInBatch().consensusStartTime - msgCtx.getFirstInBatch().receptionTime);
        posConsLatency.store(msgCtx.getFirstInBatch().executedTime - msgCtx.getFirstInBatch().decisionTime);
        proposeLatency.store(msgCtx.getFirstInBatch().weakSentTime - msgCtx.getFirstInBatch().consensusStartTime);
        weakLatency.store(msgCtx.getFirstInBatch().strongSentTime - msgCtx.getFirstInBatch().weakSentTime);
        strongLatency.store(msgCtx.getFirstInBatch().decisionTime - msgCtx.getFirstInBatch().strongSentTime);

        if(iterations % interval == 0) {
            if (context) System.out.println(iterations + " // " + msgCtx.getRegency() + " // " + msgCtx.getConsensusId());
            
            System.out.println("--- Measurements after "+ iterations+" ops ("+interval+" samples) ---");
            
            System.out.println("Throughput = " +  (float)(interval*1000/(float)(System.currentTimeMillis()-throughputMeasurementStartTime)) +" operations/sec");            
            
            System.out.println("Total latency = " + totalLatency.getAverage(false) / 1000 + " (+/- "+ (long)totalLatency.getDP(false) / 1000 +") us ");
            totalLatency.reset();
            System.out.println("Consensus latency = " + consensusLatency.getAverage(false) / 1000 + " (+/- "+ (long)consensusLatency.getDP(false) / 1000 +") us ");
            consensusLatency.reset();
            System.out.println("Pre-consensus latency = " + preConsLatency.getAverage(false) / 1000 + " (+/- "+ (long)preConsLatency.getDP(false) / 1000 +") us ");
            preConsLatency.reset();
            System.out.println("Pos-consensus latency = " + posConsLatency.getAverage(false) / 1000 + " (+/- "+ (long)posConsLatency.getDP(false) / 1000 +") us ");
            posConsLatency.reset();
            System.out.println("Propose latency = " + proposeLatency.getAverage(false) / 1000 + " (+/- "+ (long)proposeLatency.getDP(false) / 1000 +") us ");
            proposeLatency.reset();
            System.out.println("Weak latency = " + weakLatency.getAverage(false) / 1000 + " (+/- "+ (long)weakLatency.getDP(false) / 1000 +") us ");
            weakLatency.reset();
            System.out.println("Strong latency = " + strongLatency.getAverage(false) / 1000 + " (+/- "+ (long)strongLatency.getDP(false) / 1000 +") us ");
            strongLatency.reset();
            
            throughputMeasurementStartTime = System.currentTimeMillis();
        }

        return new byte[replySize];
    }

    public static void main(String[] args){
        if(args.length < 5) {
            System.out.println("Usage: ... ThroughputLatencyServer <processId> <measurement interval> <reply size> <state size> <context?>");
            System.exit(-1);
        }

        int processId = Integer.parseInt(args[0]);
        int interval = Integer.parseInt(args[1]);
        int replySize = Integer.parseInt(args[2]);
        int stateSize = Integer.parseInt(args[3]);
        boolean context = Boolean.parseBoolean(args[4]);

        new ThroughputLatencyServer(processId,interval,replySize, stateSize, context);        
    }

    @Override
    protected byte[] serializeState() {
        return state;
    }

    @Override
    protected void deserializeState(byte[] state) {
    }
}
