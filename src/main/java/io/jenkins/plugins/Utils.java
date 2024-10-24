/*
 * The MIT License
 *
 * Copyright 2020 Dassault Systèmes.
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

import static io.jenkins.plugins.ReqtifyGenerateReport.readAll;

import hudson.FilePath;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;
import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import jenkins.model.Jenkins;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.kohsuke.stapler.Stapler;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * @author Dassault Systèmes
 */
public class Utils {

    public static String findReqtifyPath() throws IOException {
        String path;
        Process proc = Runtime.getRuntime().exec("reg query HKCR\\Reqtify.Application\\CLSID");
        InputStream in = proc.getInputStream();
        path = readAll(in);
        in.close();
        String key = path.substring(path.indexOf('{') + 1, path.indexOf('}'));
        String localServer32Path = null;
        proc = Runtime.getRuntime().exec("reg query HKCR\\CLSID\\{" + key + "}\\LocalServer32");
        in = proc.getInputStream();
        localServer32Path = readAll(in);
        in.close();

        if (localServer32Path.isEmpty()) {
            proc = Runtime.getRuntime().exec("reg query HKCR\\WOW6432Node\\CLSID\\{" + key + "}\\LocalServer32");
            in = proc.getInputStream();
            localServer32Path = readAll(in);
            in.close();
        }
        if (localServer32Path.contains("\"")) {
            path = localServer32Path.substring(
                    localServer32Path.indexOf('"') + 1,
                    localServer32Path.indexOf('"', localServer32Path.indexOf('"') + 1));
            return path;
        } else {
            throw new IOException(
                    "Reqtify path not found tried path: HKCR\\\\CLSID\\\\{\" + key + \"}\\\\LocalServer32\\ and HKCR\\\\WOW6432Node\\\\CLSID\\\\{\" + key + \"}\\\\LocalServer32");
        }
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
    public Object executeGET(String targetURL, Process reqtifyProcess, boolean buildRequest)
            throws ParseException, IOException, ReqtifyException {
        HttpURLConnection connection = null;
        Object result = null;
        boolean isConnected = false;
        int MAX_CONNECTIONS_REQUESTS = 20;
        int count = 0;
        InputStreamReader isr = null;
        BufferedReader br = null;
        while (!isConnected) {
            try {
                // Create connection
                URL url = new URL(targetURL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Cookie", ReqtifyData.cookie);

                if (connection.getResponseCode() != 200) {
                    if (connection.getErrorStream() != null) {
                        isr = new InputStreamReader(connection.getErrorStream(), "UTF-8");
                        br = new BufferedReader(isr);
                        StringBuilder errorResponse = new StringBuilder();
                        String line = "";
                        errorResponse.append(line);
                        if (!buildRequest) {
                            while ((line = br.readLine()) != null) {
                                errorResponse.append(line).append("<br>");
                            }
                        } else {
                            while ((line = br.readLine()) != null) {
                                errorResponse.append(line).append("\n");
                            }
                        }
                        throw new ReqtifyException(errorResponse.toString());
                    } else {
                        throw new ReqtifyException(connection.getResponseMessage());
                    }
                }
                ReqtifyData.cookie = connection.getHeaderField("Set-Cookie");
                isConnected = true;
                isr = new InputStreamReader((connection.getInputStream()), "UTF-8");
                br = new BufferedReader(isr);

                if (targetURL.contains("openProject")) {
                    return result;
                }

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                JSONParser parser = new JSONParser();
                result = parser.parse(response.toString());
                if (result.getClass().getName().contains("JSONArray")) {
                    result = (JSONArray) parser.parse(response.toString());
                } else if (result.getClass().getName().contains("JSONObject")) {
                    result = (JSONObject) parser.parse(response.toString());
                } else if (result.getClass().getName().contains("String")) {
                    result = (String) parser.parse(response.toString());
                }

            } catch (MalformedURLException e) {
                throw new MalformedURLException();
            } catch (IOException e) {
                if (!reqtifyProcess.isAlive() && reqtifyProcess.exitValue() == 1) {
                    // Normal termination of Reqtify
                    throw new ReqtifyException(""); // Abnormal termination of Reqtify
                }
                isConnected = false;
                count++;

                if (count > MAX_CONNECTIONS_REQUESTS) {
                    // Write error
                    throw new ConnectException();
                }
            } finally {
                if (connection != null) connection.disconnect();

                if (isr != null) isr.close();
                if (br != null) br.close();
            }
        }
        return result;
    }

    public boolean isLocalPortFree(int port) {
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

    public boolean isReqtifyProjectExistInWorkspace(FilePath file, String search)
            throws IOException, InterruptedException {
        if (file.isDirectory()) {
            List<FilePath> files = file.list();
            Iterator itr = files.iterator();
            boolean found;
            while (itr.hasNext()) {
                found = isReqtifyProjectExistInWorkspace((FilePath) itr.next(), search);
                if (found) return true;
            }
        } else {
            if (file.getName().endsWith(search)) {
                return true;
            }
        }
        return false;
    }

    public String getLastLineOfFile(String path) {
        Scanner scanner;
        try {
            scanner = new Scanner(new File(path), "utf-8");
        } catch (FileNotFoundException e) {
            return ("File not found");
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

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public static List getFunctionArgumentsData(String currentJob, boolean report) {
        List selectedArguments = new ArrayList();
        try {
            File file = Paths.get(Jenkins.get().getRootPath() + "\\jobs\\" + currentJob, "config.xml")
                    .toFile();
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document document = db.parse(file);

            Node argumentListNode = report == true
                    ? document.getElementsByTagName("reportArgumentList").item(0)
                    : document.getElementsByTagName("argumentList").item(0);
            if (argumentListNode != null) {
                NodeList childs = argumentListNode.getChildNodes();
                for (int i = 0; i < childs.getLength(); i++) {
                    Node node = childs.item(i);
                    if (node.getNodeName().equals("string")) selectedArguments.add(node.getTextContent());
                }
            }
        } catch (FileNotFoundException ex) {
            return selectedArguments;
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            return selectedArguments;
        }
        return selectedArguments;
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public static String getSavedFunctionName(String currentJob) {
        String functionName = "";
        try {
            File file = Paths.get(Jenkins.get().getRootPath() + "\\jobs\\" + currentJob, "config.xml")
                    .toFile();
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document document = db.parse(file);

            Node functionNameNode =
                    document.getElementsByTagName("functionName").item(0);
            return (functionNameNode != null) ? functionNameNode.getTextContent() : functionName;
        } catch (FileNotFoundException ex) {
            return functionName;
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            return functionName;
        }
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public static String getSavedReportName(String currentJob) {
        String reportName = "";
        try {
            File file = Paths.get(Jenkins.get().getRootPath() + "\\jobs\\" + currentJob, "config.xml")
                    .toFile();
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document document = db.parse(file);

            Node reportNameNode = document.getElementsByTagName("modelReport").item(0);
            return (reportNameNode != null) ? reportNameNode.getTextContent() : reportName;
        } catch (FileNotFoundException ex) {
            return reportName;
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            return reportName;
        }
    }

    public static String getWorkspacePath(String currentJob) throws UnsupportedEncodingException {
        String currentWorkspace = "";
        currentJob = URLDecoder.decode(currentJob, "UTF-8");
        currentWorkspace = Jenkins.get().getRootPath() + "\\workspace\\" + currentJob;
        /*if(currentWorkspace.contains(" ")) {
        currentWorkspace = URLEncoder.encode(currentWorkspace, "UTF-8");
        }*/

        // Create workspace folder if not exists
        File wsDirectory = Paths.get(currentWorkspace).toFile();
        boolean wsExists = wsDirectory.exists();
        if (!wsExists) {
            boolean created = wsDirectory.mkdirs();
            if (!created) return currentWorkspace;
        }
        return currentWorkspace;
    }

    public static void initReqtifyProcess() throws IOException {
        String reqtifyLang = "eng";
        int reqtifyPort;
        String reqtifyPath = findReqtifyPath();
        int maxWaitTime = 60000; // Maximum wait time in milliseconds (60 seconds)
        int pollInterval = 5000; // Poll interval in milliseconds (5 seconds)
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < maxWaitTime) {
            try {
                // Attempt to initialize the Reqtify process
                if (ReqtifyData.reqtfyLanguageProcessMap.isEmpty()) {
                    reqtifyPort = ReqtifyData.utils.nextFreePort(4000, 8000);
                    String[] args = {
                        reqtifyPath,
                        "-http",
                        String.valueOf(reqtifyPort),
                        "-logfile",
                        ReqtifyData.tempDir + "reqtifyLog_" + reqtifyPort + ".log",
                        "-l",
                        reqtifyLang,
                        "-timeout",
                        ReqtifyData.reqtifyTimeoutValue
                    };
                    Process proc = Runtime.getRuntime().exec(args);
                    ReqtifyData.reqtfyLanguageProcessMap.put(reqtifyLang, proc);
                    ReqtifyData.reqtifyLanguagePortMap.put(reqtifyLang, reqtifyPort);
                    break;
                } else if (!ReqtifyData.reqtfyLanguageProcessMap.containsKey(reqtifyLang)) {
                    reqtifyPort = ReqtifyData.utils.nextFreePort(4000, 8000);
                    String[] args = {
                        reqtifyPath,
                        "-http",
                        String.valueOf(reqtifyPort),
                        "-logfile",
                        ReqtifyData.tempDir + "reqtifyLog_" + reqtifyPort + ".log",
                        "-l",
                        reqtifyLang,
                        "-timeout",
                        ReqtifyData.reqtifyTimeoutValue
                    };
                    Process proc = Runtime.getRuntime().exec(args);
                    ReqtifyData.reqtfyLanguageProcessMap.put(reqtifyLang, proc);
                    ReqtifyData.reqtifyLanguagePortMap.put(reqtifyLang, reqtifyPort);
                    break;
                } else {
                    reqtifyPort = ReqtifyData.reqtifyLanguagePortMap.get(reqtifyLang);
                    if (ReqtifyData.utils.isLocalPortFree(reqtifyPort)) {
                        ReqtifyData.reqtfyLanguageProcessMap.remove(reqtifyLang);
                        ReqtifyData.reqtifyLanguagePortMap.remove(reqtifyLang);
                        reqtifyPort = ReqtifyData.utils.nextFreePort(4000, 8000);
                        String[] args = {
                            reqtifyPath,
                            "-http",
                            String.valueOf(reqtifyPort),
                            "-logfile",
                            ReqtifyData.tempDir + "reqtifyLog_" + reqtifyPort + ".log",
                            "-l",
                            reqtifyLang,
                            "-timeout",
                            ReqtifyData.reqtifyTimeoutValue
                        };
                        Process proc = Runtime.getRuntime().exec(args);
                        ReqtifyData.reqtfyLanguageProcessMap.put(reqtifyLang, proc);
                        ReqtifyData.reqtifyLanguagePortMap.put(reqtifyLang, reqtifyPort);
                        break;
                    }
                }
            } catch (IOException e) {
                System.err.println(
                        "Waiting to connect Reqtify server, retrying in " + (pollInterval / 1000) + " seconds...");
                try {
                    Thread.sleep(pollInterval);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); // Restore interrupted status
                    throw new IOException(ie);
                }
            }
        }
    }
}
