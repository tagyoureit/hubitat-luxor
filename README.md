# hubitat-luxor
Hubitat SmartThings SmartApp for Luxor ZD/ZDC controllers

## Overview

This SmartApp works with the following:
* Luxor ZD Controllers (Zoning/Dimming)
    * On/Off/Polling every 5 minutes
* Luxor ZDC & ZDTWO Controllers (Zoning/Diming/Color)
    * On/Off/Polling every 5 minutes
* Monochrome Light Groups
    * On/Off
    * Intensity/Brightness
* Color Light Groups
    * On/Off
    * Intensity/Brightness
    * Color Selection
* Themes
    * Momentary Push button on (no off; handled by light groups/controllers)

## Hubitat Install with Package Manager
Find this package with Hubitat Package Manager.

### Manual, more tedious
1. Select "Apps Code" and copy the code from luxor.groovy into the editor.
1. Select "Drivers Code" and copy the code from each of the four device handlers into the editor (repeat 4 times)

## SmartThings Install with GitHub Integration
1. Add this Repo (tagyoureit/hubitat-smartthings) to your SmartThings GitHub Settings
1. Select the SmartApp, select Publish, and import the file.
1. Repeat the same for the Device Handlers.

### Manual, more tedious
1. Create a new SmartApp and copy the code from luxor.groovy to a "from code" SmartThings Template.
1. Create a new Device Handler and copy the code from each of the four device handlers to a "from code" SmartThings Device Handler template.
1. Publish all five templates and install the Smart App.

## Configuration

1. You only need to know your Luxor controller's IP address.  You can find this on the controller itself under the Wifi/Wired settings page.
1. Enter the IP Address on the setup page and click Next.
1. Optionally, add a prefix for the naming convention.  Useful if you have multiple controllers.  Include any separators and spaces that you want.  EG "Front: " will create a device "Front: Luxor ZDC Controller".  
1. The screen will refresh every 2 seconds, up to 2 minutes, looking for the controller.
1. When the controller is found, select "Save" and wait up to 1 minute for all of the devices to be configured.

## Under the Hood

The SmartApp uses an API to connect to the Luxor Controller.  
* Changes to ZDC Color Group (Hue/Saturation) will be saved back to the controller
* Name changes for SmartThings devices will _not_ be saved back to the controller, but if you change the name in Luxor it will be kept in sync.
* HUBITAT ONLY: You can update the "color group" of the groups through your HA app. The color group on the Luxor server will be updated.`
* If you delete or add new lights/themes go through the app setup again or manually "refresh" the main controller (named 'Luxor {model} Controller').
* No automatic discovery can be enabled because SmartThings will only search for SSDP broadcasts and Luxor uses mDNS broadcasts.
* This code is fully compatible with BOTH SmartThings (classic App) and Hubitat. The SHPL is awesome and made that possible so thanks to Barry Burke

## Future enhancements?
* Add all Light Groups/Themes as icons on parent controller device
* Customizeable polling period
* ~~User selected default "on" Level for turning on all light groups~~ (Not possible)

