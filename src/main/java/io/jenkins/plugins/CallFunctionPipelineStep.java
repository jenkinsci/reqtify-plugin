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

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.script.ScriptException;
import jenkins.model.Jenkins;
import org.acegisecurity.AccessDeniedException;
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

@edu.umd.cs.findbugs.annotations.SuppressFBWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
public class CallFunctionPipelineStep extends Step{
    
    private static final Logger logger = Logger.getLogger(ReportGenerationPipelineStep.class.getName());
    private static String functionName;
    private static String argument;
    private static String[] argumentList;    

    public String getFunctionName() {
        return CallFunctionPipelineStep.functionName;
    }

    public String getArgument() {
        return CallFunctionPipelineStep.argument;
    }
    
    public String[] getArgumentList() {
        return CallFunctionPipelineStep.argumentList;
    }
    
    
    @DataBoundSetter
    public void setFunctionName(@Nonnull String functionName) {
            CallFunctionPipelineStep.functionName = functionName;
    }
    
    @DataBoundSetter
    public void setArgument(@Nonnull String argument) {
            CallFunctionPipelineStep.argument = argument;
    }    
    
    @DataBoundSetter
    public void setArgumentList(@Nonnull String[] argumentList) {
            CallFunctionPipelineStep.argumentList = argumentList;
    }  

    @DataBoundConstructor
    public CallFunctionPipelineStep(String functionName, String[] argumentList) {
        CallFunctionPipelineStep.functionName = functionName;
        CallFunctionPipelineStep.argumentList = argumentList;
    }    
    
    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new CallFunctionPipelineStepExecution(this, context); 
    }
    
    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        private String reqtifyFunctionError;
        private List<String> functions;
        private final Map<String,JSONArray> functionParamterMap;  
        public DescriptorImpl() throws IOException, InterruptedException, ScriptException {
            functions = new ArrayList<>();
            functionParamterMap = new HashMap<>();
        }
        
        private List<String> getFunctions() {
            return this.functions;
        }
                
        private void setFunctions(List<String> functions) {
            this.functions = functions;
        }

        @Override
        public String getFunctionName() {
            return "reqtifyFunction";
        }

        @Override
        public String getDisplayName() {
            return io.jenkins.plugins.Messages.ReqtifyCallFunction_DisplayName();
        }   
        
        @JavaScriptMethod
        public String getReqtifyFunctionError() {
            return reqtifyFunctionError;
        }      
        
        @JavaScriptMethod
        public String getSavedFunction(String currentJob) {
            return Utils.getSavedFunctionName(currentJob);
        }
        
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, TaskListener.class);
        }
        
        @JavaScriptMethod
        public List<String> renderParamUI(String functionName, String currentJob) {
            reqtifyFunctionError = "";
            List<String> htmlList = new ArrayList<>();
            String reqtifyLang = "eng";                         
            Process reqtifyProcess = ReqtifyData.reqtfyLanguageProcessMap.get(reqtifyLang);                            
            int reqtifyPort = ReqtifyData.reqtifyLanguagePortMap.get(reqtifyLang);
            JSONArray functionParameters;
            do {
                functionParameters = functionParamterMap.get(functionName);
                if(!functionParamterMap.isEmpty()) break;
                // Wait until functionParamterMap is not null;
            } while(functionParameters == null);

            functionParameters = functionParamterMap.get(functionName);
            if(functionParameters == null) {
                return htmlList;
            }
            
            Iterator itr = functionParameters.iterator();            
            List selectedParamValues = Utils.getFunctionArgumentsData(currentJob, false);
            Iterator selectedParamValuesItr = selectedParamValues.iterator();
            List scalarParamValues = new ArrayList();
            List nonScalarParamValues = new ArrayList();
            
            //Split saved parameter values in scalar and non-scalar types
            while(selectedParamValuesItr.hasNext()) {
                String paramValue = (String)selectedParamValuesItr.next();
                if(paramValue.startsWith("ns_")) {
                    nonScalarParamValues.add(paramValue.split("_")[1]);
                } else {
                    scalarParamValues.add(paramValue);
                }
            }
            
            int index = 0;
            List scalarParams = new ArrayList();           
            while(itr.hasNext()) {
                index++;
                JSONObject param = (JSONObject) itr.next();  
                boolean isScalar = false;
                if(param.containsKey("isScalar")) isScalar = ((boolean)param.get("isScalar"));
                if(!isScalar) {
                    String getFunctionParamValueURL = "http://localhost:"+reqtifyPort+
                            "/jenkins/getFunctionParameterValues?functionName="
                            +functionName+"&paramIndex="+index;
                    try {         
                            JSONArray paramValueResult = (JSONArray)ReqtifyData.utils.executeGET(getFunctionParamValueURL, reqtifyProcess, false);
                            if (!paramValueResult.isEmpty()) { 
                                String html = "<tr class=\"function-param\">" +
                                                "	<td class=\"setting-leftspace\">&nbsp;</td>" +
                                                "	<td class=\"setting-name\">"+param.get("name").toString()+"</td>" +
                                                "	<td class=\"setting-main\">" +
                                                "	   <select name=\"_.argumentList\" class=\"setting-input  select\" value=\"\" multiple>";
                                                Iterator paramValueResultItr = paramValueResult.iterator();
                                                while(paramValueResultItr.hasNext()) {
                                                    JSONObject paramValue = (JSONObject) paramValueResultItr.next();
                                                    String hoverText;
                                                    if(paramValue.containsKey("text")) hoverText = paramValue.get("text").toString(); else hoverText = "";
                                                    if(nonScalarParamValues.size() > 0 && nonScalarParamValues.contains(paramValue.get("id").toString()))
                                                        html +="<option value=ns_"+paramValue.get("id").toString()+" selected title=\""+hoverText+"\">"+paramValue.get("print").toString()+"</option>";
                                                    else
                                                        html +="<option value=ns_"+paramValue.get("id").toString()+" title=\""+hoverText+"\">"+paramValue.get("print").toString()+"</option>";
                                                }

                                                html += "</select>" +
                                                "	</td>" +
                                                /*"<td class=\"setting-help\"><a helpurl=\"/jenkins/plugin/reqtify/help/CallFunction/help-paramValue"+index+".html\" href=\"#\" class=\"help-button\" tabindex=\"9999\">"
                                                + "<svg viewBox=\"0 0 24 24\" aria-hidden=\"\" tooltip=\"Help for feature: "+param.get("name").toString()+"\" focusable=\"false\" class=\"svg-icon icon-help \">"
                                                + "<use href=\"/jenkins/static/f65f36d5/images/material-icons/svg-sprite-action-symbol.svg#ic_help_24px\"></use></svg>"
                                                + "</a></td>" + */                                                        
                                                " </tr>";
                                htmlList.add(html);
                            }
                        } catch (ParseException | IOException ex) {
                            Logger.getLogger(CallFunction.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (ReqtifyException re) {
                            if(re.getMessage().length() > 0) {
                                reqtifyFunctionError = re.getMessage();
                            } else {  
                                Process p = ReqtifyData.reqtfyLanguageProcessMap.get(reqtifyLang);
                                if(p.isAlive())
                                    p.destroy();

                                ReqtifyData.reqtfyLanguageProcessMap.remove(reqtifyLang);
                                ReqtifyData.reqtifyLanguagePortMap.remove(reqtifyLang);
                                reqtifyFunctionError = ReqtifyData.utils.getLastLineOfFile(ReqtifyData.tempDir+"reqtifyLog_"+reqtifyPort+".log");
                            } 
                        }                 
                } else {
                    scalarParams.add(param);
                }
            }   
            
            //Create scalar param HTML
            Iterator scalarParamsItr = scalarParams.iterator();
            Iterator scalarParamValueItr = scalarParamValues.iterator();
            while(scalarParamsItr.hasNext()) {
                JSONObject param = (JSONObject)scalarParamsItr.next();
                    String html = "<tr class=\"function-param\">" +
                                    "   <td class=\"setting-leftspace\">&nbsp;</td>" +
                                    "   <td class=\"setting-name\">"+param.get("name").toString()+"</td>" +
                                    "   <td class=\"setting-main\">";
                                    if(scalarParamValueItr.hasNext())
                                        html+= "      <input default=\"\" name=\"_.argumentList\" type=\"text\" class=\"setting-input\" value="+scalarParamValueItr.next()+">";   
                                    else
                                        html+= "      <input default=\"\" name=\"_.argumentList\" type=\"text\" class=\"setting-input\" value=\"\">";   
                                     html+= "   </td>" +
                                                /*"<td class=\"setting-help\"><a helpurl=\"/jenkins/plugin/reqtify/help/CallFunction/help-paramValue"+index+".html\" href=\"#\" class=\"help-button\" tabindex=\"9999\">"
                                                + "<svg viewBox=\"0 0 24 24\" aria-hidden=\"\" tooltip=\"Help for feature: "+param.get("name").toString()+"\" focusable=\"false\" class=\"svg-icon icon-help \">"
                                                + "<use href=\"/jenkins/static/f65f36d5/images/material-icons/svg-sprite-action-symbol.svg#ic_help_24px\"></use></svg>"
                                                +"</a></td>" +    */                                          
                                    "</tr>";
                    htmlList.add(html);                
            }
            Collections.reverse(htmlList);
            return htmlList;
        }       
        
        public ListBoxModel doFillFunctionNameItems() throws InterruptedException, IOException {
            ListBoxModel m = new ListBoxModel();
            synchronized(ReqtifyData.class) {
                try {
                    String currentJob = "";
                    String currentWorkspace;
                    reqtifyFunctionError = "";
                    Pattern pattern = Pattern.compile("job/(.*?)/pipeline-syntax/descriptorByName");
                    Matcher matcher = pattern.matcher(Jenkins.get().getDescriptor().getDescriptorFullUrl());
                    while (matcher.find()) {
                        currentJob = matcher.group(1);
                    }
                    currentWorkspace = Utils.getWorkspacePath(currentJob);
                    String reqtifyLang = "eng";      
                    Utils.initReqtifyProcess();
                    int  reqtifyPort = ReqtifyData.reqtifyLanguagePortMap.get(reqtifyLang);    
                    Process reqtifyProcess = ReqtifyData.reqtfyLanguageProcessMap.get(reqtifyLang);

                    String targetURLFunctions = "http://localhost:"+reqtifyPort+"/jenkins/getFunctions?"; 
                    try {

                        String openProjectUrl = "http://localhost:"+reqtifyPort+"/jenkins/openProject?dir="+currentWorkspace;
                        ReqtifyData.utils.executeGET(openProjectUrl, reqtifyProcess,false);
                                     
                        JSONArray functionsResult = (JSONArray)ReqtifyData.utils.executeGET(targetURLFunctions, reqtifyProcess, false);
                        Iterator<JSONObject> itr = functionsResult.iterator();
                        m.add("Select Function");
                        int index = 1;                        
                        //functions parameters
                        while (itr.hasNext()) {
                            JSONObject function = (JSONObject) itr.next();
                            m.add(function.get("label").toString());
                            m.get(index++).value = function.get("name").toString();
                            JSONArray functionParamters = (JSONArray) function.get("parameters");
                            functionParamterMap.put(function.get("name").toString(), functionParamters);                        
                        }
                    } catch (ParseException ex) {
                        Logger.getLogger(CallFunction.class.getName()).log(Level.SEVERE, null, ex);
                    }  catch (ConnectException ce) {
                        //Show some error
                    }  catch (ReqtifyException re) {
                        if(re.getMessage().length() > 0) {
                            reqtifyFunctionError = re.getMessage();
                        } else {  
                            Process p = ReqtifyData.reqtfyLanguageProcessMap.get(reqtifyLang);
                            if(p.isAlive())
                                p.destroy();

                            ReqtifyData.reqtfyLanguageProcessMap.remove(reqtifyLang);
                            ReqtifyData.reqtifyLanguagePortMap.remove(reqtifyLang);
                            reqtifyFunctionError = ReqtifyData.utils.getLastLineOfFile(ReqtifyData.tempDir+"reqtifyLog_"+reqtifyPort+".log");
                        } 
                    }                
                }catch(IOException | AccessDeniedException e) {
                }  
            }//end synchronize
            
            return m;
        }            
    }// End Descriptorimpl
    
     private static class CallFunctionPipelineStepExecution extends SynchronousStepExecution<String> {
        private static final long serialVersionUID = 1L;

        private transient final CallFunctionPipelineStep step;
        CallFunctionPipelineStepExecution(CallFunctionPipelineStep step, StepContext context) {
            super(context);
            this.step = step;
        }
        
        @Override
        @edu.umd.cs.findbugs.annotations.SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
        protected String run() throws Exception {
            Run run = getContext().get(Run.class);    
            TaskListener listener = getContext().get(TaskListener.class);
            String reqtifyLang = "eng";
            Process reqtifyProcess;
            int reqtifyPort; 
            Utils.initReqtifyProcess();
            reqtifyPort = ReqtifyData.reqtifyLanguagePortMap.get(reqtifyLang);    
            reqtifyProcess = ReqtifyData.reqtfyLanguageProcessMap.get(reqtifyLang); 
            try {      
                //Open the project if it is first request that means if project is not opened
                String currentWorkspace = Utils.getWorkspacePath(run.getParent().getName());
                String openProjectUrl = "http://localhost:"+reqtifyPort+"/jenkins/openProject?dir="+currentWorkspace;
                ReqtifyData.utils.executeGET(openProjectUrl, reqtifyProcess,false);

                String targetUrl = "http://localhost:"+reqtifyPort+"/jenkins/"+functionName+"?";
                String arg1 = "";
                String arg2 = "";
                if(argumentList!=null && argumentList.length > 0) {
                    for (String argumentList1 : argumentList) {
                        if (argumentList1.startsWith("ns_")) {
                            arg1 += argumentList1.split("_")[1] + ",";
                        } else {
                            arg2 += argumentList1 + ",";
                        }                        
                    }
                    if(!arg1.isEmpty()) {
                        targetUrl+="arg1=";
                        arg1 = arg1.substring(0, arg1.length() - 1);
                        targetUrl+=arg1;
                    }
                    if(!arg2.isEmpty()) {
                        targetUrl+="&arg2=";     
                        arg2 = arg2.substring(0, arg2.length() - 1);    
                        targetUrl+=arg2;     
                    }                                          
                }

                Object result = ReqtifyData.utils.executeGET(targetUrl, reqtifyProcess,true);
                listener.getLogger().print("\n\n"+functionName+" result:\n"+result.toString()+"\n\n");
                run.setResult(Result.SUCCESS);                
            } catch (ParseException ex) {
                Logger.getLogger(CallFunction.class.getName()).log(Level.SEVERE, null, ex);
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
