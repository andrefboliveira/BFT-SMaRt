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
import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.reconfiguration.views.View;
import bftsmart.tom.util.KeyLoader;
import bftsmart.tom.util.ReconfigThread.PartialCertificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

/**
 *
 * @author eduardo
 */
public class ViewManager {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    
    private int id;
    private Reconfiguration rec = null;
    //private Hashtable<Integer, ServerConnection> connections = new Hashtable<Integer, ServerConnection>();
    private ServerViewController controller;
    //Need only inform those that are entering the systems, as those already
    //in the system will execute the reconfiguration request
    private List<Integer> addIds = new LinkedList<Integer>();

    public ViewManager(int id, KeyLoader loader) {
        this(id, "", loader);
    }

    public ViewManager(int processId, String configHome, KeyLoader loader) {
//        this.id = processId;
        this.id = loadID(configHome);

        this.controller = new ServerViewController(id, configHome, loader);
        this.rec = new Reconfiguration(id, configHome, loader);
    }

    public void connect(){
        this.rec.connect();
    }
    
    private int loadID(String configHome) {
        try {
            String path = "";
            String sep = System.getProperty("file.separator");
            if (configHome == null || configHome.equals("")) {
                path = "config" + sep + "system.config";
            } else {
                path = configHome + sep + "system.config";
            }
            FileReader fr = new FileReader(path);
            BufferedReader rd = new BufferedReader(fr);
            String line = null;
            while ((line = rd.readLine()) != null) {
                if (!line.startsWith("#")) {
                    StringTokenizer str = new StringTokenizer(line, "=");
                    if (str.countTokens() > 1
                            && str.nextToken().trim().equals("system.ttp.id")) {
                        fr.close();
                        rd.close();
                        return Integer.parseInt(str.nextToken().trim());
                    }
                }
            }
            fr.close();
            rd.close();
            return -1;
        } catch (Exception e) {
            logger.error("Could not load ID", e);
            return -1;
        }
    }


    public void addServer(int id, String ip, int port, List<PartialCertificate> replicaCertificates) {
        this.controller.getStaticConf().addHostInfo(id, ip, port);
        rec.addServer(id, ip, port, replicaCertificates);
        addIds.add(id);
    }

    public void removeServer(int id) {
        rec.removeServer(id);
    }

    public void setF(int f) {
        rec.setF(f);
    }

    public void executeUpdates(TOMConfiguration configuration) {
        connect();
        ReconfigureReply r = rec.execute(configuration);
        View v = r.getView();
        logger.info("New view f: " + v.getF());

        VMMessage msg = new VMMessage(id, r);

        if (addIds.size() > 0) { 
            sendResponse(addIds.toArray(new Integer[1]), msg);
            addIds.clear();
        }


    }

    public void executeUpdates() {
        connect();
        ReconfigureReply r = rec.execute();
        View v = r.getView();
        logger.info("New view f: " + v.getF());

        VMMessage msg = new VMMessage(id, r);

        if (addIds.size() > 0) {
            sendResponse(addIds.toArray(new Integer[1]), msg);
            addIds.clear();
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
                if (i.intValue() != id) {
                    getConnection(i.intValue()).send(data, true);
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
