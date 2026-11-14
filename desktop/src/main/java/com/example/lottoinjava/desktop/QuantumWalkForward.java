package com.example.lottoinjava.desktop;

import com.example.lottoinjava.model.Extraction;
import java.util.*;

/** Walk-forward: ogni target usa soltanto le estrazioni precedenti. */
final class QuantumWalkForward {
 private QuantumWalkForward() {}
 static String run(List<Extraction> archive,String wheel,int window,int candidates,long seed,double sw,double iw) {
  if(window<1||candidates<1||candidates>90||sw<0||iw<0||sw+iw<=0) throw new IllegalArgumentException("Parametri o pesi non validi.");
  List<Extraction>d=new ArrayList<>(archive);d.sort(Comparator.comparingInt(Extraction::getDateKey));
  if(d.size()<=window)throw new IllegalArgumentException("Servono più di "+window+" estrazioni.");
  for(int i=1;i<d.size();i++)if(d.get(i).getDateKey()==d.get(i-1).getDateKey())throw new IllegalArgumentException("Date duplicate: "+d.get(i).getDate()+".");
  double weightSum=sw+iw; sw/=weightSum; iw/=weightSum;
  List<String> wheels=wheel.equals("Tutte")?Extraction.ALL_RUOTE:List.of(wheel); Random random=new Random(seed);
  long mh=0,rh=0,targets=0; long[] whM=new long[wheels.size()],whR=new long[wheels.size()],whN=new long[wheels.size()];
  for(int t=window;t<d.size();t++){Extraction target=d.get(t);List<Extraction>hist=d.subList(t-window,t);
   for(int wi=0;wi<wheels.size();wi++){String w=wheels.get(wi);List<Integer> actual=target.getNumbersForRuota(w);if(!valid(actual))continue;
    int[] f=new int[91],recent=new int[91],delay=new int[91];Arrays.fill(delay,window+1);boolean ok=true;
    for(int i=0;i<hist.size();i++){List<Integer> ns=hist.get(i).getNumbersForRuota(w);if(!valid(ns)){ok=false;break;}for(int n:ns){f[n]++;delay[n]=window-i;if(i>=window-20)recent[n]++;}}
    if(!ok)continue;int mf=1,mr=1,md=1;for(int n=1;n<=90;n++){mf=Math.max(mf,f[n]);mr=Math.max(mr,recent[n]);md=Math.max(md,delay[n]);}
    double[] score=new double[91];double[] re=new double[91],im=new double[91];double norm=0;
    for(int n=1;n<=90;n++){double p=(f[n]+1.0)/(window+90.0);double theta=2*Math.PI*((recent[n]+1.0)/22.0+(delay[n]%17)/17.0+(f[n]%7)/7.0);re[n]=Math.sqrt(p)*Math.cos(theta);im[n]=Math.sqrt(p)*Math.sin(theta);norm+=re[n]*re[n]+im[n]*im[n];}
    if(!Double.isFinite(norm)||norm<=0)continue;
    for(int n=1;n<=90;n++){double s=.5*f[n]/mf+.3*recent[n]/mr+.2*delay[n]/md;double q=(re[n]*re[n]+im[n]*im[n])/norm;score[n]=sw*s+iw*q*90;}
    List<Integer> rank=new ArrayList<>();for(int n=1;n<=90;n++)rank.add(n);rank.sort(Comparator.<Integer>comparingDouble(n->-score[n]).thenComparingInt(n->n));Set<Integer> picks=new HashSet<>(rank.subList(0,candidates));int hit=0;for(int n:actual)if(picks.contains(n))hit++;
    List<Integer> pool=new ArrayList<>();for(int n=1;n<=90;n++)pool.add(n);Collections.shuffle(pool,random);Set<Integer> rp=new HashSet<>(pool.subList(0,candidates));int randomHit=0;for(int n:actual)if(rp.contains(n))randomHit++;
    whM[wi]+=hit;whR[wi]+=randomHit;whN[wi]++;mh+=hit;rh+=randomHit;targets++;
   }
  }
  if(targets==0)throw new IllegalArgumentException("Nessun target valido per il walk-forward.");
  StringBuilder b=new StringBuilder("WALK-FORWARD FUORI CAMPIONE\nFinestra: "+window+" · candidati: "+candidates+" · seme casuale: "+seed+"\nPesi normalizzati: statistico "+String.format(Locale.ITALY,"%.1f%%",sw*100)+" · interferenza "+String.format(Locale.ITALY,"%.1f%%",iw*100)+"\n\nRuota | target validi | hit modello | media modello | hit casuali | media casuale\n");
  for(int i=0;i<wheels.size();i++)if(whN[i]>0)b.append(String.format(Locale.ITALY,"%s | %d | %d | %.4f | %d | %.4f%n",wheels.get(i),whN[i],whM[i],(double)whM[i]/whN[i],whR[i],(double)whR[i]/whN[i]));
  b.append(String.format(Locale.ITALY,"\nTotale: %d target-ruota · modello %.4f hit/target · baseline casuale %.4f · differenza %+.4f\nValore teorico casuale: %.4f hit (candidati × 5/90).",targets,(double)mh/targets,(double)rh/targets,(double)(mh-rh)/targets,candidates*5.0/90.0));
  b.append("\n\nLIMITI: una sola sequenza casuale con seme fisso; intervalli di confidenza e ripetizioni Monte Carlo non inclusi. Differenze descrittive, non prova di capacità predittiva. Congelare i parametri prima di un periodo di test indipendente.");return b.toString();
 }
 private static boolean valid(List<Integer> ns){return ns!=null&&ns.size()==5&&new HashSet<>(ns).size()==5&&ns.stream().allMatch(n->n!=null&&n>=1&&n<=90);}
}
