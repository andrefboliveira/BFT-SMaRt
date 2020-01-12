/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.demo.microbenchmarks;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author eduardo
 */
public class CrashRecovery {

    public static void main(String[] args) {



        /*  try {*/
        try {
            Thread.sleep(360 * 1000);
        } catch (InterruptedException ex) {
            Logger.getLogger(CrashRecovery.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("Recovery time: " + System.currentTimeMillis());
            /*
            Runtime.getRuntime().exec("./runscripts/smartrun.sh bftsmart.demo.microbenchmarks.StrongThroughputServer 3 1000 400 1000 false nosig");
        } catch (IOException ex) {
            Logger.getLogger(CrashRecovery.class.getName()).log(Level.SEVERE, null, ex);
        }*/

    }


}
