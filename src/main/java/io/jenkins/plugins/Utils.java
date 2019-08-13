/*
 * The MIT License
 *
 * Copyright 2019 NKR8.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.jenkins.plugins;
import hudson.FilePath;
import static io.jenkins.plugins.ReqtifyGenerateReport.readAll;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;
import javax.servlet.http.HttpServletRequest;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.kohsuke.stapler.Stapler;

/**
 *
 * @author NKR8
 */
public class Utils {
    	/**
	 * We find the path where reqtify is installed with regedit and return it.
	 * 
	 * @return Return the path of reqtify.exe (for windows only)
         * @throws java.io.IOException
	 * @since 1.0
	 */
	public String findReqtifyPath() throws IOException {
		String path;
		Process proc=Runtime.getRuntime().exec("reg query HKCR\\Reqtify.Application\\CLSID");
                InputStream in = proc.getInputStream();
                path=readAll(in);
                in.close();
                String key = path.substring(path.indexOf('{')+1,path.indexOf('}'));
                proc=Runtime.getRuntime().exec("reg query HKCR\\WOW6432Node\\CLSID\\{"+key+"}\\LocalServer32");
                in = proc.getInputStream();
                path=readAll(in);
                in.close();
                path = path.substring(path.indexOf('"')+1,path.indexOf('"', path.indexOf('"')+1));
                return path;
	}
        
        public JSONArray executeGET(String targetURL, Process reqtifyProcess, boolean buildRequest) throws ParseException, IOException, ReqtifyException {
              HttpURLConnection connection = null;
              JSONArray result = null;
              boolean isConnected = false;
              int MAX_CONNECTIONS_REQUESTS = 20;
              int count = 0;
              InputStreamReader isr = null;
              BufferedReader br = null;
              while(!isConnected) {
               try {
                //Create connection                
                URL url = new URL(targetURL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Content-Type", "application/json");

                if (connection.getResponseCode() != 200) {
                    
                    if(connection.getResponseCode() == 404)
                         throw new ReqtifyException("No Reqtify project in the working directory");
                    else
                        throw new ReqtifyException(connection.getResponseMessage());
                }

                isConnected = true;      
                isr = new InputStreamReader((connection.getInputStream()),"UTF-8");
                br = new BufferedReader(isr);                
                                                              
                if(!buildRequest) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                            response.append(line);
                    }                    
                    JSONParser parser = new JSONParser();
                    result = (JSONArray)parser.parse(response.toString());                
                } else {
                    result = new JSONArray();
                }
              } catch (MalformedURLException e) {
                  throw new MalformedURLException();
              } catch (IOException e) {                  
                    if(!reqtifyProcess.isAlive()) {
                        throw new ReqtifyException("");
                    }
                    isConnected = false;  
                    count++;
                    
                    if(count > MAX_CONNECTIONS_REQUESTS) {
                        //Write error 
                        throw new ConnectException();
                    }
              } finally {
                   if(connection != null) 
                    connection.disconnect();
                    
                   if(isr != null)
                       isr.close();
                   if(br != null)
                       br.close();
               }  
            }                      
        return result;
    }   

    private boolean isLocalPortFree(int port) {
        try {
            new ServerSocket(port).close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }                
    public int nextFreePort(int from, int to) {
        int port;
        while (true) {
            port = ThreadLocalRandom.current().nextInt(from, to);
            if (isLocalPortFree(port)) {
                return port;
            } 
        }
    }         
        
    public boolean isReqtifyProjectExistInWorkspace(FilePath file, String search) throws IOException, InterruptedException {
        if (file.isDirectory()) {
            List<FilePath> files = file.list();
            Iterator itr = files.iterator();
            boolean found;
            while(itr.hasNext()){
                found = isReqtifyProjectExistInWorkspace((FilePath)itr.next(), search);
                if (found)
                    return true;
            }                
        } else {
            if (file.getName().endsWith(search)) {
                return true;
            }
        }
        return false;
    }
    
    	/**
	 * Return the last line of file like a log file.
	 * 
	 * @param path the path of the file we want to read
	 * @return Return the last line of file
	 * @since 1.0
	 */
	public String getLastLineOfFile(String path) {
		Scanner scanner;
		try {
			scanner = new Scanner(new File(path),"utf-8");
		} catch (FileNotFoundException e) {
			return("File not found");
		}
	    StringBuilder error = new StringBuilder();
	    while (scanner.hasNextLine()) {
	        error.append(scanner.nextLine()).append("<br>");
	    }
            
	    scanner.close();
		return error.toString();
	}
        
        public String getBrowserLanguage() {
            HttpServletRequest req = Stapler.getCurrentRequest();
            Locale currentLocale = req.getLocale();
            return currentLocale.toLanguageTag().toLowerCase();
        }
}
