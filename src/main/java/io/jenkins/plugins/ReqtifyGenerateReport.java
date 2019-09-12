package io.jenkins.plugins;

import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;

import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.script.ScriptException;
import jenkins.model.Jenkins;
import org.acegisecurity.AccessDeniedException;
import org.apache.commons.io.FilenameUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.kohsuke.stapler.bind.JavaScriptMethod;
/**
 * This class allow us to generate a reqtify report with Jenkins.
 * 
 * @author 3DS
 * @version 1.0
 */
@Extension
public class ReqtifyGenerateReport extends Builder implements SimpleBuildStep {

	private String nameReport;
	private String modelReport;
	private String templateReport;
	private String lang;
        private static Utils utils;        
        private static Map<String, Process> reqtfyLanguageProcessMap;        
        private static Map<String, Integer> reqtifyLanguagePortMap;        
        private static String tempDir;                        
        private static String currentWorkspace;
        private static String reqtifyPath; 
        private static String reqtifyTimeoutValue = "1800";
        
	public ReqtifyGenerateReport() {
            nameReport = "";          
	}
	
	/**
	 * Constructor with DataBoundConstructor.
	 * 
	 * @param nameReport stored name of the report
	 * @param modelReport stored model of the report 
	 * @param templateReport stored template of the report
	 * @param lang stored language of the report
	 * @since 1.0
	 */
	@DataBoundConstructor
	public ReqtifyGenerateReport(String nameReport, String modelReport, String templateReport, String lang) {
		this.nameReport = nameReport;
		this.modelReport = modelReport;
		this.templateReport = templateReport;
	}
	
	/**
	 * @return stored name of the report
	 * @since 1.0
	 */
	@Nonnull
	public String getNameReport() {
		return this.nameReport;
	}
	
	/**
	 * @return stored model of the report
	 * @since 1.0
	 */
	public String getModelReport() {
		return this.modelReport;
	}
	
	/**
	 * @return stored template of the report
	 * @since 1.0
	 */
	public String getTemplateReport() {
		return this.templateReport;
	}
	        
	/**
	 * @return stored language of the report
	 * @since 1.0
	 */
	public String getLang() {
		return this.lang;
	}
	
	/**
	 * Set a new name for the report.
	 * 
	 * @param nameReport New name for the report
	 * @since 1.0
	 * */
	@DataBoundSetter
	public void setNameReport(@Nonnull String nameReport) {
		this.nameReport = nameReport;
	}
	
	/**
	 * Set a new model for the report.
	 * 
	 * @param modelReport New model for the report
	 * @since 1.0
	 */
	@DataBoundSetter
	public void setModelReport(String modelReport) {
		this.modelReport = modelReport;
	}
	
	/**
	 * Set a new template for the report.
	 * 
	 * @param templateReport New template for the report
	 * @since 1.0
	 */
	@DataBoundSetter
	public void setTemplateReport(String templateReport) {
		this.templateReport = templateReport;
	}
	
	/**
	 * @return Return the descriptor
	 * @since 1.0
	 */
	@Override
	public DescriptorImpl getDescriptor() {
	    return (DescriptorImpl)super.getDescriptor();
	}
	
	/**
	 * Read all the char of an inputStream before -1.
	 * 
	 * @param is InputStream we want to read
	 * @return Return the string of readed chars
         * @throws java.io.IOException
	 * @since 1.0
	 */
	public static String readAll(InputStream is) throws IOException {
                StringBuilder  sb = new StringBuilder();
                
		int c;
                while ((c = is.read()) != -1) {
                        sb.append("").append((char)c);
                }
		return sb.toString();
	}
		                   
	/**
	 * Creation and running of the command to generate a Reqtify report.
	 * 
	 * @param run
	 * @param workspace Path of the worskspace of the project
	 * @param launcher
	 * @param listener
     * @throws java.lang.InterruptedException
     * @throws java.io.IOException
	 * @since 1.0
	 */
	@Override
	public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
                String reqtifyLang = "eng";
                Process reqtifyProcess;
                int reqtifyPort;
                if(reqtfyLanguageProcessMap.isEmpty()) {
                     //No reqtify is started
                     reqtifyPort = utils.nextFreePort(4000,8000);
                     String[] args = {reqtifyPath,"-http",String.valueOf(reqtifyPort),"-logfile",tempDir+"reqtifyLog_"+reqtifyPort+".log", "-l", reqtifyLang,"-timeout",reqtifyTimeoutValue};
                     Process proc = Runtime.getRuntime().exec(args);                             
                     reqtifyProcess = proc;
                     reqtfyLanguageProcessMap.put(reqtifyLang, proc);
                     reqtifyLanguagePortMap.put(reqtifyLang, reqtifyPort);

                 } else if(!reqtfyLanguageProcessMap.containsKey(reqtifyLang)) {
                     //No Reqtify is started for this language
                     reqtifyPort = utils.nextFreePort(4000,8000);
                     String[] args = {reqtifyPath,"-http",String.valueOf(reqtifyPort),"-logfile",tempDir+"reqtifyLog_"+reqtifyPort+".log", "-l", reqtifyLang,"-timeout",reqtifyTimeoutValue};
                     Process proc = Runtime.getRuntime().exec(args);                             
                     reqtifyProcess = proc;
                     reqtfyLanguageProcessMap.put(reqtifyLang, proc);
                     reqtifyLanguagePortMap.put(reqtifyLang, reqtifyPort);
                 } else {
                     reqtifyProcess = reqtfyLanguageProcessMap.get(reqtifyLang);
                     reqtifyPort = reqtifyLanguagePortMap.get(reqtifyLang);
                     if(utils.isLocalPortFree(reqtifyPort)) {
                        reqtfyLanguageProcessMap.remove(reqtifyLang);
                        reqtifyLanguagePortMap.remove(reqtifyLang);
                        reqtifyPort = utils.nextFreePort(4000,8000);
                        String[] args = {reqtifyPath,"-http",String.valueOf(reqtifyPort),"-logfile",tempDir+"reqtifyLog_"+reqtifyPort+".log", "-l", reqtifyLang, "-timeout",reqtifyTimeoutValue};
                        Process proc = Runtime.getRuntime().exec(args);                             
                        reqtifyProcess = proc;
                        reqtfyLanguageProcessMap.put(reqtifyLang, proc);
                        reqtifyLanguagePortMap.put(reqtifyLang, reqtifyPort);                                
                    }                     
                 }
            

                String targetUrl = "http://localhost:"+reqtifyPort+"/jenkins/generateReport?dir="+workspace.getRemote()+
                                   "&aReportModel="+URLEncoder.encode(this.modelReport,"UTF-8")+
                                   "&aReportTemplate="+URLEncoder.encode(this.templateReport, "UTF-8")+
                                   "&aFileOut="+workspace.getRemote()+"\\"+URLEncoder.encode(this.nameReport+"."+FilenameUtils.getExtension(this.templateReport),"UTF-8");

                    try {                                   
                        utils.executeGET(targetUrl, reqtifyProcess,true);
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
                            Process p = reqtfyLanguageProcessMap.get(reqtifyLang);
                            if(p.isAlive())
                                p.destroy();

                            reqtfyLanguageProcessMap.remove(reqtifyLang);
                            reqtifyLanguagePortMap.remove(reqtifyLang);
                            listener.error(utils.getLastLineOfFile(tempDir+"reqtifyLog_"+reqtifyPort+".log"));
                            run.setResult(Result.FAILURE);                    
                        }
                    }
	}
                        
	/**
	 * Descriptor of the class ReqtifyGenerateReport.
	 * 
	 * @author 3DS
	 * @version 1.0
	 * @since 1.0
	 */
	@Symbol("reqtifyReport")
	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
                private List<String> models;
                private List<String> templates;  
                private String reqtifyError;
                private int reqtifyPort;
                public DescriptorImpl() throws IOException, InterruptedException, ScriptException {
                    utils = new Utils();
                    reqtfyLanguageProcessMap = new HashMap<>();
                    reqtifyLanguagePortMap = new HashMap<>();
                    models = new ArrayList<>();
                    templates = new ArrayList<>();
                    tempDir = System.getProperty("java.io.tmpdir");
                    reqtifyPath = utils.findReqtifyPath();                                                 
                }
                                      
                private List<String> getModels() {
                    return this.models;
                }
                
                private void setModels(List<String> models) {
                    this.models = models;
                }
                
                private List<String> getTemplates() {
                    return this.templates;
                }
                
                private void setTemplates(List<String> templates) {
                    this.templates = templates;
                }                
                
		/**
		 * @return Return the name of the build step function
		 * @since 1.0
		 */
		@Override
                public String getDisplayName() {
                    return io.jenkins.plugins.Messages.ReqtifyGenerateReport_DisplayName();
                }

                @JavaScriptMethod
                public String getReqtifyError() {
                    return reqtifyError;
                }
        
		/**
		 * @param jobType
		 * @return
		 * @since 1.0
		 */
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                                        
                    try {                                                
                        FilePath currentWorkspacePath;
                        String currentJob = "";
                        reqtifyError = "";
                        List<String> modelsTemp = new ArrayList<>();
                        List<String> templatesTemp = new ArrayList<>(); 
                        Pattern pattern = Pattern.compile("job/(.*?)/descriptorByName");
                        Matcher matcher = pattern.matcher(Jenkins.getInstance().getDescriptor().getDescriptorFullUrl());
                        while (matcher.find()) {
                            currentJob = matcher.group(1);
                        }

                        currentWorkspacePath = Jenkins.getInstance().getWorkspaceFor(Jenkins.getInstance().getItem(currentJob));                    
                        
                        this.setModels(modelsTemp);
                        this.setTemplates(templatesTemp);
                        
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
                        if(reqtfyLanguageProcessMap.isEmpty()) {
                            //No reqtify is started
                            reqtifyPort = utils.nextFreePort(4000,8000);
                            String[] args = {reqtifyPath,"-http",String.valueOf(reqtifyPort),"-logfile",tempDir+"reqtifyLog_"+reqtifyPort+".log", "-l", reqtifyLang, "-timeout",reqtifyTimeoutValue};
                            Process proc = Runtime.getRuntime().exec(args);                             
                            reqtifyProcess = proc;
                            reqtfyLanguageProcessMap.put(reqtifyLang, proc);
                            reqtifyLanguagePortMap.put(reqtifyLang, reqtifyPort);
                            
                        } else if(!reqtfyLanguageProcessMap.containsKey(reqtifyLang)) {
                            //No Reqtify is started for this language
                            reqtifyPort = utils.nextFreePort(4000,8000);
                            String[] args = {reqtifyPath,"-http",String.valueOf(reqtifyPort),"-logfile",tempDir+"reqtifyLog_"+reqtifyPort+".log", "-l", reqtifyLang, "-timeout",reqtifyTimeoutValue};
                            Process proc = Runtime.getRuntime().exec(args);                             
                            reqtifyProcess = proc;
                            reqtfyLanguageProcessMap.put(reqtifyLang, proc);
                            reqtifyLanguagePortMap.put(reqtifyLang, reqtifyPort);
                        } else {                            
                            reqtifyProcess = reqtfyLanguageProcessMap.get(reqtifyLang);                            
                            reqtifyPort = reqtifyLanguagePortMap.get(reqtifyLang);  
                            
                            if(utils.isLocalPortFree(reqtifyPort)) {
                                //Reqtify stopped normally
                                reqtfyLanguageProcessMap.remove(reqtifyLang);
                                reqtifyLanguagePortMap.remove(reqtifyLang);
                                reqtifyPort = utils.nextFreePort(4000,8000);
                                String[] args = {reqtifyPath,"-http",String.valueOf(reqtifyPort),"-logfile",tempDir+"reqtifyLog_"+reqtifyPort+".log", "-l", reqtifyLang, "-timeout",reqtifyTimeoutValue};
                                Process proc = Runtime.getRuntime().exec(args);                             
                                reqtifyProcess = proc;
                                reqtfyLanguageProcessMap.put(reqtifyLang, proc);
                                reqtifyLanguagePortMap.put(reqtifyLang, reqtifyPort);                                
                            }
                        }
                                                                                                
                        String targetURLModels = "http://localhost:"+reqtifyPort+"/jenkins/getReportModels?dir="+currentWorkspace;
                        String targetURLTemplates = "http://localhost:"+reqtifyPort+"/jenkins/getReportTemplates?dir="+currentWorkspace;
                        try {
                            JSONArray modelsResult = utils.executeGET(targetURLModels, reqtifyProcess,false);
                            JSONArray templatesResult = utils.executeGET(targetURLTemplates, reqtifyProcess, false);
                            Iterator<JSONObject> itr = modelsResult.iterator();
                            //Models
                            while (itr.hasNext()) {
                                JSONObject model = (JSONObject) itr.next();
                                modelsTemp.add(model.get("label").toString());                            
                            }
                            
                            this.setModels(modelsTemp);
                            //Templates
                            Iterator<String> itr1 = templatesResult.iterator();
                            while (itr1.hasNext()) {
                                String template = itr1.next();
                                templatesTemp.add(template);                            
                            }   
                            
                            this.setTemplates(templatesTemp);
                            
                        } catch (ParseException ex) {
                            Logger.getLogger(ReqtifyGenerateReport.class.getName()).log(Level.SEVERE, null, ex);
                        }  catch (ConnectException ce) {
                            //Show some error
                        }  catch (ReqtifyException re) {
                            if(re.getMessage().length() > 0) {
                                reqtifyError = re.getMessage();
                            } else {  
                                Process p = reqtfyLanguageProcessMap.get(reqtifyLang);
                                if(p.isAlive())
                                    p.destroy();

                                reqtfyLanguageProcessMap.remove(reqtifyLang);
                                reqtifyLanguagePortMap.remove(reqtifyLang);
                                reqtifyError = utils.getLastLineOfFile(tempDir+"reqtifyLog_"+reqtifyPort+".log");
                            } 
                        }                                      
                    } catch(IOException | InterruptedException | AccessDeniedException e) {
                    }  
                    
                    return true; 
                } 
                
                public ListBoxModel doFillModelReportItems() throws InterruptedException, IOException {
                    
                    if(this.getModels().size() > 0) {
                        ListBoxModel m = new ListBoxModel();  
                        Iterator<String> itr = this.getModels().iterator();
                        while(itr.hasNext()) {
                            m.add(itr.next());
                        }                                    
                        return m;                        
                    } else {
                        return null;
                    }
                }
                
                public ListBoxModel doFillTemplateReportItems() throws IOException, InterruptedException {
                    if(this.getTemplates().size() > 0) {
                        ListBoxModel m = new ListBoxModel();  
                        Iterator<String> itr = this.getTemplates().iterator();
                        while(itr.hasNext()) {
                            m.add(itr.next());
                        }                                    
                        return m;                        
                    } else {
                        return null;
                    }
                }                                              
	}                 
}
