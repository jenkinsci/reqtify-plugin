# Reqtify-plugin-for-Jenkins

This plugin allows to configure Reqtify report generation from Jenkins and then generate during the build.

### Configure Reqtify Report Generation

In the project configuration page, add a **Reqtify: Generate a report** build step.

![build_step](https://user-images.githubusercontent.com/37103100/60001844-0332fa00-9685-11e9-97d2-8c941af4f664.JPG)

This allows to fill following fields:

![reportModelsAndReportTemplates](https://github.com/jenkinsci/reqtify-plugin/blob/master/images/generate_report_build_step.PNG)

* **Name of report** - This is the name of report without any path or suffix. This report file will be created at the root of the Jenkins workspace.

* **Model of report** - This is the report model. The list contains both library and project report models.

* **Template of report** - This is the template of report. The list contains both library and project report templates.

### Note:
The plugin will work only when Reqtify project is present in the Jenkins workspace. <br>
**Reqtify version required: 2020x**
