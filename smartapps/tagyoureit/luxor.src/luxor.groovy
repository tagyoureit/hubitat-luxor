/**
*  Luxor
*
*  Copyright 2017-2020 Russell Goldin
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
    description: "SmartApp to control Luxor ZD, ZDC and ZDTWO Lighting Controllers by Hunter",
    category: "SmartThings Labs",
    tags: "",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

def debug() {
    // set to false to disable logging
    return true
}

preferences {
    page(name: 'setupInit')
    page(name: 'setupValidate')
}

def validateController() {
    if (state.controllerDiscovered == 'Not Started') {
        log.info "Looking for Luxor Controller at $luxorIP with state $state.controllerDiscovered"

        state.controllerDiscovered = 'Pending'
        hubGet('/ControllerName.json', null, 'parseControllerName')
    }
    else {
        log.debug 'Not running validateController because it is already pending.'
    }
}

def setupValidate() {
    log.debug "In SetupValidate with $state.controllerDiscovered"
    def msg
    def canInstall = false
    def interval = 2
    def progress
    if (state.controllerDiscovered == 'Not Started') {
        log.debug "Setup has result: $state.controllerDiscovered"
        validateController()
        msg = "Status is $state.controllerDiscovered.  This page will refresh with updated status."
        canInstall = false
        interval = 2
        progress = 'Starting search'
    }
    else if (state.controllerDiscovered == 'Pending') {
        didDiscover()
        msg = "Status is $state.controllerDiscovered.  App is looking for the controller."
        canInstall = false
        interval = 2
        progress = 'Searching'
    }
    else if (state.controllerDiscovered == 'Not Found') {
        msg = "Status is $state.controllerDiscovered.  Please press the back button and re-enter the IP Address."
        canInstall = false
        interval = 120
        progress = 'Failed'
    }
    else if (state.controllerDiscovered == 'Found') {
        msg = "Found Luxor $state.controllerType controller '$state.controllerName'.  Ready to install. \n\nPlease wait up to 60 seconds for groups and themes to show up after you click save."
        canInstall = true
        interval = 120
        progress = 'Success'
    }

    def pageProperties = [
        name:        'setupValidate',
        title:        'Validation Page',
        install:    canInstall,
        refreshInterval: interval
    ]

    return dynamicPage(pageProperties) {
                section(progress) {
            paragraph msg
                }
    }

}

def setupInit() {
    state.controllerDiscovered = 'Not Started'
    log.debug "in setupInit with states $state.controllerDiscovered"
    def inputIP = [
        name:            'luxorIP',
        type:            'string',
        title:            'Enter IP Address of Luxor Controller',
        defaultValue:    'xxx.xxx.xxx.xxx',
        required:        true
    ]
    def controllerName = [
        name:            'luxorName',
        type:            'string',
        title:            'Enter a unique name for this controller (or leave blank)',
        defaultValue:    '',
        required:        false
    ]

    def pageProperties = [
        name:        'setupInit',
        title:        'Luxor ZD/ZDC/ZDTWO Setup',
        nextPage:   'setupValidate',
        install:    false,
        uninstall:  true
    ]

        return dynamicPage(pageProperties) {
        section {
            input inputIP,
            input conttrollerName
        }
        }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    getHubPlatform()
    initialize()
}

def updated() {
    getHubPlatform()
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def hubGet(def apiCommand, def body='{}', def _callback) {
    def cb = [:]
    if (_callback) {
        cb['callback'] = _callback
    }
    def result
    if (getIsST()) {
        result = physicalgraph.device.HubAction.newInstance(
        method: 'POST',
        path: apiCommand,
        body: "${body}",
        headers: [
            'HOST' : "${luxorIP}:80",
            'Content-Type': 'application/json'],
        null,
        cb
    )
    }
    else {
        result = hubitat.device.HubAction.newInstance(
        method: 'POST',
        path: apiCommand,
        body: "${body}",
        headers: [
            'HOST' : "${luxorIP}:80",
            'Content-Type': 'application/json'],
        null,
        cb
    )
    }
    log.debug cb
    log.debug result.toString()
    sendHubCommand(result)
}

def parseControllerName(hubResponse) {
    state.controllerDiscovered = 'Found'
    state.controllerMac = hubResponse.mac
    state.controllerName = hubResponse.json.Controller
    log.debug "ControllerName response: ${hubResponse.json}"
    if (hubResponse.json.Controller.toLowerCase().contains('lxzdc')) {
        log.info "Discovered LXZDC controller at $luxorIP"
        state.controllerType = 'ZDC'
    }
    else if (hubResponse.json.Controller.toLowerCase().contains('lxtwo')) {
        log.info "Discovered LXTWO controller at $luxorIP"
        state.controllerType = 'ZDTWO'
    }
    else {
        log.info "Discovered LXZD controller at $luxorIP"
        state.controllerType = 'ZD'
    }

}

def addControllerAsDevice() {
    def mac = state.controllerMac
    def hubId = location.hubs[0].id
    def d = getChildDevices()?.find { it.deviceNetworkId == mac }
    if (d) {
        d.updateDataValue('controllerIP', luxorIP)
    //d.manageChildren()  // installed in child device calls same method
    } else {
        log.info "Creating Luxor ${state.controllerType} Controller Device with dni: ${mac}"
        d = addChildDevice('tagyoureit', 'Luxor Controller', mac, hubId,
                           ['label'         : "${luxorName == null || luxorName.isEmpty() ? "" : luxorName + " :"} Luxor ${state.controllerType} Controller",
                            'completedSetup': true,
                            'data'          : [
                                'controllerMac'     : mac,
                                'controllerIP'      : luxorIP,
                                'controllerPort'    : 80,
                                'controllerType'    : state.controllerType,
                                'controllerName'    : luxorName
                            ]
                           ])
    }
    log.debug "Controller Device is $d"
}

def initialize() {
    addControllerAsDevice()
}

def didDiscover() {
    if (state.controllerDiscovered == 'Pending') {
        def error = "Luxor controller could not be discovered at $settings.luxorIP."
        log.error "$error"
        //sendPush(error)
        state.controllerDiscovered = 'Not Found'
    }
    else {
        log.debug "In did discover, state should be found.  It is $state.controllerDiscovered."
    }
}

// **************************************************************************************************************************
// SmartThings/Hubitat Portability Library (SHPL)
// Copyright (c) 2019, Barry A. Burke (storageanarchy@gmail.com)
//
// The following 3 calls are safe to use anywhere within a Device Handler or Application
//  - these can be called (e.g., if (getPlatform() == 'SmartThings'), or referenced (i.e., if (platform == 'Hubitat') )
//  - performance of the non-native platform is horrendous, so it is best to use these only in the metadata{} section of a
//    Device Handler or Application
//
private String  getPlatform() { (physicalgraph?.device?.HubAction ? 'SmartThings' : 'Hubitat') }    // if (platform == 'SmartThings') ...
private Boolean getIsST()     { (physicalgraph?.device?.HubAction ? true : false) }                    // if (isST) ...
private Boolean getIsHE()     { (hubitat?.device?.HubAction ? true : false) }                        // if (isHE) ...
//
// The following 3 calls are ONLY for use within the Device Handler or Application runtime
//  - they will throw an error at compile time if used within metadata, usually complaining that "state" is not defined
//  - getHubPlatform() ***MUST*** be called from the installed() method, then use "state.hubPlatform" elsewhere
//  - "if (state.isST)" is more efficient than "if (isSTHub)"
//
private String getHubPlatform() {
    if (state?.hubPlatform == null) {
        state.hubPlatform = getPlatform()                        // if (hubPlatform == 'Hubitat') ... or if (state.hubPlatform == 'SmartThings')...
        state.isST = state.hubPlatform.startsWith('S')            // if (state.isST) ...
        state.isHE = state.hubPlatform.startsWith('H')            // if (state.isHE) ...
    }
    return state.hubPlatform
}
private Boolean getIsSTHub() { (state.isST) }                    // if (isSTHub) ...
private Boolean getIsHEHub() { (state.isHE) }                    // if (isHEHub) ...
