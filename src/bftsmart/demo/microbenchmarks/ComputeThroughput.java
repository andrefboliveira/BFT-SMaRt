/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.demo.microbenchmarks;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.LinkedList;
import java.util.StringTokenizer;

/**
 *
 * @author eduardo
 */
public class ComputeThroughput {
    
    public static void main(String[] args) {

        /*File f = new File(args[0]);
        if (!f.exists()) {
            System.exit(0);
        }*/
        load(args[0]);

    }

    public static void load(String path) {
        //System.out.println("Vai ler!!!");
        try {

            FileReader fr = new FileReader(path);

            BufferedReader rd = new BufferedReader(fr);
            String line = null;
            //int j = 0;
            LinkedList<Double> l = new LinkedList<Double>();
            
            while (((line = rd.readLine()) != null)) {
                StringTokenizer st = new StringTokenizer(line, "\t");
                try {
                    st.nextToken();
                    st.nextToken();
                    double d = Double.parseDouble(st.nextToken());
                    l.add(d);
                           
                } catch (Exception e) {
                    e.printStackTrace();
                    //e.printStackTrace();
                }

            }
            fr.close();
            rd.close();

            //System.out.println("Size: " + l.size());

            //double sum = 0;
            
            
            double[] values = new double[l.size()];
            for(int i = 0; i < values.length; i++){
                values[i] = l.get(i);
                System.out.println("value: "+values[i]);
            }
            
            
            
            
            
            //System.out.println("Sum: " + sum);
            System.out.println("Throughput: " + computeAverage(values,true));
            System.out.println("DP: " + computeDP(values,true));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
     private static double computeAverage(double[] values, boolean percent){
        java.util.Arrays.sort(values);
        int limit = 0;
        if(percent){
            limit = values.length/10;
        }
        double count = 0;
        for(int i = limit; i < values.length - limit;i++){
            count = count + values[i];
        }
        return (double) count/ (double) (values.length - 2*limit);
    }
     
    private static double computeDP(double[] values, boolean percent){
        if(values.length <= 1){
            return 0;
        }
        java.util.Arrays.sort(values);
        int limit = 0;
        if(percent){
            limit = values.length/10;
        }
        long num = 0;
        double med = computeAverage(values,percent);
        double quad = 0;
        
        for(int i = limit; i < values.length - limit;i++){
            num++;
            quad = quad + values[i]*values[i]; //Math.pow(values[i],2);
        }
        double var = (quad - (num*(med*med)))/(num-1);
        ////br.ufsc.das.util.Logger.println("mim: "+values[limit]);
        ////br.ufsc.das.util.Logger.println("max: "+values[values.length-limit-1]);
        return Math.sqrt(var);
    }

}
