/**
*  Luxor Monochrome Group
*
*  Copyright 2018-2020 Russell Goldin
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
metadata {
    definition (name: "Luxor Monochrome Group", namespace: "tagyoureit", author: "Russell Goldin") {
        capability "Switch"
        capability "Switch Level"
        capability "Refresh"
    }


    simulator {
        //status "on":  "{ /“Name”:/”Pergola Light Test/”, /“Grp/”:1, /“Inten/”:100, /“Colr/” 0 }"
        //status "off": "{ /“Name”:/”Pergola Light Test/”, /“Grp/”:1, /“Inten/”:0, /“Colr/” 0 }"

        // reply messages
        //reply "on": "switch:on"
        //reply "off": "switch:off"
    }

    tiles(scale: 2) {
        multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'${name}', action:"switch.off", icon:"st.lights.philips.hue-single", backgroundColor:"#00A0DC", nextState:"turningOff"
                attributeState "off", label:'${name}', action:"switch.on", icon:"st.lights.philips.hue-single", backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.lights.philips.hue-single", backgroundColor:"#00A0DC", nextState:"turningOff"
                attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.lights.philips.hue-single", backgroundColor:"#ffffff", nextState:"turningOn"
            }
            tileAttribute ("device.level", key: "SLIDER_CONTROL") {
                attributeState "level", action:"switch level.setLevel"
            }
        }
        
        standardTile("refresh", "device.refresh", height: 1, width: 1, inactiveLabel: false) {
            state "default", label: 'Refresh', action: "refresh.refresh", icon: "st.secondary.refresh-icon"
        }
        
        main(["switch"])
        details(["switch","switchLevel","refresh"])
    }

}

// parse events into attributes
def parse(String description) {
  log.debug "Parsing '${description}'"
}

// handle commands
def off() {

  log.debug "Executing 'off'"

    state.desiredIntensity = 0
    sendEvent(name: "switch", value: "turningOff", displayed:true) 
    illuminateGroup()
}

def on() {

  log.debug "Executing 'on'"

    log.info "device is $device"
    log.info "device.data is $device.data"
    log.info "device.name is $device.name"


    sendEvent(name: "switch", value: "turningOn", displayed:true) 

    state.desiredIntensity = 100

    illuminateGroup()
}

def setLevel(lvl) {
  log.debug "Executing 'setLevel' with $lvl"
    state.desiredIntensity = lvl

    if (lvl==0){
        sendEvent(name: "switch", value: "turningOff", displayed:true) 
        off()
    }
    else {
        sendEvent(name: "switch", value: "turningOn", displayed:true) 
        illuminateGroup()
    }
}


def sendCommandToController(def apiCommand, def body="{}", def _callback) {
    def controllerIP = getDataValue('controllerIP')

    def cb = [:]
    if (_callback) {
        cb["callback"] = _callback
    }
    def hubAction
    if (state.isST){
    hubAction = physicalgraph.device.HubAction.newInstance(
        method: "POST",
        path: apiCommand,
        body: body,
        headers: [
            "HOST" : "$controllerIP:80",
            "Content-Type": "application/json"],
        null,
        cb
    )
    }
    else {
     hubAction = hubitat.device.HubAction.newInstance(
        method: "POST",
        path: apiCommand,
        body: body,
        headers: [
            "HOST" : "$controllerIP:80",
            "Content-Type": "application/json"],
        null,
        cb
    )   
    }
  log.debug "Sending $apiCommand to controller\n${hubAction.toString()}"
    sendHubCommand(hubAction);
}

def illuminateGroup(){
    def obj = [GroupNumber: state.luxorGroup, Intensity: state.desiredIntensity]
    log.debug "Luxor illuminating group $group at $state.desiredIntensity brightness."
    sendCommandToController('/IlluminateGroup.json', obj, 'parseIlluminateGroup')
}

def setState(_state, _val){
    state."$_state" = _val
}


def parseIlluminateGroup(hubResponse) {
  log.debug "hubResponse: $hubResponse.body  $hubResponse.description  $hubResponse.headers  desiredInten=$state.desiredIntensity"
    if (hubResponse.json.Status==0){

        if (state.desiredIntensity>0){
            log.info "Light group ${state.luxorGroup} is now on with brightness $state.desiredIntensity."
            sendEvent(name: "switch", value: "on", displayed:true) 
            sendEvent(name: "level", value: state.desiredIntensity)
        }
        else
        {
            log.info "Light group ${state.luxorGroup} is now off."
            sendEvent(name: "switch", value: "off", displayed:true) 
            sendEvent(name: "level", value: state.desiredIntensity)

        }
        parent.childRefresh()
    }
    else {
        log.info "Error from Luxor controller: ${hubResponse.json}"
    }
}

def installed() {
    getHubPlatform()
    log.info "Executing installed on $device"
}

def setValues() {
    sendEvent(name: "switch", value: switchState>0?"on":"off" , displayed:true)
    sendEvent(name: "level", value: inten)
}

def updated(){
    getHubPlatform()
    log.debug "updated $device"
    //setValues()
}

def refresh(){
  log.debug "called refresh $device"
    parent.childRefresh()
}

private logger(msg, level = 'debug') {
    def lookup = [
                'None' : 0,
                'Error' : 1,
                'Warning' : 2,
                'Info' : 3,
                'Debug' : 4,
                'Trace' : 5]
    def logLevel = lookup[state.loggingLevelIDE ? state.loggingLevelIDE : 'Debug']
    // log.debug("Lookup is now ${logLevel} for ${state.loggingLevelIDE}")

    switch (level) {
        case 'error':
            if (logLevel >= 1) log.error msg
            break

        case 'warn':
            if (logLevel >= 2) log.warn msg
            break

        case 'info':
            if (logLevel >= 3) log.info msg
            break

        case 'debug':
            if (logLevel >= 4) log.debug msg
            break

        case 'trace':
            if (logLevel >= 5) log.trace msg
            break

        case 'none':
           break

        default:
            log.debug msg
            break
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