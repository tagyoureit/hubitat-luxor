/**
 *  Luxor Color Group
 *
 *  Copyright 2018 Russell Goldin
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
    definition(name: "Luxor Color Group", namespace: "tagyoureit", author: "Russell Goldin") {
        capability 'Color Control'
        capability 'Switch'
        capability 'Switch Level'
        capability 'Refresh'

        command 'changeColorGroup', [[Name:'Change Color Group for this Group', type: 'NUMBER']]
    }

    simulator {
        // TODO: define status and reply messages here
    }

    tiles(scale: 2) {
        multiAttributeTile(name: 'switch', type: 'lighting', width: 6, height: 4, canChangeIcon: true) {
            tileAttribute('device.switch', key: 'PRIMARY_CONTROL') {
                attributeState 'on', label: '${name}', action: 'switch.off', icon: 'st.lights.philips.hue-single', backgroundColor: '#00A0DC', nextState: 'turningOff'
                attributeState 'off', label: '${name}', action: 'switch.on', icon: 'st.lights.philips.hue-single', backgroundColor: '#ffffff', nextState: 'turningOn'
                attributeState 'turningOn', label: '${name}', action: 'switch.off', icon: 'st.lights.philips.hue-single', backgroundColor: '#00A0DC', nextState: 'turningOff'
                attributeState 'turningOff', label: '${name}', action: 'switch.on', icon: 'st.lights.philips.hue-single', backgroundColor: '#ffffff', nextState: 'turningOn'
            }
            tileAttribute('device.color', key: 'COLOR_CONTROL') {
                attributeState 'color', action: 'setColor'
            }
            tileAttribute('device.level', key: 'SLIDER_CONTROL') {
                attributeState 'level', action: 'switch level.setLevel'
            }
        }

        standardTile('refresh', 'device.refresh', height: 1, width: 1, inactiveLabel: false) {
            state 'default', label: 'Refresh', action: 'refresh.refresh', icon: 'st.secondary.refresh-icon'
        }

        main(['switch'])
        details(['switch', 'switchLevel', 'refresh'])
    }
}

// parse events into attributes
def parse(description) {
    logger("Parsing '${description.json}'", 'debug')
    // TODO: handle 'hue' attribute
    // TODO: handle 'saturation' attribute
    // TODO: handle 'color' attribute
    // TODO: handle 'switch' attribute
    // TODO: handle 'level' attribute
}

def parseColorListSet(description) {
    if (description.json.Status == 0) {
        //success
        logger("Color Group ${state.color} successfully saved", 'info')
        parent.childRefresh()
    } else {
        logger("Color Group ${state.color} returned an error.\n Response: \n${description.json}", 'error')
    }
}

// handle commands
def setHue(hue) {
    logger("Executing 'setHue' $hue", 'debug')
    setColor([hue: hue, saturation: state.saturation])
}

def setSaturation(saturation) {
    logger("Executing 'setSaturation' $saturation", 'debug')
    setColor([hue: state.hue, saturation: saturation])
}

def setColor(color) {
    logger("Executing 'setColor' with $color", 'debug')

    // convert Hue 0-100 (ST/Hubitat) to 0-360 (Luxor)
    def hue = (color.hue * 360 / 100).toInteger()


    def obj = [C: state.color, Hue: hue, Sat: color.saturation.toInteger()]
    logger("about to update color group :${state.color} to be $obj", 'trace')
    // update desired color with current color
    sendCommandToController('/ColorListSet.json', obj, parseColorListSet)
    sendEvent(name: 'hue', value: color.hue, displayed: true)
    sendEvent(name: 'saturation', value: color.saturation, displayed: true)
}

def changeColorGroup(cg){
    logger("changeColorGroup $cg", 'info')
    def obj = [Name: device.getLabel(), GroupNumber: state.luxorGroup, Color: cg]
    parent.updateGroup(obj)

}

// handle commands
def off() {
  logger("Executing 'off'", 'debug')

    state.desiredIntensity = 0
    sendEvent(name: 'switch', value: 'turningOff', displayed: true)
    illuminateGroup()
}

def on() {
    logger("Executing 'on'", 'debug')
    sendEvent(name: 'switch', value: 'turningOn', displayed: true)

    state.desiredIntensity = 100
    illuminateGroup()
}

def setLevel(lvl) {
    logger("Executing 'setLevel' with $lvl", 'debug')
    state.desiredIntensity = lvl

    if (lvl == 0) {
        sendEvent(name: 'switch', value: 'turningOff', displayed: true)
        off()
    } else {
        sendEvent(name: 'switch', value: 'turningOn', displayed: true)
        illuminateGroup()
    }
}

def setState(_state, _val) {
    state."$_state" = _val
}

def updated() {
    logger("updated $device", 'debug')
    //setValues()
}

def refresh() {
    logger("native called refresh $device", 'debug')
    logger("state: $state", 'debug')
    logger("device label: $device.label", 'info')
    parent.childRefresh()
}

def sendCommandToController(def apiCommand, def body = '{}', def _callback) {
    logger("Sending $body to $apiCommand and receiving back and $_callback", 'info')
    def controllerIP = getDataValue('controllerIP')

    def cb = [:]
    if (_callback) {
        cb['callback'] = _callback
    }
    def hubAction
    if (state.isST) {
    hubAction = physicalgraph.device.HubAction.newInstance(
            method: 'POST',
            path: apiCommand,
            body: body,
            headers: [
                    'HOST'        : "$controllerIP:80",
                    'Content-Type': 'application/json'],
            null,
            cb
    )
    }
    else {
            hubAction = hubitat.device.HubAction.newInstance(
            method: 'POST',
            path: apiCommand,
            body: body,
            headers: [
                    'HOST'        : "$controllerIP:80",
                    'Content-Type': 'application/json'],
            null,
            [callback: _callback]
            )
/*                 asynchttpPost(_callback, [
                uri: '$controllerIP:80',
                path: apiCommand,
                        requestContentType: 'application/json',
                        contentType: 'application/json',
                        body: body
            ]
            , body) */
    }

  logger("Sending $apiCommand to controller\n${hubAction.toString()}", 'debug')
    try {
        sendHubCommand(hubAction)
    }
    catch (Exception e) {
        if (debug) logger("hubitat-luxor sendHubCommand Exception ${e} on ${hubAction}", 'error')
    }
}

def illuminateGroup() {
    // def jsonOutput = new groovy.json.JsonOutput()
    def obj = [GroupNumber: state.luxorGroup, Intensity: state.desiredIntensity]
    //def requestJson = jsonOutput.toJson(obj)
    logger("Luxor illuminating group $state.luxorGroup at $state.desiredIntensity brightness.", 'debug')
    sendCommandToController('/IlluminateGroup.json', obj, 'parseIlluminateGroup')
}

def parseIlluminateGroup(hubResponse) {
  logger("hubResponse: $hubResponse.body  $hubResponse.description  $hubResponse.headers  desiredInten=$state.desiredIntensity", 'debug')
    if (hubResponse.json.Status == 0) {
        if (state.desiredIntensity > 0) {
            logger("Light group ${state.luxorGroup} is now on with brightness $state.desiredIntensity.", 'info')
            sendEvent(name: 'switch', value: 'on', displayed: true)
            logger("in parseIlluminate: $state", 'trace')
            sendEvent(name: 'level', value: state.desiredIntensity)
        } else {
            logger("Light group ${state.luxorGroup} is now off.", 'info')
            sendEvent(name: 'switch', value: 'off', displayed: true)
            logger("in parseIlluminate: $state", 'trace')
            sendEvent(name: 'level', value: state.desiredIntensity)
        }
        parent.childRefresh()
    } else {
        logger("Error from Luxor controller: ${hubResponse.json}", 'info')
    }
}
def parseIlluminateGroup(response, data) {
    logger("Received back $response and data $data", 'info')
    logger("getStatus: ${response.getStatus()}", 'info')
    logger("getHeaders: {$response.getHeaders()}", 'info')
    logger("getData: {$response.getData()}", 'info')
    logger("getErrorData: {$response.getErrorData()}", 'info')
    logger("getErrorJson: {$response.getErrorJson()}", 'info')
    logger("getErrorMessage: {$response.getErrorMessage()}", 'info')
    logger("getJson: {$response.getJson()}", 'info')
    logger("hasError: {$response.hasError()}", 'info')
}

def installed() {
    logger("Executing installed on $device", 'info')
}

def setValues() {
    //def onOff = "off"
    //def inten = getDataValue("intensity") as Integer

    //if (inten > 0) {
    //    onOff = "on"
    //}
    logger("state ${state}", 'info')
    logger("set values $device  $switchLevel 1 $switchState 2 ${switchState>0} 3 4 ${currentState}", 'debug')
    logger("switch states 2 ${device.switchLevel}  3 ${device.switchState}", 'debug')
    sendEvent(name : 'switch', value : switchState > 0 ? 'on' : 'off', displayed : true)
    logger('what is inten: $inten', 'trace')
    sendEvent(name: 'level', value: inten)
    //myLogger "debug", "sent levels"

    sendEvent(name: "color", value: [hue: getDataValue("hue"), saturation: getDataValue("saturation")])
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
