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

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.script.ScriptException;
import jenkins.model.Jenkins;
import org.acegisecurity.AccessDeniedException;
import org.apache.commons.io.FilenameUtils;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.bind.JavaScriptMethod;
/**
 *
 * @author 3DS
 */
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")

public class ReportGenerationPipelineStep extends Step {

    private static final Logger logger = Logger.getLogger(ReportGenerationPipelineStep.class.getName());
    
    private static String nameReport;
    private static String modelReport;
    private static String templateReport;
    private static String lang;
        

    @Nonnull
    public String getNameReport() {
            return ReportGenerationPipelineStep.nameReport;
    }

    /**
     * @return stored model of the report
     * @since 1.0
     */
    public String getModelReport() {
            return ReportGenerationPipelineStep.modelReport;
    }

    /**
     * @return stored template of the report
     * @since 1.0
     */
    public String getTemplateReport() {
            return ReportGenerationPipelineStep.templateReport;
    }

    /**
     * @return stored language of the report
     * @since 1.0
     */
    public String getLang() {
            return ReportGenerationPipelineStep.lang;
    }
    
    @DataBoundSetter
    public void setNameReport(@Nonnull String nameReport) {
            ReportGenerationPipelineStep.nameReport = nameReport;
    }

    @DataBoundSetter
    public void setModelReport(String modelReport) {
            ReportGenerationPipelineStep.modelReport = modelReport;
    }

    @DataBoundSetter
    public void setTemplateReport(String templateReport) {
            ReportGenerationPipelineStep.templateReport = templateReport;
    }    

    @DataBoundConstructor
    public ReportGenerationPipelineStep(String nameReport, String modelReport, String templateReport, String lang) {
            ReportGenerationPipelineStep.nameReport = nameReport;
            ReportGenerationPipelineStep.modelReport = modelReport;
            ReportGenerationPipelineStep.templateReport = templateReport;
    }    
    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new ReportGenerationPipelineStepExecution(this, context); //To change body of generated methods, choose Tools | Templates.
    }
	
    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        private String reqtifyError;
        private int reqtifyPort;
        private final String reqtifyPath = ReqtifyData.utils.findReqtifyPath();
        public DescriptorImpl() throws IOException, InterruptedException, ScriptException {
        }        
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, TaskListener.class);
        }
        
        @Override
        public String getFunctionName() {
            return "reqtifyReport";
        }

        @JavaScriptMethod
        public String getReqtifyError() {
            return reqtifyError;
        }    
            
        @Nonnull
        @Override
        public String getDisplayName() {
            return io.jenkins.plugins.Messages.ReqtifyGenerateReport_DisplayName();
        }

        public ListBoxModel doFillModelReportItems() throws InterruptedException, IOException {
            ListBoxModel m = new ListBoxModel();
            synchronized(ReqtifyData.class) {
                try {                                                
                    FilePath currentWorkspacePath;
                    String currentJob = "";
                    reqtifyError = "";
                    String currentWorkspace = "";
                    Pattern pattern = Pattern.compile("job/(.*?)/pipeline-syntax/descriptorByName");
                    Matcher matcher = pattern.matcher(Jenkins.get().getDescriptor().getDescriptorFullUrl());
                    while (matcher.find()) {
                        currentJob = matcher.group(1);
                    }

                    currentWorkspacePath = Jenkins.get().getWorkspaceFor(Jenkins.get().getItem(currentJob));                    

                    if(currentWorkspacePath != null && currentWorkspacePath.exists()) {
                        currentWorkspace = currentWorkspacePath.getRemote();                                     
                        if(currentWorkspace.contains(" ")) {
                          currentWorkspace = URLEncoder.encode(currentWorkspace, "UTF-8");
                        } 
                    } else {
                        currentWorkspace = "null";
                    }

                    String reqtifyLang = "eng";
                    Process reqtifyProcess;
                    if(ReqtifyData.reqtfyLanguageProcessMap.isEmpty()) {
                        //No reqtify is started
                        reqtifyPort = ReqtifyData.utils.nextFreePort(4000,8000);
                        String[] args = {reqtifyPath,"-http",String.valueOf(reqtifyPort),"-logfile",ReqtifyData.tempDir+"reqtifyLog_"+reqtifyPort+".log", "-l", reqtifyLang, "-timeout",ReqtifyData.reqtifyTimeoutValue};
                        Process proc = Runtime.getRuntime().exec(args);                             
                        reqtifyProcess = proc;
                        ReqtifyData.reqtfyLanguageProcessMap.put(reqtifyLang, proc);
                        ReqtifyData.reqtifyLanguagePortMap.put(reqtifyLang, reqtifyPort);

                    } else if(!ReqtifyData.reqtfyLanguageProcessMap.containsKey(reqtifyLang)) {
                        //No Reqtify is started for this language
                        reqtifyPort = ReqtifyData.utils.nextFreePort(4000,8000);
                        String[] args = {reqtifyPath,"-http",String.valueOf(reqtifyPort),"-logfile",ReqtifyData.tempDir+"reqtifyLog_"+reqtifyPort+".log", "-l", reqtifyLang, "-timeout",ReqtifyData.reqtifyTimeoutValue};
                        Process proc = Runtime.getRuntime().exec(args);                             
                        reqtifyProcess = proc;
                        ReqtifyData.reqtfyLanguageProcessMap.put(reqtifyLang, proc);
                        ReqtifyData.reqtifyLanguagePortMap.put(reqtifyLang, reqtifyPort);
                    } else {                            
                        reqtifyProcess = ReqtifyData.reqtfyLanguageProcessMap.get(reqtifyLang);                            
                        reqtifyPort = ReqtifyData.reqtifyLanguagePortMap.get(reqtifyLang);  

                        if(ReqtifyData.utils.isLocalPortFree(reqtifyPort)) {
                            //Reqtify stopped normally
                            ReqtifyData.reqtfyLanguageProcessMap.remove(reqtifyLang);
                            ReqtifyData.reqtifyLanguagePortMap.remove(reqtifyLang);
                            reqtifyPort = ReqtifyData.utils.nextFreePort(4000,8000);
                            String[] args = {reqtifyPath,"-http",String.valueOf(reqtifyPort),"-logfile",ReqtifyData.tempDir+"reqtifyLog_"+reqtifyPort+".log", "-l", reqtifyLang, "-timeout",ReqtifyData.reqtifyTimeoutValue};
                            Process proc = Runtime.getRuntime().exec(args);                             
                            reqtifyProcess = proc;
                            ReqtifyData.reqtfyLanguageProcessMap.put(reqtifyLang, proc);
                            ReqtifyData.reqtifyLanguagePortMap.put(reqtifyLang, reqtifyPort);                                
                        }
                    }

                    String targetURLModels = "http://localhost:"+reqtifyPort+"/jenkins/getReportModels?dir="+currentWorkspace;
                    try {
                        JSONArray modelsResult = ReqtifyData.utils.executeGET(targetURLModels, reqtifyProcess,false);
                        Iterator<JSONObject> itr = modelsResult.iterator();                        
                        //Models
                        while (itr.hasNext()) {
                            JSONObject model = (JSONObject) itr.next();
                            m.add(model.get("label").toString());                            
                        }
  
                    } catch (ParseException ex) {
                        Logger.getLogger(ReqtifyGenerateReport.class.getName()).log(Level.SEVERE, null, ex);
                    }  catch (ConnectException ce) {
                        //Show some error
                    }  catch (ReqtifyException re) {
                        if(re.getMessage().length() > 0) {
                            reqtifyError = re.getMessage();
                        } else {  
                            Process p = ReqtifyData.reqtfyLanguageProcessMap.get(reqtifyLang);
                            if(p.isAlive())
                                p.destroy();

                            ReqtifyData.reqtfyLanguageProcessMap.remove(reqtifyLang);
                            ReqtifyData.reqtifyLanguagePortMap.remove(reqtifyLang);
                            reqtifyError = ReqtifyData.utils.getLastLineOfFile(ReqtifyData.tempDir+"reqtifyLog_"+reqtifyPort+".log");
                        } 
                    }                                      
                } catch(IOException | InterruptedException | AccessDeniedException e) {
                }    
                
                return m;
            }                     
        }
        
        public ListBoxModel doFillTemplateReportItems() throws IOException, InterruptedException {
            ListBoxModel m = new ListBoxModel();  
            synchronized(ReqtifyData.class) {
                    try {                                                
                        FilePath currentWorkspacePath;
                        String currentJob = "";
                        reqtifyError = "";
                        String currentWorkspace = "";
                        Pattern pattern = Pattern.compile("job/(.*?)/pipeline-syntax/descriptorByName");
                        Matcher matcher = pattern.matcher(Jenkins.get().getDescriptor().getDescriptorFullUrl());
                        while (matcher.find()) {
                            currentJob = matcher.group(1);
                        }

                        currentWorkspacePath = Jenkins.get().getWorkspaceFor(Jenkins.get().getItem(currentJob));                    
                        
                        if(currentWorkspacePath != null && currentWorkspacePath.exists()) {
                            currentWorkspace = currentWorkspacePath.getRemote();                                     
                            if(currentWorkspace.contains(" ")) {
                              currentWorkspace = URLEncoder.encode(currentWorkspace, "UTF-8");
                            } 
                        } else {
                            currentWorkspace = "null";
                        }
                        
                        String reqtifyLang = "eng";
                        Process reqtifyProcess;
                        if(ReqtifyData.reqtfyLanguageProcessMap.isEmpty()) {
                            //No reqtify is started
                            reqtifyPort = ReqtifyData.utils.nextFreePort(4000,8000);
                            String[] args = {reqtifyPath,"-http",String.valueOf(reqtifyPort),"-logfile",ReqtifyData.tempDir+"reqtifyLog_"+reqtifyPort+".log", "-l", reqtifyLang, "-timeout",ReqtifyData.reqtifyTimeoutValue};
                            Process proc = Runtime.getRuntime().exec(args);                             
                            reqtifyProcess = proc;
                            ReqtifyData.reqtfyLanguageProcessMap.put(reqtifyLang, proc);
                            ReqtifyData.reqtifyLanguagePortMap.put(reqtifyLang, reqtifyPort);
                            
                        } else if(!ReqtifyData.reqtfyLanguageProcessMap.containsKey(reqtifyLang)) {
                            //No Reqtify is started for this language
                            reqtifyPort = ReqtifyData.utils.nextFreePort(4000,8000);
                            String[] args = {reqtifyPath,"-http",String.valueOf(reqtifyPort),"-logfile",ReqtifyData.tempDir+"reqtifyLog_"+reqtifyPort+".log", "-l", reqtifyLang, "-timeout",ReqtifyData.reqtifyTimeoutValue};
                            Process proc = Runtime.getRuntime().exec(args);                             
                            reqtifyProcess = proc;
                            ReqtifyData.reqtfyLanguageProcessMap.put(reqtifyLang, proc);
                            ReqtifyData.reqtifyLanguagePortMap.put(reqtifyLang, reqtifyPort);
                        } else {                            
                            reqtifyProcess = ReqtifyData.reqtfyLanguageProcessMap.get(reqtifyLang);                            
                            reqtifyPort = ReqtifyData.reqtifyLanguagePortMap.get(reqtifyLang);  
                            
                            if(ReqtifyData.utils.isLocalPortFree(reqtifyPort)) {
                                //Reqtify stopped normally
                                ReqtifyData.reqtfyLanguageProcessMap.remove(reqtifyLang);
                                ReqtifyData.reqtifyLanguagePortMap.remove(reqtifyLang);
                                reqtifyPort = ReqtifyData.utils.nextFreePort(4000,8000);
                                String[] args = {reqtifyPath,"-http",String.valueOf(reqtifyPort),"-logfile",ReqtifyData.tempDir+"reqtifyLog_"+reqtifyPort+".log", "-l", reqtifyLang, "-timeout",ReqtifyData.reqtifyTimeoutValue};
                                Process proc = Runtime.getRuntime().exec(args);                             
                                reqtifyProcess = proc;
                                ReqtifyData.reqtfyLanguageProcessMap.put(reqtifyLang, proc);
                                ReqtifyData.reqtifyLanguagePortMap.put(reqtifyLang, reqtifyPort);                                
                            }
                        }
                                                                                                
                        String targetURLTemplates = "http://localhost:"+reqtifyPort+"/jenkins/getReportTemplates?dir="+currentWorkspace;
                        try {
                            JSONArray templatesResult = ReqtifyData.utils.executeGET(targetURLTemplates, reqtifyProcess, false);
   
                            //Templates
                            Iterator<String> itr = templatesResult.iterator();
                            while (itr.hasNext()) {
                                String template = itr.next();
                                m.add(template);                            
                            }   
                            
                            
                        } catch (ParseException ex) {
                            Logger.getLogger(ReqtifyGenerateReport.class.getName()).log(Level.SEVERE, null, ex);
                        }  catch (ConnectException ce) {
                            //Show some error
                        }  catch (ReqtifyException re) {
                            if(re.getMessage().length() > 0) {
                                reqtifyError = re.getMessage();
                            } else {  
                                Process p = ReqtifyData.reqtfyLanguageProcessMap.get(reqtifyLang);
                                if(p.isAlive())
                                    p.destroy();

                                ReqtifyData.reqtfyLanguageProcessMap.remove(reqtifyLang);
                                ReqtifyData.reqtifyLanguagePortMap.remove(reqtifyLang);
                                reqtifyError = ReqtifyData.utils.getLastLineOfFile(ReqtifyData.tempDir+"reqtifyLog_"+reqtifyPort+".log");
                            } 
                        }                                      
                    } catch(IOException | InterruptedException | AccessDeniedException e) {
                    }             

                    return m;
            }
        }        
    }
    
    private static class ReportGenerationPipelineStepExecution extends SynchronousStepExecution<String> {
        private static final long serialVersionUID = 1L;

        private transient final ReportGenerationPipelineStep step;
        ReportGenerationPipelineStepExecution(ReportGenerationPipelineStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        @edu.umd.cs.findbugs.annotations.SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
        protected String run() throws Exception {
            Run run = getContext().get(Run.class);    
            TaskListener listener = getContext().get(TaskListener.class);
            Job job = getContext().get(Job.class);
            FilePath currentWorkspacePath;
            currentWorkspacePath = Jenkins.get().getWorkspaceFor(Jenkins.get().getItem(job.getName()));                    
                        
            String reqtifyLang = "eng";
            Process reqtifyProcess;
            int reqtifyPort;
            String reqtifyPath = ReqtifyData.utils.findReqtifyPath();
            if(ReqtifyData.reqtfyLanguageProcessMap.isEmpty()) {
                 //No reqtify is started
                 reqtifyPort = ReqtifyData.utils.nextFreePort(4000,8000);
                 String[] args = {reqtifyPath,"-http",String.valueOf(reqtifyPort),"-logfile",ReqtifyData.tempDir+"reqtifyLog_"+reqtifyPort+".log", "-l", reqtifyLang,"-timeout",ReqtifyData.reqtifyTimeoutValue};
                 Process proc = Runtime.getRuntime().exec(args);                             
                 reqtifyProcess = proc;
                 ReqtifyData.reqtfyLanguageProcessMap.put(reqtifyLang, proc);
                 ReqtifyData.reqtifyLanguagePortMap.put(reqtifyLang, reqtifyPort);
             } else if(!ReqtifyData.reqtfyLanguageProcessMap.containsKey(reqtifyLang)) {
                 //No Reqtify is started for this language
                 reqtifyPort = ReqtifyData.utils.nextFreePort(4000,8000);
                 String[] args = {reqtifyPath,"-http",String.valueOf(reqtifyPort),"-logfile",ReqtifyData.tempDir+"reqtifyLog_"+reqtifyPort+".log", "-l", reqtifyLang,"-timeout",ReqtifyData.reqtifyTimeoutValue};
                 Process proc = Runtime.getRuntime().exec(args);                             
                 reqtifyProcess = proc;
                 ReqtifyData.reqtfyLanguageProcessMap.put(reqtifyLang, proc);
                 ReqtifyData.reqtifyLanguagePortMap.put(reqtifyLang, reqtifyPort);
             } else {
                 reqtifyProcess = ReqtifyData.reqtfyLanguageProcessMap.get(reqtifyLang);
                 reqtifyPort = ReqtifyData.reqtifyLanguagePortMap.get(reqtifyLang);
                 if(ReqtifyData.utils.isLocalPortFree(reqtifyPort)) {
                    ReqtifyData.reqtfyLanguageProcessMap.remove(reqtifyLang);
                    ReqtifyData.reqtifyLanguagePortMap.remove(reqtifyLang);
                    reqtifyPort = ReqtifyData.utils.nextFreePort(4000,8000);
                    String[] args = {reqtifyPath,"-http",String.valueOf(reqtifyPort),"-logfile",ReqtifyData.tempDir+"reqtifyLog_"+reqtifyPort+".log", "-l", reqtifyLang, "-timeout",ReqtifyData.reqtifyTimeoutValue};
                    Process proc = Runtime.getRuntime().exec(args);                             
                    reqtifyProcess = proc;
                    ReqtifyData.reqtfyLanguageProcessMap.put(reqtifyLang, proc);
                    ReqtifyData.reqtifyLanguagePortMap.put(reqtifyLang, reqtifyPort);                                
                }                     
             }


            String targetUrl = "http://localhost:"+reqtifyPort+"/jenkins/generateReport?dir="+currentWorkspacePath.getRemote()+
                               "&aReportModel="+URLEncoder.encode(modelReport,"UTF-8")+
                               "&aReportTemplate="+URLEncoder.encode(templateReport, "UTF-8")+
                               "&aFileOut="+currentWorkspacePath.getRemote()+"\\"+URLEncoder.encode(nameReport+"."+FilenameUtils.getExtension(templateReport),"UTF-8");

            try {                                   
                ReqtifyData.utils.executeGET(targetUrl, reqtifyProcess,true);
            } catch (ParseException ex) {
                Logger.getLogger(ReqtifyGenerateReport.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ConnectException e) {
                listener.error(e.getMessage());
                run.setResult(Result.FAILURE);
            } catch(ReqtifyException re) {
                if(re.getMessage().length() > 0) {
                    listener.error(re.getMessage());
                    run.setResult(Result.FAILURE);                    
                } else {                    
                    Process p = ReqtifyData.reqtfyLanguageProcessMap.get(reqtifyLang);
                    if(p.isAlive())
                        p.destroy();

                    ReqtifyData.reqtfyLanguageProcessMap.remove(reqtifyLang);
                    ReqtifyData.reqtifyLanguagePortMap.remove(reqtifyLang);
                    listener.error(ReqtifyData.utils.getLastLineOfFile(ReqtifyData.tempDir+"reqtifyLog_"+reqtifyPort+".log"));
                    run.setResult(Result.FAILURE);                    
                }
            }            
            return "";
        }
           
    }
}
