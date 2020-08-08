---
name: Bug report
about: Create a report to help us improve
title: ''
labels: ''
assignees: ''

---

**Describe the bug**
A clear and concise description of what the bug is.

**To Reproduce**
Steps to reproduce the behavior:
1. Go to '...'
2. Click on '....'
3. Scroll down to '....'
4. See error

**Expected behavior**
A clear and concise description of what you expected to happen.

**Screenshots**
If applicable, add screenshots to help explain your problem.

**Desktop (please complete the following information):**
 - OS: [e.g. iOS]
 - Browser [e.g. chrome, safari]
 - Version [e.g. 22]

**Smartphone (please complete the following information):**
 - Device: [e.g. iPhone6]
 - OS: [e.g. iOS8.1]
 - Browser [e.g. stock browser, safari]
 - Version [e.g. 22]

# Turn on, capture and attach logs

### Step 1: Turn on trace logging
Please capture and attach logs to help with the debug process 

#### Option 1: In your SmartThings app
1. Go to My Home/Things
2. Click on the name (not button) of 'Luxor xyz Controller'
3. Click the gear icon in the upper right
4. Select "Trace" logging level and click save (upper right)

#### Option 2: In a browser
1. Log in to your Hub (for NA: https://graph-na04-useast2.api.smartthings.com/)
2. Click My Devices
3. Click on the name 'Luxor xyz Controller'
4. Click edit next to Preferences
5. Type in "Trace" (must be capital T)
6. Click save

### To capture logs
1. Log into your hub
2. Click on "Live Logging"
3. There could be lots of activity here; once you "Luxor xyz Controller" appear in the list of devices you should click on it to filter to just this controller
4. Go back to your Luxor and add a new theme, change a name, etc. Every time this app updates it's status it checks to see if the right "children" (lights/themes) are installed and if they need to be update.
5. Go back to the the 'Luxor xyz Controller' and now hit the button to turn it on/off. You'll see a whole lot of stuff in the logs. Copy and paste it all (probably into a text file) and attach it here.

**Additional context**
Add any other context about the problem here.
