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
        capability "Switch"
        capability "Refresh"
    }


    simulator {
// TODO: define status and reply messages here
    }

    tiles {
        multiAttributeTile(name: "switch", type: "lighting", width: 6, height: 4, canChangeIcon: true) {
            tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label: '${name}', action: "switch.off", icon: "st.switches.light.on", backgroundColor: "#00A0DC", nextState: "turningOff"
                attributeState "off", label: '${name}', action: "switch.on", icon: "st.secondary.off", backgroundColor: "#ffffff", nextState: "turningOn"
                attributeState "turningOn", label: '${name}', action: "switch.off", icon: "st.switches.light.on", backgroundColor: "#00A0DC", nextState: "turningOff"
                attributeState "turningOff", label: '${name}', action: "switch.on", icon: "st.secondary.off", backgroundColor: "#ffffff", nextState: "turningOn"
            }
        }

        standardTile("refresh", "device.refresh", height: 1, width: 1, inactiveLabel: false) {
            state "default", label: 'Refresh', action: "refresh.refresh", icon: "st.secondary.refresh-icon"
            state "coolDown", label: 'Cool Down', icon: ""
        }

        main(["switch"])
        details(["switch","refresh"])
    }
}

def superDebug() {
    // set to true for verbose debugging.  false for quiet.
    // this will propogate to child devices as well
    state.superDebug = false
}

def installed() {
    myLogger "debug", "executing Luxor Controller installed"
    runIn(10,manageChildren)
    runEvery5Minutes(manageChildren)
    //manageChildren()
}

// parse events into attributes
def parse(physicalgraph.device.HubResponse hubResponse) {
    myLogger "debug", "Parsing '${hubResponse}'"
    myLogger "debug", "Switch response is ${hubResponse.json}"

}

def parseAllOn(physicalgraph.device.HubResponse hubResponse) {
    myLogger "debug", "Controller response to IlluminateAll is ${hubResponse.json}"
    if (hubResponse.json.Status == 0) {
        log.info "All Luxor Light Groups turned on."
        sendEvent(name: "switch", value: "on", displayed: true)
        manageChildren()
    } else {
        log.info "Error from Luxor controller: ${hubResponse.json}"
    }
}

def parseAllOff(physicalgraph.device.HubResponse hubResponse) {
    myLogger "debug", "Controller response to ExtinguishAll is ${hubResponse.json}"
    if (hubResponse.json.Status == 0) {
        log.info "All Luxor Light Groups turned off."
        sendEvent(name: "switch", value: "off", displayed: true)
        manageChildren()

    } else {
        log.info "Error from Luxor controller: ${hubResponse.json}"
    }
}

// handle commands
def off() {
    myLogger "debug", "Executing 'off'"
    sendCommandToController('/ExtinguishAll.json', null, "parseAllOff")
}

def on() {
    myLogger "debug", "Executing 'on'"
    sendCommandToController('/IlluminateAll.json', null, "parseAllOn")
}

def updated() {
    myLogger "debug", "Executing Luxor Controller updated"
    unsubscribe()
    manageChildren()
    runEvery5Minutes(manageChildren)

}

def sendCommandToController(def apiCommand, def body = "{}", def _callback) {
    def controllerIP = getDataValue('controllerIP')

    def cb = [:]
    if (_callback) {
        cb["callback"] = _callback
    }
    def result = new physicalgraph.device.HubAction(
            method: "POST",
            path: apiCommand,
            body: "${body}",
            headers: [
                    "HOST"        : "$controllerIP:80",
                    "Content-Type": "application/json"],
            null,
            cb
    )
    myLogger "debug", result.toString()
    sendHubCommand(result)
}

def manageChildren() {
    superDebug() // reset state if user changes it
    if (state.superDebug) myLogger "debug", "this is a super crazy debug statement"
    myLogger "debug", "manage children in Luxor Controller (current state = $state.manageChildren)"
    state.manageChildren = state.manageChildren ?: "idle"
    // if state.manageChildren is not assigned, initialize it with "idle"
    myLogger "debug", "state.managechildren = ${state.manageChildren}"
    if (state.manageChildren == "idle") {
        state.manageChildren = "running"
        sendEvent(name: "refresh", value: "coolDown")
        sendCommandToController("/GroupListGet.json", null, "parseGroupListGet")
    }

    runIn(30, resetRunningState)

}

def resetRunningState() {
    // just in case code crashes, reset state so we can continue.
    if (state.manageChildren != "idle") {
        myLogger "debug", "Reset manageChildren State"
        state.manageChildren = "idle"
        sendEvent(name: "refresh", value: "default")
    }
}

def parseGroupListGet(physicalgraph.device.HubResponse hubResponse) {
    myLogger "debug", "GroupListGet response: ${hubResponse.json}"
    def hubId = location.hubs[0].id
    def groups = hubResponse.json.GroupList
    def groupType
    def devices = getChildDevices()?.findAll { it.deviceNetworkId.contains("Group")}

    groups.each { group ->
        myLogger "debug", "group $group"
        def _group = group.Grp?:group.GroupNumber
        def childMac = "${hubResponse.mac}-Group${_group}".replaceAll("\\s", "")

        def device = devices.find {
            childMac == it.deviceNetworkId
        }
        def _color = group.Colr?:0

        def _intensity = 0// group.Inten?:group.Intensity  NOTE: Elvis doesn't work here because 0 is a valid group but defaults to falsy

        if (group.Inten){
            _intensity = group.Inten
        }
        else {
            _intensity = group.Intensity
        }


        if (device) {
            // remove device from list of not-yet-found devices
            devices.remove(device)
        } else {
            if (getDataValue("controllerType") == "ZDC") {
                if (group.Colr == 0) {
                    groupType = "Monochrome"

                } else {
                    groupType = "Color"

                }
                log.info "Creating Luxor $groupType light group #:${_group}, name ${group.Name}, Intensity ${_intensity}, Color ${_color}"
            }  else //zd
            {
                groupType = "Monochrome"
                log.info "Creating Luxor $groupType light group #:${_group}, name ${group.Name}, Intensity ${_intensity}"

            }
            def lightGroup = "Luxor $groupType Group"
            def params = ["label"         : group.Name,
                          "completedSetup": true,
                          "data"          : [
                                  "controllerType": getDataValue("controllerType"),
                                  "controllerIP"  : getDataValue("controllerIP"),
                                  "controllerPort": getDataValue("controllerPort")
                          ],
                          "isComponent"   : false,
                          "componentLabel": "${group.Name}grouplabel" // what is this for?
            ]
            myLogger "debug", "about to add child with values namespace: tagyoureit \n  typeName $lightGroup \n childMac $childMac \n hubId $hubId \n Map properties: $params"
            device = addChildDevice("tagyoureit", lightGroup, childMac, hubId, params)

            log.info "Light Group $device Added"



        }
        // update device values/states whether new or existing device
        device.updateDataValue("controllerIP", getDataValue("controllerIP"))


        device.sendEvent(name: "switch", value: _intensity>0?"on":"off" , displayed:true)
        device.sendEvent(name: "level", value: _intensity)
        if (getDataValue("controllerType") == "ZDC") {
            device.setState("color", _color)
        }
        device.setState("type", "group")
        device.setState("luxorGroup", _group)
        device.setState("superDebug", state.superDebug)

    }

    setControllerState(groups)
    removeOrphanedChildren(devices)
    sendCommandToController("/ThemeListGet.json", null, "parseThemeListGet")

}


def removeOrphanedChildren(devices){
    devices.each{device ->
        log.info "Removing $device.deviceNetworkId"
        deleteChildDevice($$device.deviceNetworkId)
    }
}

def parseThemeListGet(physicalgraph.device.HubResponse hubResponse) {
    myLogger "debug", "ThemeListGet response: ${hubResponse.json}"
    def hub = location.hubs[0]
    def hubId = hub.id
    def themes = hubResponse.json.ThemeList
    def devices = getChildDevices()?.findAll { it.deviceNetworkId.contains("Theme") }

    themes.each { theme ->
        myLogger "debug", "theme $theme"
        def childMac = "${hubResponse.mac}-Theme${theme.ThemeIndex}".replaceAll("\\s", "")

        def device = devices.find {
            childMac == it.deviceNetworkId
        }
        if (device) {
            devices.remove(device)
        } else {
            log.info "Creating Luxor Theme #:${theme.ThemeIndex}, name ${theme.Name}"
            def params = ["label"         : theme.Name,
                          "completedSetup": true,
                          "data"          : [
                                  //"theme"         : theme.ThemeIndex,
                                  "controllerType": getDataValue("controllerType"),
                                  "controllerIP"  : getDataValue("controllerIP"),
                                  "controllerPort": getDataValue("controllerPort"),
                                  "type"          : "theme"
                          ],
                          "isComponent"   : false,
                          "componentLabel": "${theme.Name} theme label" // what is this for?
            ]
            device = addChildDevice("tagyoureit", "Luxor Theme", childMac, hubId, params)
            log.info "THEME 6.  Light Theme $device Added"

        }

        device.updateDataValue("controllerIP", getDataValue("controllerIP"))
        device.setState("type", "theme")
        device.setState("luxorTheme", theme.ThemeIndex)
        device.setState("superDebug", state.superDebug)
    }
    removeOrphanedChildren(devices)

    // if we have a color controller, continue to retrieve colors.  Otherwise update children
    if (getDataValue("controllerType") == "ZDC") {
        sendCommandToController("/ColorListGet.json", null, "parseColorListGet")
    } else {
        updateChildren()
    }

}


def parseColorListGet(physicalgraph.device.HubResponse hubResponse) {
    myLogger "debug", "ColorListGet response: ${hubResponse.json}"

    def colors = hubResponse.json.ColorList

    def devices = getChildDevices()?.findAll { it?.state?.color > 0 }


    devices.each { device ->
        def color = colors.find { color -> color.C == device.state?.color }
        device.sendEvent(name: "color", value: [hue: color.Hue, saturation: color.Sat])
    }
    resetRunningState()
    updateChildren()
}

def updateChildren(){
}

def setControllerState(groups) {
    // set the controller to on if any of the groups are on, otherwise off
    def controllerOn
    if (getDataValue("controllerType") == "ZDC") {
        controllerOn = groups.any { group -> group.Inten > 0 }
    } else {
        controllerOn = groups.any { group -> group.Intensity > 0 }
    }
    controllerOn = controllerOn?"on":"off"
    sendEvent(name: "switch", value: controllerOn, displayed: true)
}

def refresh() {
    manageChildren()
}

// called from Children
def childRefresh() {
    myLogger "debug", "childRefresh() called"
    manageChildren()
}

def myLogger(level, message) {
    if (level == "debug") {
        if (state.superDebug) {
            log."$level" "$message"
        }
        // silently abort debug messages unless state.superDebug = true
    } else {
        log."$level" "$message"
    }

}