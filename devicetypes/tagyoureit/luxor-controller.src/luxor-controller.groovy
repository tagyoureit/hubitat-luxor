/**
 *  Luxor ZDC Controller
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
    definition(name: "Luxor ZDC Controller", namespace: "tagyoureit", author: "Russell Goldin") {
        capability "Switch"
    }


    simulator {
// TODO: define status and reply messages here
    }

    tiles {
        multiAttributeTile(name: "switch", type: "lighting", width: 6, height: 4, canChangeIcon: true) {
            tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label: '${name}', action: "switch.off", icon: "st.lights.philips.hue-single", backgroundColor: "#00A0DC", nextState: "turningOff"
                attributeState "off", label: '${name}', action: "switch.on", icon: "st.lights.philips.hue-single", backgroundColor: "#ffffff", nextState: "turningOn"
                attributeState "turningOn", label: '${name}', action: "switch.off", icon: "st.lights.philips.hue-single", backgroundColor: "#00A0DC", nextState: "turningOff"
                attributeState "turningOff", label: '${name}', action: "switch.on", icon: "st.lights.philips.hue-single", backgroundColor: "#ffffff", nextState: "turningOn"
            }
        }
    }
}

def installed() {
    log.debug "executing ZDC Controller installed"
    manageChildren()
}

// parse events into attributes
def parse(physicalgraph.device.HubResponse hubResponse) {
    log.debug "Parsing '${hubResponse}'"
    log.debug "Switch response is ${hubResponse.json}"

}

def parseAllOn(physicalgraph.device.HubResponse hubResponse) {
    log.debug "Controller response to IlluminateAll is ${hubResponse.json}"
    if (hubResponse.json.Status == 0) {
        log.info "All Luxor Groups turned on."
        sendEvent(name: "switch", value: "on", displayed: true)
    } else {
        log.info "Error from Luxor controller: ${hubResponse.json}"
    }
}

def parseAllOff(physicalgraph.device.HubResponse hubResponse) {
    log.debug "Controller response to ExtinguishAll is ${hubResponse.json}"
    if (hubResponse.json.Status == 0) {
        log.info "All Luxor Lights turned off."
        sendEvent(name: "light", value: "off", displayed: true)
    } else {
        log.info "Error from Luxor controller: ${hubResponse.json}"
    }
}

// handle commands
def off() {
    log.debug "Executing 'off'"
    sendCommandToController('/ExtinguishAll.json', null, "parseAllOff")
}

def on() {
    log.debug "Executing 'on'"
    sendCommandToController('/IlluminateAll.json', null, "parseAllOn")
}

def updated() {
    log.debug "Executing ZDC Controller updated"
    unsubscribe()
    manageChildren()
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
    log.debug result.toString()
    sendHubCommand(result)
}

def manageChildren() {
    log.debug "manage children in ZDC Controller"
    state.manageChildren ?: state.manageChildren = "idle"
    // if state.manageChildren is not assigned, initialize it with "idle"
    if (state.manageChildren != "idle") {
        state.manageChildren = "running"
        sendCommandToController("/GroupListGet.json", null, "parseGroupListGet")
    }

}

def parseGroupListGet(physicalgraph.device.HubResponse hubResponse) {
    log.debug "GroupListGet response: ${hubResponse.json}"
    def hub = location.hubs[0]
    def hubId = hub.id
    def groups = hubResponse.json.GroupList
    def groupType
    def devices = getChildDevices()?.findAll { it.getDataValue("group") }
    groups.each { group ->
        log.debug "group $group"
        def childMac = "${hubResponse.mac}-${group.Grp}".replaceAll("\\s", "")

        def device = devices.find {
            childMac == it.deviceNetworkId
        }
        log.debug "0. device is found??? $device"
        if (device) {
            //log.debug "verified a device"
            device.updateDataValue("controllerIP", getDataValue("controllerIP"))
            devices.remove(device)
        } else {
            log.debug "1.  no device found? $device"
            if (getDataValue("controllerType") == "ZDC") {
                if (group.Colr == 0) {
                    groupType = "Monochrome"
                    log.info "Creating Luxor $groupType light group #:${group.GroupNumber}, name ${group.Name}, Intensity ${group.Intensity}"
                } else {
                    groupType = "Color"
                    log.info "Creating Luxor $groupType light group #:${group.Grp}, name ${group.Name}, Intensity ${group.Inten}, Color ${group.Colr}"
                }
            }
            def lightGroup = "Luxor $groupType Group"
            def componentName = "${group.Name}componentname".replaceAll("\\s", "")  // what is this for?


            log.debug "2.  values? for $lightGroup  $childMac, $hubId"
            log.debug "3.  getDataValue(controllerIP) ${getDataValue('controllerIP')}    getDataValue(controllerPort)  ${getDataValue('controllerPort')}"
            def params = ["label"         : group.Name,
                          "completedSetup": true,
                          "data"          : [
                                  "intensity"     : group.Inten || group.Intensity,
                                  "color"         : 0 || group.Colr,
                                  "group"         : group.Grp || group.GroupNumber,
                                  "controllerType": getDataValue("controllerType"),
                                  "controllerIP"  : getDataValue("controllerIP"),
                                  "controllerPort": getDataValue("controllerPort"),
                                  "type"          : "group"

                          ],
                          "isComponent"   : false,
                          "componentName" : componentName,
                          "componentLabel": "${group.Name} group label" // what is this for?
            ]
            log.debug "4.  params are $params"
            log.debug "5.  about to add child with values namespace: tagyoureit \n  lightgroup $lightGroup \n childMac $childMac \n hubId $hubId \n params: $params"
            device = addChildDevice("tagyoureit", lightGroup, childMac, hubId, params)
            log.debug "6.  Light Group Added $device"

        }

    }

    setControllerState(groups)
    removeOrphanedChildren(devices)
    sendCommandToController("/GroupListGet.json", null, "parseGroupListGet")

}

def removeOrphanedChildren(devices){
    devices.all{device ->
        log.info "Removing $device.deviceNetworkId"
        deleteChildDevice($$device.deviceNetworkId)
    }
}

def parseThemeListGet() {
    log.debug "ThemeListGet response: ${hubResponse.json}"
    def hub = location.hubs[0]
    def hubId = hub.id
    def themes = hubResponse.json.ThemeList
    def devices = getChildDevices()?.findAll { it.getDataValue("theme") }
    themes.each { theme ->
        log.debug "theme $theme"
        def childMac = "${hubResponse.mac}-${theme.ThemeIndex}".replaceAll("\\s", "")

        def device = devices.find {
            childMac == it.deviceNetworkId
        }
        log.debug "0. device is found??? $device"
        if (device) {
            //log.debug "verified a device"
            device.updateDataValue("controllerIP", getDataValue("controllerIP"))
            devices.remove(device)

        } else {
            log.debug "1.  no device found? $device"
            log.info "Creating Luxor Theme #:${theme.ThemeIndex}, name ${theme.Name}"


            def componentName = "${theme.Name}componentname".replaceAll("\\s", "")  // what is this for?


            log.debug "3.  getDataValue(controllerIP) ${getDataValue('controllerIP')}    getDataValue(controllerPort)  ${getDataValue('controllerPort')}"
            def params = ["label"         : theme.Name,
                          "completedSetup": true,
                          "data"          : [
                                  "theme"         : theme.ThemeIndex,
                                  "controllerType": getDataValue("controllerType"),
                                  "controllerIP"  : getDataValue("controllerIP"),
                                  "controllerPort": getDataValue("controllerPort"),
                                  "type"          : "theme"
                          ],
                          "isComponent"   : false,
                          "componentName" : componentName,
                          "componentLabel": "${theme.Name} theme label" // what is this for?
            ]
            log.debug "4.  params are $params"
            log.debug "5.  about to add child with values namespace: tagyoureit \n  lighttheme $lightTheme \n childMac $childMac \n hubId $hubId \n params: $params"
            device = addChildDevice("tagyoureit", "Luxor Theme", childMac, hubId, params)
            log.debug "6.  Light Theme Added $device"

        }

    }
    removeOrphanedDevices(devices)
    if (getDataValue("controllerType") == "ZDC") {
        sendCommandToController("/ColorListGet.json", null, "parseColorListGet")
    } else {
        state.manageChildren = "idle"
    }

}


parseColorListGet() {
    log.debug "ColorListGet response: ${hubResponse.json}"
    def hub = location.hubs[0]
    def hubId = hub.id
    def colors = hubResponse.json.ColorList

    def devices = getChildDevices()?.findAll { it.getDataValue("color") > 0 }


    devices.each { device ->
        log.debug "devic $device"

        color = colors.find { color -> color.C == device.getDataValue("color") }
        log.debug "found color $color"
        device.updateDataValue("hue", color.Hue)
        device.updateDataValue("saturation", color.Sat)
        device.setValues()
        state.manageChildren = "idle"
    }
}

def setControllerState(groups) {
    // set the controller to on if any of the groups are on, otherwise off
    def controllerOn
    if (getDataValue("controllerType") == "ZDC") {
        controllerOn = groups.findAny { group -> group.Inten > 0 }
    } else {
        controllerOn = groups.findAny { group -> group.Intensity > 0 }
    }
    sendEvent(name: "switch", value: onOff, displayed: true)

}

def refresh() {
    manageChildren()
}

// called from Children
def childRefresh() {
    log.debug "childRefresh() called"
    manageChildren()
}

