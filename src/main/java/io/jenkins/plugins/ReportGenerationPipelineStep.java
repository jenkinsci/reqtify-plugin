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
import java.net.URLEncoder;
import java.util.*;
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

@edu.umd.cs.findbugs.annotations.SuppressFBWarnings("ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD")
public class ReportGenerationPipelineStep extends Step {

    private static final Logger logger = Logger.getLogger(ReportGenerationPipelineStep.class.getName());

    private static String nameReport;
    private static String modelReport;
    private static String templateReport;
    private static String[] reportArgumentList;
    private static String lang;
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("io.jenkins.plugins.Messages");

    @Nonnull
    public String getNameReport() {
        return ReportGenerationPipelineStep.nameReport;
    }

    public String getModelReport() {
        return ReportGenerationPipelineStep.modelReport;
    }

    public String getTemplateReport() {
        return ReportGenerationPipelineStep.templateReport;
    }

    public String getLang() {
        return ReportGenerationPipelineStep.lang;
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings("EI_EXPOSE_REP")
    public String[] getReportArgumentList() {
        return ReportGenerationPipelineStep.reportArgumentList;
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
    public ReportGenerationPipelineStep(
            String nameReport, String modelReport, String templateReport, String[] reportArgumentList) {
        ReportGenerationPipelineStep.nameReport = nameReport;
        ReportGenerationPipelineStep.modelReport = modelReport;
        ReportGenerationPipelineStep.templateReport = templateReport;
        ReportGenerationPipelineStep.reportArgumentList = reportArgumentList;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new ReportGenerationPipelineStepExecution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        private String reqtifyError;
        private final Map<String, JSONArray> functionParamterMap;

        public DescriptorImpl() throws IOException, InterruptedException, ScriptException {
            functionParamterMap = new HashMap<>();
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

        @edu.umd.cs.findbugs.annotations.SuppressFBWarnings("SBSC_USE_STRINGBUFFER_CONCATENATION")
        @JavaScriptMethod
        public List<String> renderReportParamUI(String functionName, String currentJob) {
            reqtifyError = "";
            List<String> htmlList = new ArrayList<>();
            String reqtifyLang = "eng";
            Process reqtifyProcess = ReqtifyData.reqtfyLanguageProcessMap.get(reqtifyLang);
            int reqtifyPort = ReqtifyData.reqtifyLanguagePortMap.get(reqtifyLang);
            JSONArray functionParameters;
            do {
                functionParameters = functionParamterMap.get(functionName);
                if (!functionParamterMap.isEmpty()) break;
                // Wait until functionParamterMap is not null;
            } while (functionParameters == null);

            functionParameters = functionParamterMap.get(functionName);
            if (functionParameters == null) {
                return htmlList;
            }
            Iterator itr = functionParameters.iterator();
            List selectedParamValues = Utils.getFunctionArgumentsData(currentJob, true);
            Iterator selectedParamValuesItr = selectedParamValues.iterator();
            List scalarParamValues = new ArrayList();
            List nonScalarParamValues = new ArrayList();
            // Devide selected parameter values into scalar and non-scalar type
            while (selectedParamValuesItr.hasNext()) {
                String paramValue = (String) selectedParamValuesItr.next();
                if (paramValue.startsWith("ns_")) {
                    nonScalarParamValues.add(paramValue.split("_")[1]);
                } else {
                    scalarParamValues.add(paramValue);
                }
            }
            int index = 0;
            List scalarParams = new ArrayList();
            while (itr.hasNext()) {
                index++;
                JSONObject param = (JSONObject) itr.next();
                boolean isScalar = false;
                if (param.containsKey("isScalar")) isScalar = ((boolean) param.get("isScalar"));
                if (!isScalar) {
                    String getFunctionParamValueURL = "http://localhost:" + reqtifyPort
                            + "/jenkins/getReportParameterValues?functionName=" + functionName + "&paramIndex=" + index;
                    try {
                        JSONArray paramValueResult = (JSONArray)
                                ReqtifyData.utils.executeGET(getFunctionParamValueURL, reqtifyProcess, false);
                        if (!paramValueResult.isEmpty()) {
                            String html = "<tr class=\"report-param\">" + "	<td class=\"setting-leftspace\">&nbsp;</td>"
                                    + "	<td class=\"setting-name\">"
                                    + param.get("name").toString() + "</td>" + "	<td class=\"setting-main\">"
                                    + "	   <select name=\"_.reportArgumentList\" class=\"setting-input  select\" value=\"\" multiple>";
                            Iterator paramValueResultItr = paramValueResult.iterator();
                            while (paramValueResultItr.hasNext()) {
                                JSONObject paramValue = (JSONObject) paramValueResultItr.next();

                                if (nonScalarParamValues.size() > 0
                                        && nonScalarParamValues.contains(
                                                paramValue.get("id").toString()))
                                    html += "<option value=ns_"
                                            + paramValue.get("id").toString() + " selected>"
                                            + paramValue.get("print").toString() + "</option>";
                                else
                                    html += "<option value=ns_"
                                            + paramValue.get("id").toString() + ">"
                                            + paramValue.get("print").toString() + "</option>";
                            }

                            html += "</select>" + "	</td>"
                                    +
                                    /* "<td class=\"setting-help\"><a helpurl=\"/jenkins/plugin/reqtify/help/CallFunction/help-paramValue"+index+".html\" href=\"#\" class=\"help-button\" tabindex=\"9999\">"
                                    + "<svg viewBox=\"0 0 24 24\" aria-hidden=\"\" tooltip=\"Help for feature: "+param.get("name").toString()+"\" focusable=\"false\" class=\"svg-icon icon-help \">"
                                    + "<use href=\"/jenkins/static/f65f36d5/images/material-icons/svg-sprite-action-symbol.svg#ic_help_24px\"></use></svg>"
                                    + "</a></td>" +     */
                                    " </tr>";
                            htmlList.add(html);
                        }
                    } catch (ParseException | IOException ex) {
                        Logger.getLogger(CallFunction.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (ReqtifyException re) {
                        if (re.getMessage().length() > 0) {
                            reqtifyError = re.getMessage();
                        } else {
                            Process p = ReqtifyData.reqtfyLanguageProcessMap.get(reqtifyLang);
                            if (p.isAlive()) p.destroy();

                            ReqtifyData.reqtfyLanguageProcessMap.remove(reqtifyLang);
                            ReqtifyData.reqtifyLanguagePortMap.remove(reqtifyLang);
                            reqtifyError = ReqtifyData.utils.getLastLineOfFile(
                                    ReqtifyData.tempDir + "reqtifyLog_" + reqtifyPort + ".log");
                        }
                    }
                } else {
                    scalarParams.add(param);
                }
            }

            // Create scalar param HTML
            Iterator scalarParamsItr = scalarParams.iterator();
            Iterator scalarParamValueItr = scalarParamValues.iterator();
            while (scalarParamsItr.hasNext()) {
                JSONObject param = (JSONObject) scalarParamsItr.next();
                String html = "<tr class=\"report-param\">" + "   <td class=\"setting-leftspace\">&nbsp;</td>"
                        + "   <td class=\"setting-name\">"
                        + param.get("name").toString() + "</td>" + "   <td class=\"setting-main\">";
                if (scalarParamValueItr.hasNext())
                    html +=
                            "      <input default=\"\" name=\"_.reportArgumentList\" type=\"text\" class=\"setting-input\" value="
                                    + scalarParamValueItr.next() + ">";
                else
                    html +=
                            "      <input default=\"\" name=\"_.reportArgumentList\" type=\"text\" class=\"setting-input\" value=\"\">";
                html += "   </td>" +
                        /* "<td class=\"setting-help\"><a helpurl=\"/jenkins/plugin/reqtify/help/CallFunction/help-paramValue"+index+".html\" href=\"#\" class=\"help-button\" tabindex=\"9999\">"
                        + "<svg viewBox=\"0 0 24 24\" aria-hidden=\"\" tooltip=\"Help for feature: "+param.get("name").toString()+"\" focusable=\"false\" class=\"svg-icon icon-help \">"
                        + "<use href=\"/jenkins/static/f65f36d5/images/material-icons/svg-sprite-action-symbol.svg#ic_help_24px\"></use></svg>"
                        + "</a></td>" +    */
                        "</tr>";
                htmlList.add(html);
            }
            Collections.reverse(htmlList);
            return htmlList;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return BUNDLE.getString("ReqtifyGenerateReport.DisplayName");
        }

        public ListBoxModel doFillModelReportItems() throws InterruptedException, IOException {
            ListBoxModel m = new ListBoxModel();
            synchronized (ReqtifyData.class) {
                try {
                    String currentJob = "";
                    reqtifyError = "";
                    String currentWorkspace = "";
                    Pattern pattern = Pattern.compile("job/(.*?)/pipeline-syntax/descriptorByName");
                    Matcher matcher =
                            pattern.matcher(Jenkins.get().getDescriptor().getDescriptorFullUrl());
                    while (matcher.find()) {
                        currentJob = matcher.group(1);
                    }

                    currentWorkspace = Utils.getWorkspacePath(currentJob);

                    String reqtifyLang = "eng";
                    Utils.initReqtifyProcess();
                    int reqtifyPort = ReqtifyData.reqtifyLanguagePortMap.get(reqtifyLang);
                    Process reqtifyProcess = ReqtifyData.reqtfyLanguageProcessMap.get(reqtifyLang);

                    String targetURLModels = "http://localhost:" + reqtifyPort + "/jenkins/getReportModels?";
                    try {
                        String openProjectUrl =
                                "http://localhost:" + reqtifyPort + "/jenkins/openProject?dir=" + currentWorkspace;
                        ReqtifyData.utils.executeGET(openProjectUrl, reqtifyProcess, false);
                        JSONArray modelsResult =
                                (JSONArray) ReqtifyData.utils.executeGET(targetURLModels, reqtifyProcess, false);
                        Iterator<JSONObject> itr = modelsResult.iterator();
                        // Models
                        m.add("Select Report Model");
                        while (itr.hasNext()) {
                            JSONObject model = (JSONObject) itr.next();
                            m.add(model.get("label").toString());
                            // Report parameters
                            JSONArray functionParamters = (JSONArray) model.get("parameters");
                            functionParamterMap.put(model.get("name").toString(), functionParamters);
                        }

                    } catch (ParseException ex) {
                        Logger.getLogger(ReqtifyGenerateReport.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (ConnectException ce) {
                        // Show some error
                    } catch (ReqtifyException re) {
                        if (re.getMessage().length() > 0) {
                            reqtifyError = re.getMessage();
                        } else {
                            Process p = ReqtifyData.reqtfyLanguageProcessMap.get(reqtifyLang);
                            if (p.isAlive()) p.destroy();

                            ReqtifyData.reqtfyLanguageProcessMap.remove(reqtifyLang);
                            ReqtifyData.reqtifyLanguagePortMap.remove(reqtifyLang);
                            reqtifyError = ReqtifyData.utils.getLastLineOfFile(
                                    ReqtifyData.tempDir + "reqtifyLog_" + reqtifyPort + ".log");
                        }
                    }
                } catch (IOException | AccessDeniedException e) {
                }

                return m;
            }
        }

        public ListBoxModel doFillTemplateReportItems() throws IOException, InterruptedException {
            ListBoxModel m = new ListBoxModel();
            synchronized (ReqtifyData.class) {
                try {
                    String currentJob = "";
                    reqtifyError = "";
                    String currentWorkspace = "";
                    Pattern pattern = Pattern.compile("job/(.*?)/pipeline-syntax/descriptorByName");
                    Matcher matcher =
                            pattern.matcher(Jenkins.get().getDescriptor().getDescriptorFullUrl());
                    while (matcher.find()) {
                        currentJob = matcher.group(1);
                    }

                    currentWorkspace = Utils.getWorkspacePath(currentJob);

                    String reqtifyLang = "eng";
                    Utils.initReqtifyProcess();
                    int reqtifyPort = ReqtifyData.reqtifyLanguagePortMap.get(reqtifyLang);
                    Process reqtifyProcess = ReqtifyData.reqtfyLanguageProcessMap.get(reqtifyLang);

                    String targetURLTemplates = "http://localhost:" + reqtifyPort + "/jenkins/getReportTemplates?";
                    try {
                        String openProjectUrl =
                                "http://localhost:" + reqtifyPort + "/jenkins/openProject?dir=" + currentWorkspace;
                        ReqtifyData.utils.executeGET(openProjectUrl, reqtifyProcess, false);
                        JSONArray templatesResult =
                                (JSONArray) ReqtifyData.utils.executeGET(targetURLTemplates, reqtifyProcess, false);

                        // Templates
                        Iterator<String> itr = templatesResult.iterator();
                        m.add("Select Report Template");
                        while (itr.hasNext()) {
                            String template = itr.next();
                            m.add(template);
                        }
                    } catch (ParseException ex) {
                        Logger.getLogger(ReqtifyGenerateReport.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (ConnectException ce) {
                        // Show some error
                    } catch (ReqtifyException re) {
                        if (re.getMessage().length() > 0) {
                            reqtifyError = re.getMessage();
                        } else {
                            Process p = ReqtifyData.reqtfyLanguageProcessMap.get(reqtifyLang);
                            if (p.isAlive()) p.destroy();

                            ReqtifyData.reqtfyLanguageProcessMap.remove(reqtifyLang);
                            ReqtifyData.reqtifyLanguagePortMap.remove(reqtifyLang);
                            reqtifyError = ReqtifyData.utils.getLastLineOfFile(
                                    ReqtifyData.tempDir + "reqtifyLog_" + reqtifyPort + ".log");
                        }
                    }
                } catch (IOException | AccessDeniedException e) {
                }

                return m;
            }
        }
    }

    private static class ReportGenerationPipelineStepExecution extends SynchronousStepExecution<String> {
        private static final long serialVersionUID = 1L;

        private final transient ReportGenerationPipelineStep step;

        ReportGenerationPipelineStepExecution(ReportGenerationPipelineStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        @edu.umd.cs.findbugs.annotations.SuppressFBWarnings("SBSC_USE_STRINGBUFFER_CONCATENATION")
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
                String currentWorkspace = Utils.getWorkspacePath(run.getParent().getName());
                // Open the project if it is first request that means if project is not opened
                String openProjectUrl =
                        "http://localhost:" + reqtifyPort + "/jenkins/openProject?dir=" + currentWorkspace;
                ReqtifyData.utils.executeGET(openProjectUrl, reqtifyProcess, false);
                String targetUrl = "http://localhost:" + reqtifyPort + "/jenkins/generateReport?" + "aReportModel="
                        + URLEncoder.encode(modelReport, "UTF-8") + "&aReportTemplate="
                        + URLEncoder.encode(templateReport, "UTF-8") + "&aFileOut="
                        + currentWorkspace + "\\"
                        + URLEncoder.encode(nameReport + "." + FilenameUtils.getExtension(templateReport), "UTF-8");

                String arg1 = "";
                String arg2 = "";
                if (reportArgumentList != null && reportArgumentList.length > 0) {
                    for (String reportArgumentList1 : reportArgumentList) {
                        if (reportArgumentList1.startsWith("ns_")) {
                            arg1 += reportArgumentList1.split("_")[1] + ",";
                        } else {
                            arg2 += reportArgumentList1 + ",";
                        }
                    }
                    if (!arg1.isEmpty()) {
                        targetUrl += "&arg1=";
                        arg1 = arg1.substring(0, arg1.length() - 1);
                        targetUrl += arg1;
                    }
                    if (!arg2.isEmpty()) {
                        targetUrl += "&arg2=";
                        arg2 = arg2.substring(0, arg2.length() - 1);
                        targetUrl += arg2;
                    }
                }
                ReqtifyData.utils.executeGET(targetUrl, reqtifyProcess, true);
            } catch (ParseException ex) {
                Logger.getLogger(ReqtifyGenerateReport.class.getName()).log(Level.SEVERE, null, ex);
            } catch (ConnectException e) {
                listener.error(e.getMessage());
                run.setResult(Result.FAILURE);
            } catch (ReqtifyException re) {
                if (re.getMessage().length() > 0) {
                    listener.error(re.getMessage());
                    run.setResult(Result.FAILURE);
                } else {
                    Process p = ReqtifyData.reqtfyLanguageProcessMap.get(reqtifyLang);
                    if (p.isAlive()) p.destroy();

                    ReqtifyData.reqtfyLanguageProcessMap.remove(reqtifyLang);
                    ReqtifyData.reqtifyLanguagePortMap.remove(reqtifyLang);
                    listener.error(ReqtifyData.utils.getLastLineOfFile(
                            ReqtifyData.tempDir + "reqtifyLog_" + reqtifyPort + ".log"));
                    run.setResult(Result.FAILURE);
                }
            }
            return "";
        }
    }
}
