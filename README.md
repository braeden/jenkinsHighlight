# To Install:

1. Git Clone this repo:

2. On the Jenkins homepage, click, "Manage Jenkins"

3. Scroll to "Manage Plugins"

4. Click on the "Advanced" tab

5. Scroll to "Upload Plugin", click "Choose File" and navigate to the .hpi file in the "Target" forlder of the cloned repo


# To Use:

1. From the Jenkins hompage, click, "Manage Jenkins"

2. Click "Configure System"

3. Scroll to the bottom and fill out all of the information you need to. (Paths to Highlightautomation.jar and perl directory are mandatory)

4. Click "Apply" then "Save"

4. Select the Job that you'd like to add Highlight to

5. Click "Configure"

6. Under "Post-build Actions", click "Add post-build action", select "Run CAST Highlight"

7. Fill out the information, Project Filepath and Output Filepath are mandatory. 

8. Click "Apply" then "Save"

9. Click "Build Now" on your project, and check the "Console Output"
