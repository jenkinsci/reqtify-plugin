# Reqtify-plugin-for-Jenkins

This plugin adds an ability to configure Reqtify report genration feature in Jenkins.

### Configure Reqtify Report Generation

In the project configuration page, add a Reqtify: Generate a report build step.

![build_step](https://user-images.githubusercontent.com/37103100/60001844-0332fa00-9685-11e9-97d2-8c941af4f664.JPG)

This allows to fill following fields:

![reportModelsAndReportTemplates](https://user-images.githubusercontent.com/37103100/60001925-35445c00-9685-11e9-9aaf-083dead7fd87.JPG)

* #### Name of report 
        This is the name of the report without any path or suffix. This
report file will be created at the root of the Jenkins workspace

* #### Model of report
        This is the report model. The list contains both library and project
report models

* #### Template of report 
        This is template of the report. The list contains both library
and project report templates.

### Note:

The plugin will work only when Reqtify project is present in the Jenkins
workspace.
