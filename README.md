# Reqtify-plugin-for-Jenkins

This plugin allows to  
1. Configure Reqtify report generation build step and then generate report during build.
2. Configure Reqtify call function build step and then call the function during build. 

### Configure Reqtify report generation build step

In the project configuration page, add a **Reqtify: Generate Report** build step.

![build_step](https://github.com/jenkinsci/reqtify-plugin/blob/master/images/generate_report.png)

This allows to fill following fields:

![reportModelsAndReportTemplates](https://github.com/jenkinsci/reqtify-plugin/blob/master/images/generate_report_build_step.PNG)

* **Report Name** - This is the report file name without any path or suffix. This report file will be created at the root of the Jenkins workspace.

* **Report Model** - This is the report model. The list contains both library and project report models.

* **docs** - A scalar paramter to for report generation

* **Report Template** - This is the report template. The list contains both library and project report templates like HTML, DOCX, Excel, PDF etc.

### Configure calling function build step

In the project configuration page, add a **Reqtify: Call Function** build step.

![build_step](https://github.com/jenkinsci/reqtify-plugin/blob/master/images/call_function.PNG)

This allows to fill following fields:

![reportModelsAndReportTemplates](https://github.com/jenkinsci/reqtify-plugin/blob/master/images/call_function_build_step.png)

* **Function Name** - This is the function name to call during the build. To add parameters for the function, you have to select a function from the list.

* **aReq** - A scalar parameter for the function

* **anIndex** - Non-Scalar parameter for the function

### Note:
The plugin will work only when Reqtify project is present in the Jenkins workspace. <br>
**Reqtify version required: 2021x**
**for 64-bit Reqtify need Reqtify plugin version 3.0.0 or more**
