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

import bftsmart.reconfiguration.util.ReconfigThread.pojo.FullCertificate;
import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.tom.util.KeyLoader;

/**
 * This class is used by the trusted client to add and remove replicas from the group
 */

public class VMServices {
    
    private KeyLoader keyLoader;
    private String configDir;
    
    /**
     * Constructor. It adopts the default RSA key loader and default configuration path.
     */
    public VMServices() { // for the default keyloader and provider
        
        keyLoader = null;
        configDir = "";
    }
    
    /**
     * Constructor.
     * 
     * @param keyLoader Key loader to use to fetch keys from disk
     * @param configDir Configuration path
     */
    public VMServices(KeyLoader keyLoader, String configDir) {
        
        this.keyLoader = keyLoader;
        this.configDir = configDir;
    }
    
    /**
     * Adds a new server to the group
     * @param id ID of the server to be added (needs to match the value in config/hosts.config)
     * @param ipAddress IP address of the server to be added (needs to match the value in config/hosts.config)
     * @param port Port of the server to be added (needs to match the value in config/hosts.config)
     * @param toReconfigureReplicaConfig
     * @param fullCertificate
     */
    public void addServer(int id, String ipAddress, int port, TOMConfiguration toReconfigureReplicaConfig, FullCertificate fullCertificate) {

        ViewManager viewManager = new ViewManager(toReconfigureReplicaConfig.getProcessId(), toReconfigureReplicaConfig.getTTPId(), configDir, keyLoader);

        viewManager.addServer(id, ipAddress, port, fullCertificate);

        execute(viewManager, toReconfigureReplicaConfig);

    }
    
    /**
     * Removes a server from the group
     * 
     * @param id ID of the server to be removed 
     */
    public void removeServer(int id, TOMConfiguration toReconfigureReplicaConfig) {

        ViewManager viewManager = new ViewManager(toReconfigureReplicaConfig.getProcessId(), toReconfigureReplicaConfig.getTTPId(), keyLoader);

        viewManager.removeServer(id);

        execute(viewManager, toReconfigureReplicaConfig);

    }

    public void forceRemoveServer(int id, TOMConfiguration toReconfigureReplicaConfig) {

        ViewManager viewManager = new ViewManager(toReconfigureReplicaConfig.getProcessId(), toReconfigureReplicaConfig.getTTPId(), keyLoader);

        viewManager.forceRemoveServer(id);

        execute(viewManager, toReconfigureReplicaConfig);

    }


    private void execute(ViewManager viewManager, TOMConfiguration toReconfigureReplicaConfig) {

        viewManager.executeUpdates(toReconfigureReplicaConfig);
        
        viewManager.close();
    }
}