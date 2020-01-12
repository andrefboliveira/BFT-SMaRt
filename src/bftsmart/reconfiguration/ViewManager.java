/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package bftsmart.reconfiguration;

import bftsmart.communication.server.ServerConnection;
import bftsmart.reconfiguration.util.ReconfigThread.pojo.FullCertificate;
import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.reconfiguration.views.View;
import bftsmart.tom.util.KeyLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author eduardo
 */
public class ViewManager {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private int idTTP;
    private Reconfiguration rec = null;
    //private Hashtable<Integer, ServerConnection> connections = new Hashtable<Integer, ServerConnection>();
    private ServerViewController controller;
    //Need only inform those that are entering the systems, as those already
    //in the system will execute the reconfiguration request
    private List<Integer> addIds = new LinkedList<Integer>();

    public ViewManager(int reconfiguredReplicaID, int idTTP, KeyLoader loader) {
        this(reconfiguredReplicaID, idTTP, "", loader);
    }

    public ViewManager(int reconfiguredReplicaID, int idTTP, String configHome, KeyLoader loader) {
        this.idTTP = idTTP;
        this.controller = new ServerViewController(this.idTTP, configHome, loader);
        this.rec = new Reconfiguration(reconfiguredReplicaID, this.idTTP, configHome, loader);
    }

    public void connect(){
        this.rec.connect();
    }

    public void addServer(int id, String ip, int port, FullCertificate fullCertificate) {
        //Fix me!!! It is necessary to use the ports below as in the hosts.config file
        this.controller.getStaticConf().addHostInfo(id, ip, port, port + 1);
        rec.addServer(id, ip, port, fullCertificate);
        addIds.add(id);
    }

    public void removeServer(int id) {
        rec.removeServer(id);
    }

    public void forceRemoveServer(int id) {
        rec.forceRemoveServer(id);
    }

    /*public void setF(int f) {
        rec.setF(f);
    }*/

    public void executeUpdates(TOMConfiguration toReconfigureReplicaConfig) {
        connect();
        ReconfigureReply r = rec.execute(toReconfigureReplicaConfig);

        if (r != null) {
            View v = r.getView();
            logger.info("New view f: " + v.getF());

            VMMessage msg = new VMMessage(idTTP, r);

            if (addIds.size() > 0) {
                sendResponse(addIds.toArray(new Integer[1]), msg);
                addIds.clear();
            }
        }

    }

    private ServerConnection getConnection(int remoteId) {
         return new ServerConnection(controller, null, remoteId, null, null);
    }

    public void sendResponse(Integer[] targets, VMMessage sm) {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();

        try {
            new ObjectOutputStream(bOut).writeObject(sm);
        } catch (IOException ex) {
            logger.error("Could not serialize message", ex);
        }

        byte[] data = bOut.toByteArray();

        for (Integer i : targets) {
            //br.ufsc.das.tom.util.Logger.println("(ServersCommunicationLayer.send) Sending msg to replica "+i);
            try {
                if (i.intValue() != idTTP) {
                    //getConnection(i.intValue()).send(data, true);
                    getConnection(i.intValue()).send(data);
                }
            } catch (InterruptedException ex) {
               // ex.printStackTrace();
                logger.error("Failed to send data to target", ex);
            }
        }
        //br.ufsc.das.tom.util.Logger.println("(ServersCommunicationLayer.send) Finished sending messages to replicas");
    }

    public void close() {
        rec.close();
    }
}
