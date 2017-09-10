package com.rescale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.lang.StringBuilder;
import java.util.concurrent.TimeUnit;
import java.lang.InterruptedException;
import org.jsoup.Connection;
import java.util.HashSet;
import java.util.ArrayList;

public class Crawler{

  private HashSet<String> visitedUrls;
  private ArrayList<String> toVisitUrls;
  private Integer count;
  private final int MAX_THREADS = 5;

  public Crawler(){
    visitedUrls = new HashSet<String>();
    toVisitUrls = new ArrayList<String>();
    count = 0;
  }

  public static void main(String args[]) throws Exception{
    if(args.length!=1){
      System.out.println("Please provide the URL to be crawled as argument to program");
      System.exit(0);
    }
  
    Crawler webCrawler = new Crawler();
    webCrawler.crawl(args[0]);
  }

  /*This function is the starting point to crawl through a url
   *It spaws a fixed threapool, the threads parallely crawl links 
   *accumulated in toVisitUrls list 
   * @param url : URL to be crawled 
   */
  private void crawl(String url) throws Exception{
    
    int flag = 0;
    toVisitUrls.add(url);
    ThreadPoolExecutor executor = 
      (ThreadPoolExecutor)Executors.newFixedThreadPool(MAX_THREADS);
    initiateThreads(executor);

      /*
       *Wait until all links are crawled then shutdown executor
       */
       
    while(flag == 0){
      synchronized(toVisitUrls){    
        if(!(count==0  && toVisitUrls.isEmpty())){
          toVisitUrls.wait();
        }
        else{
          flag = 1;
          toVisitUrls.notifyAll();
        }
      }
    }
        executor.shutdownNow();
  }

  /*
   *initiates the threads in the threadpool
   *@param executor : ThreadPoolExecutor
   */
  private void initiateThreads(ThreadPoolExecutor executor){

    for(int i = 0; i < MAX_THREADS; i++){
      WorkerThread task = new WorkerThread();
      executor.execute(task);
    }        
  }

  private class WorkerThread implements Runnable{
    private String url;

    public WorkerThread(){
      url = null;
    }
       
    
    /*This function picks urls from the toVisitUrl list in an infinite loop
     *and crawl each url to collect links
     *@param void
     */
    @Override
    public void run(){
    
      outer:
        while(true){
        StringBuilder linkCollection = new StringBuilder();
        url = null;
        while(url == null){
          synchronized(toVisitUrls){
            if(toVisitUrls.isEmpty()){
              try{
                if(count!=0)
                  toVisitUrls.wait();
                else{
                  toVisitUrls.notifyAll();
                  break outer;
                }
              }catch(InterruptedException e){
                continue;
              }
            }
            else{
              url = toVisitUrls.remove(0);
              visitedUrls.add(url);
              count++;
            }
          }
        }
        
        linkCollection.append(url);
        collectUrls(linkCollection);

        synchronized(toVisitUrls){
          System.out.println(linkCollection.toString());
          count--;
          toVisitUrls.notifyAll();
        }
      }
    }


      /*This function connects to a webpage parses it for anchor tags
       *and collects all http or https links from it in toVisitUrl list
     * @param url : URL to be crawled 
     */
    private void collectUrls(StringBuilder linkCollection){
      Document doc = null;
      try{
        Connection connection = Jsoup.connect(url);
        connection.userAgent("Mozilla/5.0");
        //set timeout to 100 seconds
        connection.timeout(10 * 10000);
        doc = connection.get();
      }catch(Exception e){
        /*e.printStackTrace(); */ //For debugging purposes 
        return; 
      }

      Elements links = doc.getElementsByTag("a");
      for (Element link : links) {

        String linkHref = link.attr("href");
        if((linkHref.startsWith("http://")|| 
          linkHref.startsWith("https://"))){
    
          linkCollection.append("\n ");
          linkCollection.append(linkHref);
          synchronized(toVisitUrls){
            if(!(visitedUrls.contains(linkHref) || 
              toVisitUrls.contains(linkHref))){
              toVisitUrls.add(linkHref);
              toVisitUrls.notifyAll();
            }
          }
        }
      }
    }
  }
}