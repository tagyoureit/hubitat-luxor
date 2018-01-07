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
	definition (name: "Luxor Theme", namespace: "tagyoureit", author: "Russell Goldin") {
		capability "Momentary"
	}


	simulator {
		// TODO: define status and reply messages here
	}

	// UI tile definitions
	tiles(scale: 2){
		multiAttributeTile(name:"momentary", type: "generic", width: 6, height: 4, canChangeIcon: true){
			tileAttribute("device.momentary", key: "PRIMARY_CONTROL") {
				attributeState("off", label: 'Push', action: "momentary.push", icon: "st.lights.philips.hue-multi", backgroundColor: "#ffffff", nextState: "on")
				attributeState("on", label: 'Push', action: "momentary.push", icon: "st.lights.philips.hue-multi", backgroundColor: "#00a0dc")
			}	
		}
		main "momentary"
		details "momentary"
	}
}

// parse events into attributes
def parse(description) {
	log.debug "Parsing '${description.json}'"
	if (description.json.Status==0){
    //success
    	parent.childRefresh()
    }
    else {
    	
    }

}

// handle commands
def push() {
	log.debug "Executing 'push'"

    def setStr = "{\"ThemeIndex\":${getDataValue('theme')}, \"OnOff\":1}"
            log.debug "about to update theme to $setStr"
            // update luxor group to use desired group 
            sendCommandToController("/IlluminateTheme.json",setStr, parse)
    sendEvent(name: "momentary", value: "off",)
}


// 	sendEvent(name: "momentary", value: "pushed", isStateChange: true)


def sendCommandToController(def apiCommand, def body="{}", def _callback) {
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