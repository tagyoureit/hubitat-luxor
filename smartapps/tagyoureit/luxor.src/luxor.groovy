/**
*  Luxor
*
*  Copyright 2017 Russell Goldin
*
*  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License. You may obtain a copy of the License at:
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*  for the specific language governing permissions and limitations under the License.
*
*/
definition(
    name: "Luxor",
    namespace: "tagyoureit",
    author: "Russell Goldin",
    description: "SmartApp to control Luxor ZD and ZDC Lighting Controllers by Hunter",
    category: "SmartThings Labs",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

def debug() {
    // set to false to disable logging
    return true
}

preferences {
    page(name: "setupInit")
    page(name: "setupValidate")

}



def validateController(){
    if (state.controllerDiscovered == "Not Started"){
        log.info "Looking for Luxor Controller at $luxorIP with state $state.controllerDiscovered"

        state.controllerDiscovered = "Pending"
        hubGet("/ControllerName.json", null, "parseControllerName")
    }
    else {
        log.debug "Not running validateController because it is already pending."
    }
}


def setupValidate() {
    log.debug "In SetupValidate with $state.controllerDiscovered"
    def msg
    def canInstall = false
    def interval = 2
    def progress
    if (state.controllerDiscovered == "Not Started"){
        log.debug "Setup has result: $state.controllerDiscovered"
        validateController()
        msg = "Status is $state.controllerDiscovered.  This page will refresh with updated status."
        canInstall = false
        interval = 2
        progress = "Starting search"
    }
    else if (state.controllerDiscovered == "Pending") {
    	didDiscover()
        msg = "Status is $state.controllerDiscovered.  App is looking for the controller."
        canInstall = false
        interval = 2
        progress = "Searching"
    }
    else if (state.controllerDiscovered == "Not Found") {
        msg = "Status is $state.controllerDiscovered.  Please press the back button and re-enter the IP Address."
        canInstall = false
        interval = 120
        progress = "Failed"
    }
    else if (state.controllerDiscovered == "Found") {
        msg = "Found Luxor $state.controllerType controller '$state.controllerName'.  Ready to install. \n\nPlease wait ~30 seconds for groups and themes to show up after you click save."
        canInstall = true
        interval = 120
        progress = "Success"
    }
   
    def pageProperties = [
        name:		"setupValidate",
        title:		"Validation Page",
        install:	canInstall,
        refreshInterval: interval
    ]

    return dynamicPage(pageProperties) {
        section(progress){
            paragraph msg
        }
    }


}

def setupInit() {
    state.controllerDiscovered = "Not Started"
    log.debug "in setupInit with states $state.controllerDiscovered"
    def inputIP = [
        name:			"luxorIP",
        type:			"string",
        title:			"Enter IP Address of Luxor Controller",
        defaultValue:	"11.11.11.13",
        required:		true
    ]

    def pageProperties = [
        name:		"setupInit",
        title:		"Luxor ZD/DZC Setup",
        nextPage:   "setupValidate",
        install:	false,
        uninstall:  true
    ]


    return dynamicPage(pageProperties){
        section{
            input inputIP
        }
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"

    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"

    unsubscribe()
    initialize()
}

def hubGet(def apiCommand, def body="{}", def _callback) {
    def cb = [:]
    if (_callback) {
        cb["callback"] = _callback
    }
    def result = new physicalgraph.device.HubAction(
        method: "POST",
        path: apiCommand,
        body: "${body}",
        headers: [
            "HOST" : "${luxorIP}:80",
            "Content-Type": "application/json"],
        null,
        cb
    )
    log.debug cb
    log.debug result.toString()
    sendHubCommand(result);

}



def parseControllerName(physicalgraph.device.HubResponse hubResponse) {
    state.controllerDiscovered = "Found"
    state.controllerMac = hubResponse.mac
    state.controllerName = hubResponse.json.Controller
    log.debug "ControllerName response: ${hubResponse.json}"
    if (hubResponse.json.Controller.toLowerCase().contains("lxzdc")){
        log.info "Discovered LXZDC controller at $luxorIP"
        state.controllerType = "ZDC"
    }
    else {
        log.info "Discovered LXZD controller at $luxorIP"
        state.controllerType = "ZD"
    }



}

def addControllerAsDevice(){
    def mac = state.controllerMac
    def hubId = location.hubs[0].id
    def d = getChildDevices()?.find { it.deviceNetworkId == mac}
    if (d) {
        d.updateDataValue("controllerIP", luxorIP)
        d.manageChildren()
    } else {
        log.info "Creating Luxor ${state.controllerType} Controller Device with dni: ${mac}"
        d = addChildDevice("tagyoureit", "Luxor Controller", mac, hubId,
                           ["label"         : "Luxor ${state.controllerType} Controller",
                            "completedSetup": true,
                            "data"          : [
                                "controllerMac"     : mac,
                                "controllerIP"      : luxorIP,
                                "controllerPort"    : 80,
                                "controllerType"	: state.controllerType
                            ]
                           ])
    }
    log.debug "Controller Device is $d"

}


def initialize() {
    addControllerAsDevice()

}


def didDiscover(){
    if (state.controllerDiscovered=="Pending"){
        def error = "Luxor controller could not be discovered at $settings.luxorIP."
        log.error "$error"
        //sendPush(error)
        state.controllerDiscovered = "Not Found"
    }
    else {
        log.debug "In did discover, state should be found.  It is $state.controllerDiscovered."
    }
}


