# SmartThings_Luxor
SmartThings SmartApp for Luxor ZD/ZDC controllers

## Overview

This SmartApp works with the following:
* Luxor ZD Controllers (Zoning/Dimming)
    * On/Off/Polling every 5 minutes
* Luxor ZDC Controllers (Zoning/Diming/Color)
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

## Install directions:

### Manual, more tedious
1. Copy code from luxor.groovy to a "from code" SmartThings Template.
1. Copy code from each of the four device handlers to a "from code" SmartThings Device Handler template.
1. Publish all five templates and install the Smart App.

### Or... Easier, with GitHub Integration
1. Add this Repo to your SmartThings GitHub Settings
1. Select the SmartApp, select Publish, and import the file.
1. Repeat the same for the Device Handlers.

## Configuration

1. You only need to know your Luxor controller's IP address.  You can find this on the controller itself under the Wifi/Wired settings page.
1. Enter the IP Address on the setup page and click Next.
1. The screen will refresh every 2 seconds, up to 2 minutes, looking for the controller.
1. When the controller is found, select "Save" and wait up to 1 minute for all of the devices to be configured.

## Under the Hood

The SmartApp uses an API to connect to the Luxor Controller.  
* Changes to ZDC Color Group (Hue/Saturation) will be saved back to the controller
* Name changes for SmartThings devices will _not_ be saved back to the controller.

No automatic discovery can be enabled because SmartThings will only search for SSDP broadcasts and Luxor uses mDNS broadcasts.


## Future enhancements?
* Add all Light Groups/Themes as icons on parent controller device
* Customizeable polling period
* ~~User selected default "on" Level for turning on all light groups~~ (Not possible)
* Save/change color group for color lights
* Enable "live color change mode" for color groups before save (may not be necessary.  This is when the user holds/drags the color before pressing done when selecting a color.)