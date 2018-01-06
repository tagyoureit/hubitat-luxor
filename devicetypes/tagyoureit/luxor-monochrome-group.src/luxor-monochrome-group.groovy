/**
*  Luxor Monochrome Group
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
        details(["switch","refresh"])
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


def controllerHubGet(def apiCommand, def body="{}", def _callback) {
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
            "HOST" : "$controllerIP:80",
            "Content-Type": "application/json"],
        //getDataValue("controllerMac"),
        null,
        cb
    )
    log.debug result.toString()
    sendHubCommand(result);
}

def illuminateGroup(){
    def jsonSlurper = new groovy.json.JsonSlurper()
    def jsonOutput = new groovy.json.JsonOutput()
    def group = getDataValue("group")
    def obj = [GroupNumber: group, Intensity: state.desiredIntensity]
    log.debug "obj $obj"
    def requestJson = jsonOutput.toJson(obj)
    log.debug "requestJson is $requestJson"




    log.info "Luxor illuminating group $group at $state.desiredIntensity brightness."
    controllerHubGet('/IlluminateGroup.json',requestJson,'parseIlluminateGroup')
}



def parseIlluminateGroup(physicalgraph.device.HubResponse hubResponse) {
    log.debug "hubResponse: $hubResponse.body  $hubResponse.description  $hubResponse.headers  desiredInten=$state.desiredIntensity"
    if (hubResponse.json.Status==0){
        log.info "desiredIntensity = $state.desiredIntensity"

        if (state.desiredIntensity>0){
            log.info "Light group ${getDataValue('group')} is now on with brightness $state.desiredIntensity."
            sendEvent(name: "switch", value: "on", displayed:true) 
            sendEvent(name: "level", value: state.desiredIntensity)
        }
        else
        {
            log.info "Light group ${getDataValue('group')} is now off."
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
    log.info "Executing installed on $device"
    setValues()
}

def setValues() {


    def onOff = "off"
	def inten = getDataValue("intensity") as Integer 

    if (inten>0){ onOff="on"}
    log.debug "set values $device $inten  $onOff"
	sendEvent(name: "switch", value: onOff , displayed:true) 
    // sendEvent(name: "switch level", value: state.desiredIntensity, displayed:true)
    log.debug "sent switch, now levels"
    sendEvent(name: "level", value: getDataValue("intensity"))
    log.debug "sent levels"
}

def updated(){
    log.debug "updated $device"
    setValues()
}

def refresh(){
    log.debug "called refresh $device"
    parent.childRefresh()
}