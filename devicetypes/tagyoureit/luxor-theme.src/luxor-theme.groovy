/**
 *  Luxor Theme
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
    definition(name: 'Luxor Theme', namespace: 'tagyoureit', author: 'Russell Goldin') {
        capability 'Momentary'
    }

    simulator {
    // TODO: define status and reply messages here
    }

    // UI tile definitions
    tiles(scale: 2) {
        multiAttributeTile(name: 'momentary', type: 'generic', width: 6, height: 4, canChangeIcon: true) {
            tileAttribute('device.momentary', key: 'PRIMARY_CONTROL') {
                attributeState('off', label: 'Push', action: 'momentary.push', icon: 'st.lights.philips.hue-multi', backgroundColor: '#ffffff')
                attributeState('on', label: 'Push', action: 'momentary.push', icon: 'st.lights.philips.hue-multi', backgroundColor: '#00a0dc')
            }
        }
        main 'momentary'
        details 'momentary'
    }
}

// parse events into attributes
def parseThemeListGet(description) {
    logger("Parsing '${description.json}'", 'debug')
    if (description.json.Status == 0) {
        //success
        log.info "$device theme successfully turned on"
        runIn(2, turnOffMomentary)
        parent.childRefresh()
    } else {
        log.error "$device theme did not turn on \n Response: \n${description.json}"
    }
}

def turnOffMomentary() {
    sendEvent(name: 'momentary', value: 'off')
}

// handle commands
def push() {
    def obj = [ThemeIndex: state.luxorTheme, OnOff: 1]
    logger("Executing 'push' on $device", 'debug')

    //def setStr = "{\"ThemeIndex\":${state.luxorTheme}, \"OnOff\":1}"
    // update luxor group to use desired group
    sendCommandToController('/IlluminateTheme.json', obj, parseThemeListGet)
    sendEvent(name: 'momentary', value: 'on')
}

def sendCommandToController(def apiCommand, def body = '{}', def _callback) {
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
            //getDataValue("controllerMac"),
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
            //getDataValue("controllerMac"),
            null,
            cb
    )
    }
    logger("Sending $apiCommand to controller\n${hubAction.toString()}", 'debug')
    sendHubCommand(hubAction)
}

def setState(_state, _val) {
    state."$_state" = _val
}

def installed() {
    log.info "Executing installed on $device"
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
