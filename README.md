# To Install:

1. Git Clone this repo: ```git clone https://github.com/braeden123/jenkinsHighlight.git```
2. (Optional) Build it yourself from source, using ```mvn install```

3. On the Jenkins homepage, click, "Manage Jenkins"

4. Scroll to "Manage Plugins"

5. Click on the "Advanced" tab

6. Scroll to "Upload Plugin", click "Choose File" and navigate to the .hpi file in the "Target" folder of the cloned repo


# To Use:

1. From the Jenkins hompage, click, "Manage Jenkins"

2. Click "Configure System"

3. Scroll to the bottom and fill out all of the information you need to. (* = mandatory)

4. Click "Apply" then "Save"

5. Select the Job that you'd like to add Highlight to

6. Click "Configure"

7. Under "Post-build Actions", click "Add post-build action", select "Run CAST Highlight"

8. Fill out the information (* = mandatory)

9. Click "Apply" then "Save"

10. Click "Build Now" on your project, and check the "Console Output"

11. If running online, check the "Highlight Results" tab in the build, for raw-extracted results
