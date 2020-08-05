/* groovylint-disable IfStatementBraces */
/**
 *  Luxor Controller
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
    definition(name: "Luxor Controller", namespace: "tagyoureit", author: "Russell Goldin") {
        capability 'Switch'
        capability 'Refresh'
    }

    simulator {
    // TODO: define status and reply messages here
    }

    tiles {
        multiAttributeTile(name: 'switch', type: 'lighting', width: 6, height: 4, canChangeIcon: true) {
            tileAttribute('device.switch', key: 'PRIMARY_CONTROL') {
                attributeState 'on', label: '${name}', action: 'switch.off', icon: 'st.switches.light.on', backgroundColor: '#00A0DC', nextState: 'turningOff'
                attributeState 'off', label: '${name}', action: 'switch.on', icon: 'st.secondary.off', backgroundColor: '#ffffff', nextState: 'turningOn'
                attributeState 'turningOn', label: '${name}', action: 'switch.off', icon: 'st.switches.light.on', backgroundColor: '#00A0DC', nextState: 'turningOff'
                attributeState 'turningOff', label: '${name}', action: 'switch.on', icon: 'st.secondary.off', backgroundColor: '#ffffff', nextState: 'turningOn'
            }
        }

        standardTile('refresh', 'device.refresh', height: 1, width: 1, inactiveLabel: false) {
            state 'default', label: 'Refresh', action: 'refresh.refresh', icon: 'st.secondary.refresh-icon'
            state 'coolDown', label: 'Cool Down', icon: ''
        }

        main(['switch'])
        details(['switch', 'refresh'])
    }

    preferences {
        input (
            name: 'loggingLevelIDE',
            title: 'IDE Live Logging Level:\nMessages with this level and higher will be logged to the IDE.',
            type: 'enum',
            options: [
                'None',
                'Error',
                'Warning',
                'Info',
                'Debug',
                'Trace'
            ],
            required: false,
            defaultValue: 'Debug'
        )
    }
}

def installed() {
    log.debug 'executing Luxor Controller installed'
    runIn(10, manageChildren)
    runEvery5Minutes(manageChildren)
    manageChildren()
}

// parse events into attributes
def parse(hubResponse) {
    logger("Parsing '${hubResponse}'", 'debug')
    logger("Switch response is ${hubResponse.json}", 'debug')
}

def parseAllOn(hubResponse) {
    logger("Controller response to IlluminateAll is ${hubResponse.json}", 'debug')
    if (hubResponse.json.Status == 0) {
        log.info 'All Luxor Light Groups turned on.'
        sendEvent(name: 'switch', value: 'on', displayed: true)
        manageChildren()
    } else {
        logger("Error from Luxor controller: ${hubResponse.json}", 'info')
    }
}

def parseAllOff(hubResponse) {
    logger("Controller response to ExtinguishAll is ${hubResponse.json}", 'debug')
    if (hubResponse.json.Status == 0) {
        log.info 'All Luxor Light Groups turned off.'
        sendEvent(name: 'switch', value: 'off', displayed: true)
        manageChildren()
    } else {
        logger("Error from Luxor controller: ${hubResponse.json}", 'info')
    }
}

// handle commands
def off() {
    logger("Executing 'off'", 'debug')
    sendCommandToController('/ExtinguishAll.json', null, 'parseAllOff')
}

def on() {
    logger("Executing 'on'", 'debug')
    sendCommandToController('/IlluminateAll.json', null, 'parseAllOn')
}

def updated() {
    log.debug 'Executing Luxor Controller updated'
    if (state.isST) unsubscribe()
    manageChildren()
    runEvery5Minutes(manageChildren)
}

def sendCommandToController(def apiCommand, def body = [:], def _callback) {
    def controllerIP = getDataValue('controllerIP')

    def cb = [:]
    if (_callback) {
        cb['callback'] = _callback
    }
    def result
    if (state.isST) {
        result = physicalgraph.device.HubAction.newInstance(
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
        result = hubitat.device.HubAction.newInstance(
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
    log.debug result.toString()
    sendHubCommand(result)
}

def manageChildren() {
    logger("manage children in Luxor Controller (current state = $state.manageChildren)", 'debug')
    state.manageChildren = state.manageChildren ?: 'idle'
    // if state.manageChildren is not assigned, initialize it with "idle"
    logger("state.managechildren = ${state.manageChildren}", 'debug')
    if (state.manageChildren == 'idle') {
        state.manageChildren = 'running'
        sendEvent(name: 'refresh', value: 'coolDown')
        sendCommandToController('/GroupListGet.json', null, 'parseGroupListGet')
    }

    runIn(30, resetRunningState)
}

def resetRunningState() {
    // just in case code crashes, reset state so we can continue.
    if (state.manageChildren != 'idle') {
        log.debug 'Reset manageChildren State'
        state.manageChildren = 'idle'
        sendEvent(name: 'refresh', value: 'default')
    }
}

def parseGroupListGet(hubResponse) {
    logger("GroupListGet response: ${hubResponse.json}", 'debug')
    def hubId = location.hubs[0].id
    def groups = hubResponse.json.GroupList
    def groupType
    def devices = getChildDevices()?.findAll { it.deviceNetworkId.contains('Group') }
    logger('Hub retrieved groups: $groups', 'trace')
    groups.each { group ->
        logger("group $group", 'debug')
        def _group = group.Grp ?: group.GroupNumber
        def childMac = "${hubResponse.mac}-Group${_group}".replaceAll("\\s", '')

        def device = devices.find {
            childMac == it.deviceNetworkId
        }
        def _color = group.Colr ?: 0

        def _intensity = 0// group.Inten?:group.Intensity  NOTE: Elvis doesn't work here because 0 is a valid group but defaults to falsy

        if (group.Inten) {
            _intensity = group.Inten
        }
        else {
            _intensity = group.Intensity
        }

        if (device) {
            // remove device from list of not-yet-found devices
            devices.remove(device)
        } else {
            if (getDataValue('controllerType') == 'ZDC' || getDataValue('controllerType') == 'ZDTWO') {
                if (group.Colr == 0) {
                    groupType = 'Monochrome'
                } else {
                    groupType = 'Color'
                }
                logger("Creating Luxor $groupType light group #:${_group}, name ${group.Name}, Intensity ${_intensity}, Color ${_color}", 'info')
            }  else //zd
            {
                groupType = 'Monochrome'
                logger("Creating Luxor $groupType light group #:${_group}, name ${group.Name}, Intensity ${_intensity}", 'info')
            }
            def lightGroup = "Luxor ${groupType} Group"
            def params = ['label'         : group.Name,
                          'completedSetup': true,
                          'data'          : [
                                  'controllerType': getDataValue('controllerType'),
                                  'controllerIP'  : getDataValue('controllerIP'),
                                  'controllerPort': getDataValue('controllerPort')
                          ],
                          'isComponent'   : false,
                          'componentLabel': "${group.Name}grouplabel" // what is this for?
            ]
            logger("about to add child with values \n  namespace: \n  tagyoureit \n  typeName: $lightGroup \n  childMac: $childMac \n  hubId: $hubId \n  Map properties: $params", 'debug')
            if (state.isST) {
                device = addChildDevice('tagyoureit', lightGroup, childMac, hubId, params)
            }
            else {
                device = addChildDevice('tagyoureit', lightGroup, childMac, params)
            }

            logger("Light Group $device Added", 'info')
        }
        // update device values/states whether new or existing device
        device.updateDataValue('controllerIP', getDataValue('controllerIP'))

        device.sendEvent(name : 'switch', value : _intensity > 0 ? 'on' : 'off' , displayed : true)

        device.sendEvent(name: 'level', value: _intensity)
        if (getDataValue('controllerType') == 'ZDC' || getDataValue('controllerType') == 'ZDTWO') {
            device.setState('color', _color)
        }
        device.setState('type', 'group')
        device.setState('luxorGroup', _group)
        device.setState('loggingLevelIDE', loggingLevelIDE)
    }

    setControllerState(groups)
    removeOrphanedChildren(devices)
    sendCommandToController('/ThemeListGet.json', null, 'parseThemeListGet')
}

def removeOrphanedChildren(devices) {
    devices.each { device ->
        logger("Removing $device.deviceNetworkId", 'info')
        deleteChildDevice(device.deviceNetworkId)
    }
}

def parseThemeListGet(hubResponse) {
    logger("ThemeListGet response: ${hubResponse.json}", 'debug')
    def hub = location.hubs[0]
    def hubId = hub.id
    def themes = hubResponse.json.ThemeList
    def devices = getChildDevices()?.findAll { it.deviceNetworkId.contains('Theme') }

    themes.each { theme ->
        logger("theme $theme", 'debug')
        def childMac = "${hubResponse.mac}-Theme${theme.ThemeIndex}".replaceAll("\\s", '')

        def device = devices.find {
            childMac == it.deviceNetworkId
        }
        if (device) {
            devices.remove(device)
        } else {
            logger("Creating Luxor Theme #:${theme.ThemeIndex}, name ${theme.Name}", 'info')
            def params = ['label'         : theme.Name,
                          'completedSetup': true,
                          'data'          : [
                                  //"theme"         : theme.ThemeIndex,
                                  'controllerType': getDataValue('controllerType'),
                                  'controllerIP'  : getDataValue('controllerIP'),
                                  'controllerPort': getDataValue('controllerPort'),
                                  'type'          : 'theme'
                          ],
                          'isComponent'   : false,
                          'componentLabel': "${theme.Name} theme label" // what is this for?
            ]
            if (state.isST) {
                device = addChildDevice('tagyoureit', 'Luxor Theme', childMac, hubId, params)
            }
            else {
                device = addChildDevice('tagyoureit', 'Luxor Theme', childMac, params)
            }
            logger("THEME 6.  Light Theme $device Added", 'info')
        }
        if (device.getLabel() != theme.Name) device.setLabel(theme.Name)
        device.updateDataValue('controllerIP', getDataValue('controllerIP'))
        device.setState('type', 'theme')
        device.setState('luxorTheme', theme.ThemeIndex)
        device.setState('loggingLevelIDE', loggingLevelIDE)
    }
    removeOrphanedChildren(devices)

    // if we have a color controller, continue to retrieve colors.  Otherwise update children
    if (getDataValue('controllerType') == 'ZDC' || getDataValue('controllerType') == 'ZDTWO') {
        sendCommandToController('/ColorListGet.json', null, 'parseColorListGet')
    } else {
        updateChildren()
    }
}

def parseColorListGet(hubResponse) {
    logger("ColorListGet response: ${hubResponse.json}", 'debug')

    def colors = hubResponse.json.ColorList

    def devices = getChildDevices()?.findAll { it?.state?.color > 0 }

    devices.each { device ->
        def color = colors.find { color -> color.C == device.state?.color }
        device.sendEvent(name: 'color', value: [hue: color.Hue, saturation: color.Sat])
    }
    resetRunningState()
    updateChildren()
}

def updateChildren() {
}

def updateGroup(obj){
    sendCommandToController('/GroupListEdit.json', obj, 'parseUpdateGroup')

}
def parseUpdateGroup(hubResponse) {
  logger("hubResponse for update group: $hubResponse.body  $hubResponse.description  $hubResponse.headers", 'trace')
    if (hubResponse.json.Status == 0) {
        refresh()
    } else {
        logger("Error from Luxor controller: ${hubResponse.json}", 'info')
    }
}
def setControllerState(groups) {
    // set the controller to on if any of the groups are on, otherwise off
    def controllerOn
    if (getDataValue('controllerType') == 'ZDC' || getDataValue('controllerType') == 'ZDTWO') {
        controllerOn = groups.any { group -> group.Inten > 0 }
    } else {
        controllerOn = groups.any { group -> group.Intensity > 0 }
    }
    controllerOn = controllerOn ? 'on' : 'off'
    sendEvent(name: 'switch', value: controllerOn, displayed: true)
}

def refresh() {
    manageChildren()
}

// called from Children
def childRefresh() {
    log.debug 'childRefresh() called'
    manageChildren()
}

//*******************************************************
//*  logger()
//*
//*  Wrapper function for all logging.
//*******************************************************

private logger(msg, level = 'debug') {
    def lookup = [
                'None' : 0,
                'Error' : 1,
                'Warning' : 2,
                'Info' : 3,
                'Debug' : 4,
                'Trace' : 5]
    def logLevel = lookup[loggingLevelIDE ? loggingLevelIDE : 'Debug']
    // log.trace("Lookup is now ${logLevel} for ${loggingLevelIDE}")

    if (logLevel != 0) {
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
                break;

        default:
            log.debug msg
            break
        }
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
